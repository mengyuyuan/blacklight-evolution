package com.codex.blacklight.client.renderer;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** Apply the authored motion to the player and draw only the organ geometry. */
public final class RoofPerformance {
    private static final RooftopRig R=RooftopRig.INSTANCE;
    private RoofPerformance(){}
    public record Clock(String key,float tick,float source,float bodyWeight,double now,double[] firedAt){
        double sinceShot(int slot) {
            int physical = slot ^ (RooftopRig.reflect(key) ? 1 : 0);
            return now - firedAt[physical];
        }
        float fill(int slot) { return RooftopRig.refill(sinceShot(slot), slot); }
    }
    public static final net.minecraft.resources.ResourceLocation ATLAS=java.util.Objects.requireNonNull(
            net.minecraft.resources.ResourceLocation.tryParse("blacklight:textures/entity/weapons_mc.png"));
    private static final net.minecraft.resources.ResourceLocation VEINS=java.util.Objects.requireNonNull(
            net.minecraft.resources.ResourceLocation.tryParse("blacklight:textures/entity/weapons_mc_emissive.png"));
    /** Draw the released blade at its attached model scale with its original shoulder socket and materials. */
    public static void detachedQuill(int slot, PoseStack stack, MultiBufferSource buffers, int light){
        stack.pushPose();
        stack.scale(.9375F,.9375F,.9375F);
        var center=RooftopRig.center(slot);
        stack.translate(-center.x,-center.y,-center.z);
        for(int pass=0;pass<2;pass++){
            boolean veins=pass==1;
            part(R.mesh[R.quill[slot]],stack,buffers.getBuffer(material(veins)),light,veins,0);
        }
        stack.popPose();
    }
    public static RenderType material(boolean veins){return veins?RenderType.entityTranslucentEmissive(VEINS):RenderType.entityCutoutNoCull(ATLAS);}
    private static final Matrix4f[] READY=R.sample(1);
    private static final class Tail {
        String key="";
        float tick,lastSource,entrySource;
        double when;
        double lastAim=Double.NaN;
        float aimYaw,aimPitch;
        final double[] firedAt=new double[6];
        Tail(){java.util.Arrays.fill(firedAt,Double.NEGATIVE_INFINITY);}
    }
    private static final java.util.WeakHashMap<AbstractClientPlayer,Tail> tails=new java.util.WeakHashMap<>();
    public static Clock clock(AbstractClientPlayer p,float partial){
        var action=p.getData(ModAttachments.PLAYER_ACTION);
        double now=PlayerMotionController.actionTime(p,partial);
        // Use entity ticks for visual recovery, independently of server day/time corrections.
        double world=p.tickCount+(double)partial;
        var tail=tails.computeIfAbsent(p,unused->new Tail());
        if(action.active(now)&&action.clipId().startsWith("spines_")){
            String key=action.clipId();
            float tick=action.seconds(now)*20;
            // The input clock holds at tick ten. The photographed wind-up continues until the shoulders finish coiling.
            if(key.endsWith("_draw")&&action.heldTime()>=0)
                tick=action.clipOffset()*20+(float)Math.max(0,now-action.started());
            if(!tail.key.equals(key)){
                tail.entrySource=tail.key.endsWith("_draw")?tail.lastSource:RooftopRig.time(key,0);
                tail.key=key;
            }
            float source=RooftopRig.time(key,tick);
            if(key.startsWith("spines_ground_")&&key.endsWith("_charged")&&tick<5)
                source=net.minecraft.util.Mth.lerp(tick/5,tail.entrySource,3.52F);
            tail.tick=tick;tail.when=world;tail.lastSource=source;
            boolean mirror=RooftopRig.reflect(key);
            for(int q=0;q<6;q++){
                int shot=RooftopRig.releaseTick(key,q);
                if(shot>=0&&tick>=shot){
                    int physical=q^(mirror?1:0);
                    tail.firedAt[physical]=Math.max(tail.firedAt[physical],world-tick+shot);
                }
            }
            float weight=RooftopRig.fullBody(key)?ScapulaVolley.ease(tick/2.5F):0;
            if(key.startsWith("spines_ground_")&&key.endsWith("_draw"))
                weight=ScapulaVolley.ease((tick-5)/8);
            return new Clock(key,tick,source,weight,world,tail.firedAt);
        }
        if(action.active(now))return new Clock("",0,1,0,world,tail.firedAt);
        float elapsed=(float)Math.max(0,world-tail.when);
        float age=tail.tick+elapsed,weight=0,source=1;
        boolean mirror=RooftopRig.reflect(tail.key);
        if(RooftopRig.four(tail.key)&&age<45){
            source=RooftopRig.time(tail.key,age);
            weight=1-ScapulaVolley.ease((age-34)/11);
        }else if(!RooftopRig.four(tail.key)&&RooftopRig.fullBody(tail.key)&&elapsed<5){
            source=tail.lastSource;
            weight=1-ScapulaVolley.ease(elapsed/5);
        }
        boolean ground=p.onGround()&&!p.isPassenger()&&!p.isCrouching()&&!p.isInWaterOrBubble();
        return new Clock("spines_rack_"+(ground?"still":"move")+(mirror?"_mirror":""),age,source,weight,world,tail.firedAt);
    }
    private static final ThreadLocal<RCache> CACHE=ThreadLocal.withInitial(RCache::new);
    private static final class RCache{float at=Float.NaN;Matrix4f[] pose;}
    private static Matrix4f[] pose(float at){if(at==1)return READY;var cache=CACHE.get();if(at!=cache.at){cache.at=at;cache.pose=R.sample(at);}return cache.pose;}
    public static float projectPlayer(AbstractClientPlayer p,float partial,Matrix4f[] playerBones){
        Clock clock=clock(p,partial);
        float feet=projectBase(p,partial,playerBones,clock);
        RooftopRig.recoil(playerBones,clock.key,clock.tick);
        return feet;
    }
    private static float projectBase(AbstractClientPlayer p,float partial,Matrix4f[] playerBones,Clock clock){
        boolean standby=clock.key().startsWith("spines_rack_still");
        boolean groundShot=RooftopRig.groundShot(clock.key());
        var phase=com.codex.blacklight.client.PrototypeAnimationController.phaseFor(p);
        boolean standing=p.onGround()&&!p.isPassenger()&&!p.isCrouching()&&!p.isInWaterOrBubble()
                &&(phase==com.codex.blacklight.movement.LocomotionPhase.IDLE||phase.isGroundMovement());
        if (!standing && (standby||groundShot||clock.key().equals("spines_scapula_deploy"))) return 0;
        float gait=PlayerMotionController.spineStride(p,partial);
        boolean plant=standing&&(groundShot||standby&&gait<.999F);
        boolean project=clock.bodyWeight>0&&RooftopRig.fullBody(clock.key())&&!p.isPassenger();
        if(!plant&&!project || standby&&gait>=.999F){
            if(standing)aim(p,partial,playerBones,clock);
            return 0;
        }
        if(project){
            var nativePose=clock.bodyWeight<1?playerBones.clone():null;
            R.projectPlayer(pose(clock.source),RooftopRig.reflect(clock.key()),playerBones);
            if(nativePose!=null)for(int i=0;i<playerBones.length;i++)
                blendRigid(playerBones[i],nativePose[i],1-clock.bodyWeight);
        }
        if(plant)RooftopRig.plantGroundShot(playerBones);
        if(gait>0){
            var sk=BlacklightSkeleton.get();
            var stride=sk.getPoseMatrices(PlayerMotionController.locomotionPose(p,partial));
            RooftopRig.groundStride(playerBones,stride,gait,standby);
        }
        if(clock.key.equals("spines_scapula_deploy")){
            float brace=ScapulaVolley.ease(clock.tick/4)*(1-ScapulaVolley.ease((clock.tick-4)/3));
            float open=ScapulaVolley.ease((clock.tick-5)/3)*(1-ScapulaVolley.ease((clock.tick-9)/7));
            RooftopRig.aimUpperBody(playerBones,10*brace-7*open,15*brace-8*open);
        }
        if(standing)aim(p,partial,playerBones,clock);
        return (1-gait)*(plant?1:clock.bodyWeight);
    }
    private static void aim(AbstractClientPlayer p,float partial,Matrix4f[] bones,Clock clock){
        var tail=tails.get(p);
        if(tail==null)return;
        double now=p.tickCount+(double)partial;
        float dt=(float)(Double.isFinite(tail.lastAim)?Math.max(0,Math.min(2,now-tail.lastAim)):1);
        if(now-tail.lastAim>8){tail.aimYaw=0;tail.aimPitch=0;}
        tail.lastAim=now;
        float yaw=0,pitch=0;
        if(RooftopRig.groundShot(clock.key())){
            var target=p.level().getEntity(p.getData(ModAttachments.QUILL_AIM_TARGET));
            if(target!=null && target.isAlive()){
                var delta=target.getPosition(partial).add(0,target.getBbHeight()*.55,0).subtract(p.getEyePosition(partial));
                float facing=com.codex.blacklight.client.animation.BlacklightRenderPose.facing(p,partial);
                boolean rearRight=Math.abs(tail.aimYaw)>8 ? tail.aimYaw>0 : ScapulaCycle.rightLead(clock.key());
                yaw=RooftopRig.shoulderTurn(delta,facing,rearRight);
                pitch=RooftopRig.shoulderPitch(delta);
            }
        }
        tail.aimYaw=net.minecraft.util.Mth.approach(tail.aimYaw,yaw,dt*24);
        tail.aimPitch=net.minecraft.util.Mth.approach(tail.aimPitch,pitch,dt*10);
        RooftopRig.aimUpperBody(bones,tail.aimYaw,tail.aimPitch);
    }
    private static void blendRigid(Matrix4f a,Matrix4f b,float weight){
        var position=a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()),weight);
        var rotation=a.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize()
                .slerp(b.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize(),weight);
        a.translationRotateScale(position,rotation,a.getScale(new Vector3f()).lerp(b.getScale(new Vector3f()),weight));
    }
    public static void rack(Clock c,PoseStack stack,MultiBufferSource buf,int light,Matrix4f nativeChest,WeaponMorphTracker.Sample morph,int side){
        float growth=morph.shoulder();
        var frames=new Matrix4f[R.parent.length];
        var wetness=new float[frames.length];
        java.util.Arrays.fill(wetness,1-growth);
        for(int slot=0;slot<6;slot++)wetness[R.quill[slot]]=Math.max(1-growth,1-c.fill(slot));
        var source=pose(Math.min(3.88F,c.source));
        for(int n=0;n<frames.length;n++){
            if(R.attached(n)&&(side<0||R.side(n)==side))frames[n]=organFrame(c,source,n,nativeChest);
        }
        float[] pulse=chargeWave(c);
        for(int pass=0;pass<(pulse==null?3:4);pass++){
            boolean circulation=pass==3;
            boolean liquid=pass==1,veins=pass==2;
            VertexConsumer out=null;
            for(int n=0;n<frames.length;n++){
                var m=frames[n];if(m==null||Math.abs(m.determinant3x3())<.000015F)continue;
                if(!circulation&&!veins&&liquid!=(wetness[n]>.005F))continue;
                if(out==null)out=buf.getBuffer(circulation?RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE):
                        liquid?LiquidBiomassMaterial.mesh(ConsumedMobVisuals.WHITE):material(veins));
                stack.pushPose();BlacklightBoneRendering.apply(stack,m);if(RooftopRig.reflect(c.key))stack.scale(-1,1,1);
                stack.scale(growth,growth,growth);
                if(circulation)drawVessels(R.geometry[R.mesh[n]],stack,out,pulse);
                else part(R.mesh[n],stack,out,light,veins,liquid?wetness[n]:0);
                stack.popPose();
            }
        }
        if(morph.transitioning() && morph.flow()>.01F && growth<.999F){
            for(int joint:R.quill){
                var frame=frames[joint];
                if(frame==null || Math.abs(frame.determinant3x3())<.000015F)continue;
                stack.pushPose();
                BlacklightBoneRendering.apply(stack,frame);
                WeaponMorphTendrils.socket(stack,buf,light,growth,morph.flow());
                stack.popPose();
            }
        }
    }
    private static float[] chargeWave(Clock c){
        if(c.key.equals("spines_scapula_deploy")){
            var wave=new float[24];
            for(int i=0;i<wave.length;i++){
                float t=c.tick-3.4F-i*.29F;
                wave[i]=ScapulaVolley.ease(t/1.2F)*(1-ScapulaVolley.ease((t-1.2F)/2.8F));
            }
            return wave;
        }
        float load;
        if(c.key.endsWith("_draw"))load=ScapulaVolley.ease(c.tick/(c.key.startsWith("spines_air_")?5:9));
        else if(c.key.endsWith("_charged"))load=1-ScapulaVolley.ease((c.tick-(c.key.startsWith("spines_air_")?1:5))/3);
        else return null;
        if(load<.012F)return null;
        var wave=new float[24];
        for(int i=0;i<wave.length;i++){
            float beat=ScapulaVolley.vascularPulse(c.now,i/23F);
            wave[i]=load*(.16F+.84F*beat);
        }
        return wave;
    }
    private static void drawVessels(RooftopRig.Geometry g,PoseStack stack,VertexConsumer out,float[] wave){
        float[] pos=g.pos(),normal=g.normal();
        byte[] band=g.vessels();
        for(int first=0;first<band.length;first+=3){
            if(band[first]<0)continue;
            int tile=(int)(9*g.uv()[first*2]);
            float intensity=tile==5?.46F:tile==6?.79F:1;
            for(int corner=0;corner<4;corner++){
                int v=first+Math.min(corner,2),i=v*3;
                float pulse=wave[band[v]], lift=.0025F+.012F*intensity*pulse;
                int alpha=Math.round(242*intensity*pulse);
                int green=Math.round(8+55*pulse*pulse),blue=Math.round(14+28*pulse);
                int tint=alpha<<24|0xff0000|green<<8|blue;
                vert(stack,out,pos[i]+normal[i]*lift,pos[i+1]+normal[i+1]*lift,pos[i+2]+normal[i+2]*lift,
                        tint,.5F,.5F,0xf000f0,OverlayTexture.NO_OVERLAY,normal[i],normal[i+1],normal[i+2]);
            }
        }
    }
    /** Body recovery plays to completion while the equipped sockets return directly to their ready angles. */
    static Matrix4f organFrame(Clock c,Matrix4f[] source,int node,Matrix4f nativeChest){
        boolean deploy=c.key.equals("spines_scapula_deploy");
        int slot = deploy ? -1 : R.socketSlot(node);
        boolean quill = slot >= 0 && node == R.quill[slot];
        int driver = quill ? R.parent[node] : node;
        float ready=ScapulaVolley.ease((c.source-3.78F)/.86F);
        if(c.key.startsWith("spines_rack_"))ready=Math.max(ready,1-c.bodyWeight);
        var m=R.frame(ready>=1?READY:source,driver,c.key,nativeChest);
        if(ready>0&&ready<1)blendRigid(m,R.frame(READY,driver,c.key,nativeChest),ready);
        if(deploy && R.socketSlot(node)>=0){
            int outlet=R.socketSlot(node);
            float fill=RooftopRig.deployFill(c.tick,outlet);
            R.refillFrame(m,node,fill,1.1,false);
        }
        if (slot >= 0) R.refillFrame(m, node, c.fill(slot), c.sinceShot(slot), RooftopRig.reflect(c.key));
        return m;
    }
    public static void part(int mesh,PoseStack stack,VertexConsumer out,int light,boolean veins,float wet){
        var g=R.geometry[mesh];var p=g.pos();var n=g.normal();var uv=g.uv();
        for(int j=0;j<p.length;j+=9){
            if(g.emissive(j/3)!=veins)continue;
            for(int i=0;i<4;i++){
                int vertex=j/3+Math.min(i,2),k=3*vertex;
                int tint=wet>0?com.codex.blacklight.client.animation.BiomassSurface.color(g.color(vertex),wet,0,0):0xffffffff;
                vert(stack,out,p[k],p[k+1],p[k+2],tint,wet>0?wet:uv[2*vertex],wet>0?.5F:uv[2*vertex+1],veins?0xf000f0:light,
                        OverlayTexture.NO_OVERLAY,n[k],n[k+1],n[k+2]);
            }
        }
    }
    private static void vert(PoseStack s,VertexConsumer out,float x,float y,float z,int color,float u,float v,int light,int overlay,float nx,float ny,float nz){
        out.addVertex(s.last(),x,y,z).setColor(color).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(s.last(),nx,ny,nz);
    }
}
