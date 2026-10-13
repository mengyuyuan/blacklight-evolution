package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original world-space reconstruction of the source shockWave/three-joint
 * motion trails. No source mesh, texture, physics or camera rotation is used. */
public final class BulletFlightVisualRenderer {
    public record Frame(long session,double seconds,double height,double speed,Vec3 origin,Vec3 airflow,
                        DevastatorEffectTimeline.Flight optics) { }
    public record Statistics(long frame,boolean active,double seconds,double height,double downSpeed,
                             double strength,int airQuads,int tissueQuads,int shockQuads,int vertices,
                             double nearEnvironmentDistance) { }
    public record Draw(int vertices,int quads) { }
    private static final Draw EMPTY=new Draw(0,0);
    private static final Map<Integer,Vec3[]> JOINTS=new HashMap<>();
    private static Statistics statistics=new Statistics(0,false,0,0,0,0,0,0,0,0,-1);
    private BulletFlightVisualRenderer() { }
    public static Statistics statistics(){return statistics;}
    static void beforeFrame(long frame){statistics=new Statistics(frame,false,0,0,0,0,0,0,0,0,-1);}
    static void resetBodies(){JOINTS.clear();}
    /** Read true release-age rather than the held flight animation's pose time. */
    public static Frame sample(LivingEntity owner,float partial){
        if(owner==null||!owner.isAlive()||owner.onGround())return null;
        var visual=owner.getData(ModAttachments.DEVASTATOR_VISUAL);
        if(visual.kind()!=3||visual.phase()!=2||visual.released()<0)return null;
        Vec3 origin=owner.getPosition(partial),motion=owner.getDeltaMovement();
        double seconds=Math.max(0,(owner.level().getGameTime()-visual.released()+partial)/20D-1D/6);
        double height=Math.max(visual.fallHeight(),Math.max(0,visual.origin().y-origin.y));
        double speed=Math.max(visual.downSpeed(),Math.max(0,-motion.y));
        var optics=DevastatorEffectTimeline.flight(height,speed,seconds+1D/6);
        var airflow=new Vec3(-motion.x,Math.max(.01,speed),-motion.z).normalize();
        return new Frame(visual.session(),seconds,height,speed,origin,airflow,optics);
    }
    /** Called by the existing world-model capture, after its native bone frame. */
    static void capture(LivingEntity owner,Matrix4f frame,Matrix4f[] poses,Vec3 camera){
        var rig=com.codex.blacklight.animation.BlacklightSkeleton.get();
        String[] joints={"Chest","Chest","Torso","Arm_R","Hand_R","Tool_R","Arm_L","Hand_L","Tool_L"};
        float[] y={.28F,.08F,.10F,0,0,-.03F,0,0,-.03F};
        Vec3[] points=new Vec3[9];
        for(int i=0;i<points.length;i++){
            var point=poses[rig.searchJointByName(joints[i]).getId()].transformPosition(new Vector3f(0,y[i],i<3?.12F:0));
            frame.transformPosition(point);points[i]=camera.add(point.x,point.y,point.z);
        }
        JOINTS.put(owner.getId(),points);
    }
    static Draw draw(RenderLevelStageEvent event,LivingEntity owner,int budget){
        var flight=sample(owner,event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if(flight==null||!flight.optics.active()||budget<4||VisionPowerRenderer.drawingSilhouettes())return EMPTY;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();
        if(flight.origin.distanceToSqr(camera)>96*96)return EMPTY;
        var buffers=mc.renderBuffers().bufferSource();var stack=event.getPoseStack();
        stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        int air=0,tissue=0,shock=0;
        var airMaterial=RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE);
        int light=mc.getEntityRenderDispatcher().getPackedLightCoords(owner,event.getPartialTick().getGameTimeDeltaPartialTick(false));
        try{
            var out=buffers.getBuffer(airMaterial);
            // Short pixel-like air streaks occupy a shell around the actor. Y is
            // tied to world distance, so nearby walls/ground give true upward
            // optic flow while the falling actor passes them. Never a HUD strip.
            for(int i=0;i<24&&4*(air+shock)+4<=budget;i++){
                double angle=i*2.3999632297+(flight.session&63)*.07;
                double period=6.0,offset=mod(flight.height+i*1.713,period)-1.4;
                double edge=DevastatorEffectTimeline.smooth(Math.min((offset+1.4)/.7,(4.6-offset)/.7));
                if(edge<.015)continue;
                Vec3 start=airStart(flight,i);
                Vec3 end=start.add(flight.airflow.scale(flight.optics.streakLength()*(.65+.35*noise(i+3,flight.session))));
                Vec3 across=new Vec3(-Math.sin(angle),0,Math.cos(angle)).scale(.013+.019*flight.optics.strength());
                int alpha=(int)(255*flight.optics.airAlpha()*edge);
                quad(stack,out,start.subtract(across),start.add(across),end.add(across),end.subtract(across),
                        alpha<<24|0xD5DEE2,light,.65F);
                air++;
            }
            // shockWave001: entry .033333.. .666667 plus .5s fade, recreated as
            // a thin square/octagonal world ring rather than a colored screen bar.
            if(flight.optics.shock()>.01){
                double radius=.35+Math.max(0,flight.seconds+1D/6-1D/30)*2.1,thickness=.065;
                Vec3 centre=flight.origin.add(0,.18,0);
                for(int i=0;i<8&&4*(air+shock)+4<=budget;i++){
                    double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
                    quad(stack,out,centre.add(Math.cos(a)*radius,0,Math.sin(a)*radius),
                            centre.add(Math.cos(b)*radius,0,Math.sin(b)*radius),
                            centre.add(Math.cos(b)*(radius+thickness),0,Math.sin(b)*(radius+thickness)),
                            centre.add(Math.cos(a)*(radius+thickness),0,Math.sin(a)*(radius+thickness)),
                            (int)(85*flight.optics.shock())<<24|0xCBD9DE,light,.8F);
                    shock++;
                }
            }
            buffers.endBatch(airMaterial);
            // Source cycle/fall use spine and Shoulder->Elbow->Wrist trails.
            // Their shader width/colour was not recovered. These narrow wet
            // ribbons are an authored biomass presentation on the actual joints.
            boolean localFirst=owner==mc.player&&mc.options.getCameraType().isFirstPerson();
            Vec3[] points=JOINTS.get(owner.getId());
            if(!localFirst&&points!=null){
                var material=LiquidBiomassMaterial.mesh(ConsumedMobVisuals.WHITE);
                out=buffers.getBuffer(material);
                for(int joint=0;joint<points.length;joint++){
                    double phase=flight.seconds*Math.PI*2/(joint<3?1.666667:.91)+joint*.73;
                    double length=.45+1.4*flight.optics.strength(),width=(joint<3?.036:.024)*(1+.12*Math.sin(phase));
                    Vec3 side=new Vec3(Math.cos(joint*2.2),0,Math.sin(joint*2.2));
                    for(int segment=0;segment<5&&4*(air+shock+tissue)+4<=budget;segment++){
                        double a=segment/5D,b=(segment+1)/5D;
                        Vec3 start=points[joint].add(flight.airflow.scale(length*a)).add(side.scale(Math.sin(phase+a*4)*.045*a));
                        Vec3 end=points[joint].add(flight.airflow.scale(length*b)).add(side.scale(Math.sin(phase+b*4)*.045*b));
                        Vec3 spread=side.scale(width*(1-.65*a));
                        int alpha=(int)(210*flight.optics.strength()*(1-b*.8));
                        quad(stack,out,start.subtract(spread),start.add(spread),end.add(spread.scale(1-.12)),end.subtract(spread.scale(1-.12)),
                                alpha<<24|(segment%3==1?0x500D17:0x19212B),light,.92F);
                        tissue++;
                    }
                }
                buffers.endBatch(material);
            }
        }finally{
            buffers.endBatch(airMaterial);stack.popPose();
        }
        int quads=air+tissue+shock;
        if(owner==mc.player)statistics=new Statistics(statistics.frame,true,flight.seconds,flight.height,flight.speed,
                flight.optics.strength(),air,tissue,shock,quads*4,nearEnvironment(owner,flight.origin));
        return new Draw(quads*4,quads);
    }
    private static void quad(PoseStack stack,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int colour,int light,float wet){
        var normal=b.subtract(a).cross(d.subtract(a)).normalize();
        for(var point:new Vec3[]{a,b,c,d})out.addVertex(stack.last(),(float)point.x,(float)point.y,(float)point.z)
                .setColor(colour).setUv(wet,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(stack.last(),(float)normal.x,(float)normal.y,(float)normal.z);
    }
    private static double nearEnvironment(LivingEntity owner,Vec3 origin){
        double closest=Double.POSITIVE_INFINITY;Vec3 start=origin.add(0,.8,0);
        for(var offset:new Vec3[]{new Vec3(6,0,0),new Vec3(-6,0,0),new Vec3(0,0,6),new Vec3(0,0,-6),new Vec3(0,-12,0)}){
            var hit=owner.level().clip(new ClipContext(start,start.add(offset),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(hit.getType()==HitResult.Type.BLOCK)closest=Math.min(closest,start.distanceTo(hit.getLocation()));
        }
        return Double.isFinite(closest)?closest:-1;
    }
    private static double mod(double value,double period){return value-Math.floor(value/period)*period;}
    static Vec3 airStart(Frame flight,int index){
        double angle=index*2.3999632297+(flight.session&63)*.07,radius=.85+.62*noise(index+7,flight.session);
        return flight.origin.add(Math.cos(angle)*radius,mod(flight.height+index*1.713,6)-1.4,Math.sin(angle)*radius);
    }
    private static double noise(int value,long session){return .5+.5*Math.sin(value*12.9898+(session&1023)*.071);}
}
