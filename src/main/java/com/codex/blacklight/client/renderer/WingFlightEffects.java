package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.WingFlightVisuals;
import com.codex.blacklight.client.model.SovereignWingModel;
import com.codex.blacklight.movement.WingFlightState;
import com.codex.blacklight.movement.WingFlightSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import java.util.*;

/** Bounded observer-local wake. No entities, terrain writes or per-particle packets. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WingFlightEffects {
    private record Sample(double time,Vec3[] tips,float flow){}
    private record Pulse(double born,Vec3 center,Vec3 normal,float strength,boolean ground){}
    private static final class Track {
        final ArrayDeque<Sample> samples=new ArrayDeque<>();
        int phase=-1;long launch=Long.MIN_VALUE;boolean fast;float thrust,brake;
        Vec3 head;double headAt;
        Vec3[][] edges;Vec3[] relativeTips;double edgeAt;
        final float[] downstroke=new float[2];
        double shockBorn=-100;
        final com.codex.blacklight.movement.WingFlightTuning.SonicGate sonic=new com.codex.blacklight.movement.WingFlightTuning.SonicGate();
    }
    private static final Map<Player,Track> TRACKS=new WeakHashMap<>();
    private static final ArrayDeque<Pulse> PULSES=new ArrayDeque<>();
    private static Object level;
    private static final org.joml.Matrix4f HEAD_BIND_INVERSE=new org.joml.Matrix4f(
            com.codex.blacklight.animation.BlacklightSkeleton.get().getBoundTransformFor(
                    new com.codex.blacklight.animation.BlacklightPose(),com.codex.blacklight.animation.BlacklightSkeleton.get().head)).invert();
    public static void captureHead(Player player,PoseStack stack,org.joml.Matrix4f[] bones,float partial){
        world();var mc=Minecraft.getInstance();
        if(player.isInvisible()||VisionPowerRenderer.drawingSilhouettes()
                ||player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>64*64)return;
        if(!TRACKS.containsKey(player)&&TRACKS.size()>=32)return;
        var skeleton=com.codex.blacklight.animation.BlacklightSkeleton.get();
        var skin=new org.joml.Matrix4f(bones[skeleton.head.getId()]).mul(HEAD_BIND_INVERSE);
        var point=skin.transformPosition(new org.joml.Vector3f(0,28F/16,0));stack.last().pose().transformPosition(point);
        var track=TRACKS.computeIfAbsent(player,p->new Track());
        track.head=new Vec3(point.x,point.y,point.z).add(mc.gameRenderer.getMainCamera().getPosition());
        track.headAt=player.level().getGameTime()+partial;
    }
    private static void world(){
        var current=Minecraft.getInstance().level;
        if(level!=current){TRACKS.clear();PULSES.clear();level=current;}
    }
    public static void capture(Player player,PoseStack stack,SovereignWingModel model,float partial){
        world();var mc=Minecraft.getInstance();
        if(mc.level==null||player.isInvisible()||VisionPowerRenderer.drawingSilhouettes()
                ||player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>64*64)return;
        var visual=WingFlightVisuals.sample(player,partial);
        if(visual.flow()<.025F)return;
        if(!TRACKS.containsKey(player)&&TRACKS.size()>=32)return;
        var track=TRACKS.computeIfAbsent(player,p->new Track());
        double now=player.level().getGameTime()+partial;
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        var origin=player.getPosition(partial);var edges=new Vec3[2][];var relative=new Vec3[2];
        for(int wing=0;wing<2;wing++){
            var edge=model.leadingEdge(wing==0?"left":"right");edges[wing]=new Vec3[edge.length];
            for(int i=0;i<edge.length;i++){
                var point=stack.last().pose().transformPosition(edge[i]);
                edges[wing][i]=new Vec3(point.x,point.y,point.z).add(camera);
            }
            relative[wing]=edges[wing][edge.length-1].subtract(origin);
            double dt=now-track.edgeAt;
            if(track.relativeTips!=null&&dt>1e-4&&dt<2){
                float push=(float)com.codex.blacklight.forge.WingMath.clamp((track.relativeTips[wing].y-relative[wing].y)/dt*3,0,1);
                track.downstroke[wing]+=(push-track.downstroke[wing])*(1-(float)Math.exp(-dt*.7));
            }
        }
        track.edges=edges;track.relativeTips=relative;track.edgeAt=now;
        if(!track.samples.isEmpty()&&now-track.samples.getLast().time<.45)return;
        Vec3[] points=new Vec3[4];int at=0;
        for(String side:new String[]{"left","right"})for(int tip:new int[]{1,3}){
            var p=stack.last().pose().transformPosition(model.tip(side,tip));
            points[at++]=new Vec3(p.x,p.y,p.z).add(camera);
        }
        if(!track.samples.isEmpty()&&(now<track.samples.getLast().time||now-track.samples.getLast().time>2
                ||points[0].distanceToSqr(track.samples.getLast().tips[0])>1600))track.samples.clear();
        track.samples.add(new Sample(now,points,visual.flow()));
        while(track.samples.size()>24)track.samples.removeFirst();
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        world();var mc=Minecraft.getInstance();if(mc.level==null)return;
        long now=mc.level.getGameTime();
        TRACKS.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=mc.level
                ||(!WingFlightSystem.equipped(e.getKey())&&e.getValue().samples.isEmpty()));
        PULSES.removeIf(p->now-p.born>12||p.born-now>2);
        for(var player:mc.level.players()){
            if(player.isInvisible()||player.isSpectator()||!WingFlightSystem.equipped(player)
                    ||player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition())>64*64)continue;
            if(!TRACKS.containsKey(player)&&TRACKS.size()>=32)continue;
            var track=TRACKS.computeIfAbsent(player,p->new Track());var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WING_FLIGHT);
            var visual=WingFlightVisuals.sample(player,1);
            var velocity=player.isControlledByLocalInstance()?player.getDeltaMovement():player.position().subtract(player.xo,player.yo,player.zo);
            var direction=velocity.lengthSqr()>.01?velocity.normalize():player.getLookAngle();
            double actualSpeed=velocity.length();
            if(track.sonic.update(now,actualSpeed,state.flying()&&actualSpeed<=24)){
                track.shockBorn=now;
                var point=track.head!=null&&now-track.headAt<2?track.head:player.getEyePosition();
                pulse(now,point,direction,3F,false);
                pulse(now+2,point,direction,2F,false);
            }
            if(state.phase()==WingFlightState.TAKEOFF&&now-state.started()>=WingFlightState.WINDUP_TICKS&&track.launch!=state.started()){
                track.launch=state.started();pulse(now,player.position().add(0,.08,0),new Vec3(0,1,0),.7F,true);
                dust(player,.7);flap(player,.32F,.85F);
            }
            if(state.phase()==WingFlightState.LAND&&track.phase!=WingFlightState.LAND&&track.phase>=0){
                pulse(now,player.position().add(0,.06,0),new Vec3(0,1,0),.45F,true);dust(player,.4);
            }
            // First hard push and the high-speed threshold have different bursts.
            if(state.flying()&&visual.thrust()>.60F&&track.thrust<=.60F&&visual.speed()>.35F){
                pulse(now,player.position().add(0,1,0),direction,.45F,false);flap(player,.22F,1.15F);
            }
            if(state.flying()&&visual.flow()>.80F&&!track.fast){
                track.fast=true;pulse(now,player.position().add(0,1,0),direction,.8F,false);
            }
            if(visual.flow()<.55F)track.fast=false;
            if(state.flying()&&visual.brake()>.65F&&track.brake<=.65F&&visual.speed()>.3F)
                pulse(now,player.position().add(0,1,0).add(direction.scale(.4)),direction,.3F,false);
            track.thrust=visual.thrust();track.brake=visual.brake();track.phase=state.phase();
        }
    }
    private static void pulse(double now,Vec3 point,Vec3 normal,float strength,boolean ground){
        if(PULSES.size()>=48)PULSES.removeFirst();
        PULSES.add(new Pulse(now,point,normal.normalize(),strength,ground));
    }
    private static void dust(Player p,double force){
        // A midair takeoff produces an air pulse, not ground dust.
        if(p.level().getBlockState(p.blockPosition().below(2)).isAir()&&p.level().getBlockState(p.blockPosition().below()).isAir())return;
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4,x=Math.cos(angle),z=Math.sin(angle);
            p.level().addParticle(ParticleTypes.POOF,p.getX()+x*.5,p.getY()+.1,p.getZ()+z*.5,x*.09*force,.025,z*.09*force);
        }
    }
    private static void flap(Player p,float volume,float pitch){
        p.level().playLocalSound(p.getX(),p.getY(),p.getZ(),SoundEvents.ENDER_DRAGON_FLAP,SoundSource.PLAYERS,volume,pitch,false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov event){
        if(!event.usedConfiguredFov()||com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView())return;
        if(com.codex.blacklight.client.camera.HydraCinematicCamera.active())return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.getCameraEntity()!=mc.player||mc.player.isScoping()||!WingFlightSystem.flying(mc.player))return;
        var v=WingFlightVisuals.sample(mc.player,(float)event.getPartialTick());
        var track=TRACKS.get(mc.player);double shock=0;
        if(track!=null){double age=(mc.level.getGameTime()+event.getPartialTick()-track.shockBorn)/7;
            if(age>=0&&age<1)shock=Math.sin(Math.PI*age)*2.4;}
        event.setFOV(event.getFOV()+(v.flow()*4+v.thrust()*v.flow()+v.streamline()*7+shock)*mc.options.fovEffectScale().get());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        world();var mc=Minecraft.getInstance();if(mc.level==null)return;
        double now=mc.level.getGameTime()+event.getPartialTick();
        var camera=event.getCamera().getPosition();var stack=event.getPoseStack();stack.pushPose();
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();
        try{
            stack.translate(-camera.x,-camera.y,-camera.z);var out=buffers.getBuffer(type);
            for(var player:TRACKS.keySet()){
                if(!WingFlightSystem.flying(player)||player.isInvisible())continue;
                var visual=WingFlightVisuals.sample(player,event.getPartialTick());
                var track=TRACKS.get(player);
                var wakeVelocity=player.isControlledByLocalInstance()?player.getDeltaMovement():player.position().subtract(player.xo,player.yo,player.zo);
                if(track.edges!=null&&now-track.edgeAt<1&&wakeVelocity.lengthSqr()>.0025)
                    wingAir(out,stack,track,wakeVelocity.normalize(),now,visual.flow(),visual.thrust(),visual.streamline(),camera);
                if(visual.mach()<.18F)continue;
                var velocity=player.isControlledByLocalInstance()?player.getDeltaMovement():player.position().subtract(player.xo,player.yo,player.zo);
                if(velocity.lengthSqr()<.01)continue;
                var center=track.head!=null&&now-track.headAt<1?track.head:player.getEyePosition(event.getPartialTick());
                var direction=velocity.normalize();
                headStreams(out,stack,center,direction,now,com.codex.blacklight.forge.WingMath.clamp((visual.mach()-.18F)/.82F,0,1),camera);
                if(visual.mach()>.55F){
                    float condensation=com.codex.blacklight.forge.WingMath.clamp((visual.mach()-.55F)/.45F,0,1);
                    condensation=condensation*condensation*(3-2*condensation);
                    shockMembrane(out,stack,center,direction,condensation,camera);
                }
            }
            for(var track:TRACKS.values()){
                while(!track.samples.isEmpty()&&now-track.samples.getFirst().time>5.5)track.samples.removeFirst();
                Sample previous=null;
                for(var current:track.samples){
                    if(previous!=null)for(int i=0;i<4;i++){
                        Vec3 a=previous.tips[i],b=current.tips[i],line=b.subtract(a);
                        if(line.lengthSqr()<1e-6)continue;
                        var width=line.cross(camera.subtract(a)).normalize();
                        double life=5.5-current.flow*2.5;
                        float fade=(float)com.codex.blacklight.forge.WingMath.clamp(1-(now-current.time)/life,0,1);
                        float strength=fade*fade*(float)Math.sqrt(current.flow)*(i%2==0?1:.5F);
                        if(strength<.01F)continue;
                        strip(out,stack,a,b,width.scale(.012+.018*strength),((int)(80*strength)<<24)|0xd6dfe8);
                        if(current.flow>.06F)strip(out,stack,a,b,width.scale(.005+.006*strength),((int)(110*strength)<<24)|0xc62436);
                    }
                    previous=current;
                }
            }
            for(var p:PULSES){
                double age=(now-p.born)/12;if(age<0||age>1)continue;
                var basis=p.normal.cross(Math.abs(p.normal.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
                var other=p.normal.cross(basis).normalize();
                double radius=.45+age*(p.ground?2.2:1.6)*p.strength;
                double width=.04*(1-age);int alpha=Math.min(255,(int)(100*(1-age)*(1-age)*p.strength));
                var center=p.center.subtract(p.normal.scale(p.ground?0:age*.5));
                for(int i=0;i<32;i++){
                    if(i%8==7)continue;
                    double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                    var u=basis.scale(Math.cos(a)).add(other.scale(Math.sin(a)));
                    var v=basis.scale(Math.cos(b)).add(other.scale(Math.sin(b)));
                    quad(out,stack,center.add(u.scale(radius-width)),center.add(u.scale(radius+width)),
                            center.add(v.scale(radius+width)),center.add(v.scale(radius-width)),alpha<<24|0xd4c7cd);
                }
            }
        }finally{stack.popPose();buffers.endBatch(type);}
    }
    private static void strip(VertexConsumer out,PoseStack stack,Vec3 a,Vec3 b,Vec3 width,int color){quad(out,stack,a.subtract(width),a.add(width),b.add(width),b.subtract(width),color);}
    private static void headStreams(VertexConsumer out,PoseStack stack,Vec3 head,Vec3 forward,double now,float force,Vec3 camera){
        var u=forward.cross(Math.abs(forward.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();var v=forward.cross(u);
        for(int streak=0;streak<8;streak++){
            double angle=streak*Math.PI/4+.2,phase=(now*.055+streak*.61803398875)%1;
            var radial=u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            Vec3 previous=null;
            for(int segment=0;segment<=8;segment++){
                double t=phase*.65+segment*.06;
                var point=head.add(forward.scale(.85-t*(2.3+force*1.7))).add(radial.scale(.43+t*.1+t*t*.22));
                if(previous!=null){
                    float fade=(float)Math.sin(segment/9.*Math.PI)*(float)Math.sin(phase*Math.PI)*force;
                    var width=point.subtract(previous).cross(camera.subtract(point)).normalize();
                    int rgb=streak%3==0?0xef334a:0xd9f0ff;
                    strip(out,stack,previous,point,width.scale(.022),((int)(45*fade)<<24)|rgb);
                    strip(out,stack,previous,point,width.scale(.006),((int)(210*fade)<<24)|rgb);
                }
                previous=point;
            }
        }
    }
    // Open bow surface IN FRONT of the head. Radius only widens downstream;
    // no rear hemisphere, cylindrical body enclosure or closed trailing rim.
    private static final double[] SHOCK_AXIAL={2.25,2.18,2.00,1.70,1.25,.65,.10,-.30};
    private static final double[] SHOCK_RADIUS={0,.18,.35,.54,.72,.86,.94,.98};
    private static void shockMembrane(VertexConsumer out,PoseStack stack,Vec3 center,Vec3 forward,float strength,Vec3 camera){
        var u=forward.cross(Math.abs(forward.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var v=forward.cross(u);
        for(int ring=0;ring<SHOCK_AXIAL.length-1;ring++){
            double z0=SHOCK_AXIAL[ring],z1=SHOCK_AXIAL[ring+1],r0=SHOCK_RADIUS[ring],r1=SHOCK_RADIUS[ring+1];
            var c0=center.add(forward.scale(z0));var c1=center.add(forward.scale(z1));
            for(int i=0;i<32;i++){
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16,mid=(a+b)*.5;
                var radial=u.scale(Math.cos(mid)).add(v.scale(Math.sin(mid)));
                var normal=radial.scale(z0-z1).add(forward.scale(r1-r0)).normalize();
                var midpoint=center.add(forward.scale((z0+z1)*.5)).add(radial.scale((r0+r1)*.5));
                double edge=1-Math.abs(normal.dot(camera.subtract(midpoint).normalize()));
                double tail=ring<4?1:ring==4?.65:ring==5?.28:.04;
                // Fresnel-like edge emphasis keeps the actor readable through the centre.
                int alpha=(int)((5+110*edge*edge*edge)*strength*tail);
                var ra=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                var rb=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
                quad(out,stack,c0.add(ra.scale(r0)),c1.add(ra.scale(r1)),c1.add(rb.scale(r1)),c0.add(rb.scale(r0)),alpha<<24|0xd9f2ff);
            }
        }
    }
    private static void wingAir(VertexConsumer out,PoseStack stack,Track track,Vec3 direction,double now,float flow,float thrust,float fast,Vec3 camera){
        var u=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var v=direction.cross(u);
        for(int wing=0;wing<2;wing++){
            var edge=track.edges[wing];float push=track.downstroke[wing];
            float strength=com.codex.blacklight.forge.WingMath.clamp(flow*(.12F+push*.62F+thrust*.25F+fast*.45F),0,1);
            // Condensation follows the current skinned edge, not a fixed shoulder plane.
            var span=edge[edge.length-1].subtract(edge[0]).normalize();
            var normal=span.cross(direction).normalize();
            for(int surface:new int[]{-1,1})for(int i=1;i<edge.length;i++){
                var offset=direction.scale(.10).add(normal.scale(surface*.16));
                var a=edge[i-1].add(offset);var b=edge[i].add(offset);
                var tail=direction.scale(-(.25+strength*.8));
                fadeStrip(out,stack,a,b,b.add(tail),a.add(tail),(int)(100*strength),0,0xd8edff);
            }
            // Loaded downstrokes shed short streamlets across the outer wing surface.
            for(int streak=0;streak<4;streak++){
                var start=edge[Math.min(edge.length-1,(streak+1)*edge.length/5)];Vec3 previous=null;
                double phase=(now*.12+streak*.23)%1;
                for(int i=0;i<=8;i++){
                    double t=i/8.;var point=start.subtract(direction.scale((phase*.35+t)*(.6+push*1.1)))
                            .add(0,-push*.22*t*t,0);
                    if(previous!=null){var width=point.subtract(previous).cross(camera.subtract(point)).normalize().scale(.012);
                        strip(out,stack,previous,point,width,((int)(150*strength*(1-t)*(1-phase))<<24)|0xe6f6ff);}
                    previous=point;
                }
            }
            // One counter-rotating tip vortex per wing, attached to its actual outer tip.
            Vec3 previous=edge[edge.length-1];
            for(int i=1;i<=24;i++){
                double t=i/24.,angle=(t*2.1-now*.055)*Math.PI*2*(wing==0?1:-1),radius=.14*t;
                var point=edge[edge.length-1].subtract(direction.scale(t*(1.5+flow*3)))
                        .add(u.scale(Math.cos(angle)*radius)).add(v.scale(Math.sin(angle)*radius));
                var width=point.subtract(previous).cross(camera.subtract(point)).normalize();
                strip(out,stack,previous,point,width.scale(.035),((int)(55*strength*(1-t))<<24)|0xbddfff);
                strip(out,stack,previous,point,width.scale(.010),((int)(180*strength*(1-t))<<24)|0xe8f5ff);
                previous=point;
            }
        }
    }
    private static void fadeStrip(VertexConsumer out,PoseStack stack,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int leading,int trailing,int rgb){
        Vec3[] points={a,b,c,d};
        for(int i=0;i<4;i++){var p=points[i];out.vertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z)
                .color(((i<2?leading:trailing)<<24)|rgb).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xf000f0).normal(stack.last().normal(),0,1,0).endVertex();}
    }
    private static void quad(VertexConsumer out,PoseStack stack,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){
        for(var p:new Vec3[]{a,b,c,d})out.vertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal(stack.last().normal(),0,1,0).endVertex();
    }
    private WingFlightEffects(){}
}
