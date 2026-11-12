package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.*;
import java.util.*;
import java.lang.Math;

/** Pose-driven articulated bodies. Animation, impacts, holding and absorption share joint history.
 * Server entities retain authority over health, collision, rewards and capture ownership. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ImpactRagdoll {
    private static final int LIMIT=32,LIFETIME=100;
    private static final Map<LivingEntity,Body> BODIES=new LinkedHashMap<>();
    private static final Map<LivingEntity,RagdollImpactPayload> PENDING=new LinkedHashMap<>();
    private static final Map<LivingEntity,Sample> HISTORY=new WeakHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel scene;
    // Neck, pelvis, head tip, shoulders, hands, hips, feet, elbows and knees.
    private static final int[] REGION={32,32,1,2,4,2,4,8,16,8,16,2,4,8,16};
    private static final double[] MASS={12,18,5,4,4,2,2,7,7,3,3,3,3,5,5};
    private static final int[][] LINKS={{0,1},{0,3},{0,4},{3,4},{3,1},{4,1},{1,7},{1,8},
            {7,8},{3,7},{4,8},{3,8},{4,7},{0,2},{2,3},{2,4},{3,11},{11,5},{4,12},{12,6},{7,13},{13,9},{8,14},{14,10}};
    private record Sample(double time,Vec3[] points,Vec3[] velocities) {}
    private ImpactRagdoll() {}
    private static void resetScene(){
        var current=Minecraft.getInstance().level;
        if(scene!=current){BODIES.clear();PENDING.clear();HISTORY.clear();scene=current;}
    }
    private static Vec3 modelPoint(ModelPart part,double y){
        var v=new Vector3f(0,(float)y*part.yScale,0).rotate(new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot));
        return new Vec3(part.x+v.x,part.y+v.y,part.z+v.z);
    }
    private static Vec3 childPoint(ModelPart part,String name,double y){
        if(!part.hasChild(name))return modelPoint(part,y==0?6:12);
        var child=part.getChild(name);Vec3 at=modelPoint(child,y);
        var v=new Vector3f((float)at.x*part.xScale,(float)at.y*part.yScale,(float)at.z*part.zScale)
                .rotate(new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot));
        return new Vec3(part.x+v.x,part.y+v.y,part.z+v.z);
    }
    private static Sample sample(HumanoidModel<?> h,LivingEntity e,float partial){
        Vec3[] p={modelPoint(h.body,0),modelPoint(h.body,12),modelPoint(h.head,-7),
                modelPoint(h.rightArm,0),modelPoint(h.leftArm,0),childPoint(h.rightArm,"forearm",6),childPoint(h.leftArm,"forearm",6),
                modelPoint(h.rightLeg,0),modelPoint(h.leftLeg,0),childPoint(h.rightLeg,"shin",6),childPoint(h.leftLeg,"shin",6),
                childPoint(h.rightArm,"forearm",0),childPoint(h.leftArm,"forearm",0),childPoint(h.rightLeg,"shin",0),childPoint(h.leftLeg,"shin",0)};
        var frame=RagdollDrive.frame(e,partial);
        for(int i=0;i<p.length;i++)p[i]=RagdollDrive.transform(frame,new Vec3(-p[i].x/16,1.501-p[i].y/16,p[i].z/16));
        double now=e.level().getGameTime()+partial;var old=HISTORY.get(e);Vec3[] velocities=new Vec3[p.length];
        double dt=old==null?0:now-old.time;
        for(int i=0;i<p.length;i++)velocities[i]=dt>.05&&dt<3?RagdollDrive.limit(p[i].subtract(old.points[i]).scale(1/dt),1.2)
                :old!=null&&dt>=0&&dt<=.05?old.velocities[i]:RagdollDrive.limit(e.getDeltaMovement(),1.2);
        var result=new Sample(now,p,velocities);if(old==null||dt>.05)HISTORY.put(e,result);return result;
    }
    private static final class Body {
        final LivingEntity entity;
        final Vec3[] pos=new Vec3[15],previous=new Vec3[15],renderPrevious=new Vec3[15];
        final double[] lengths=new double[LINKS.length];
        final Map<Integer,Vec3> cutVelocities=new HashMap<>();
        Sample target;
        Vec3 impactVelocity=Vec3.ZERO,origin;
        long started,expires,freezeUntil,deadAt=-1;int still;boolean light;
        Body(LivingEntity entity,Sample pose,RagdollImpactPayload hit){
            this.entity=entity;origin=entity.position();started=entity.level().getGameTime();target=pose;
            for(int i=0;i<pos.length;i++){pos[i]=renderPrevious[i]=pose.points[i];previous[i]=pos[i].subtract(pose.velocities[i].scale(.5));}
            for(int i=0;i<LINKS.length;i++)lengths[i]=Math.max(.015,pos[LINKS[i][0]].distanceTo(pos[LINKS[i][1]]));
            expires=started+20;if(hit!=null)impulse(hit);
        }
        void impulse(RagdollImpactPayload hit){
            boolean soft=!RagdollDrive.fullImpact(hit);
            still=0;
            freezeUntil=Math.max(freezeUntil,entity.level().getGameTime()+Math.clamp(hit.freeze(),0,8));
            if(!soft){light=false;expires=freezeUntil+hit.ticks();}
            impactVelocity=new Vec3(hit.vx(),hit.vy(),hit.vz());
            cutVelocities.clear();
            for(var cut:hit.cuts())cutVelocities.put(cut.region(),new Vec3(cut.x(),cut.y(),cut.z()));
            for(int i=0;i<pos.length;i++){
                double contact=.15;
                for(var w:hit.wounds())if(REGION[i]==w.region()){
                    Vec3 at=RagdollDrive.transform(RagdollDrive.frame(entity,1),w.point());
                    contact=Math.max(contact,1/(1+pos[i].distanceToSqr(at)*12));
                }
                // Preserve motion already present in the joint; impulse acts locally and propagates through constraints.
                Vec3 jointImpulse=cutVelocities.getOrDefault(REGION[i],impactVelocity);
                Vec3 kick=jointImpulse.scale((soft?.12:.25)+contact*.45);
                if(REGION[i]==1||REGION[i]==2||REGION[i]==4)kick=kick.add(0,soft?.015:.035,0);
                previous[i]=previous[i].subtract(RagdollDrive.limit(kick,.65).scale(.5));
            }
        }
        boolean dead(){return deadAt>=0||entity.isDeadOrDying();}
        float weight(float partial){
            if(RagdollDrive.captured(entity))return 1;
            if(dead())return 1;
            var hit=entity.getData(ModAttachments.HIT_REACTION);
            if(hit.kind()==HitReaction.GETUP&&hit.weight(entity.level().getGameTime(),partial)>0)return 1-RagdollDrive.smooth(hit.progress(entity.level().getGameTime(),partial));
            return (light?.65F:1)*Math.clamp((expires-entity.level().getGameTime()-partial)/6F,0,1);
        }
        void step(){
            long now=entity.level().getGameTime();boolean captured=RagdollDrive.captured(entity);
            if(entity.isDeadOrDying()&&deadAt<0)deadAt=now;
            System.arraycopy(pos,0,renderPrevious,0,pos.length);
            if(captured){expires=now+16;still=0;deadAt=-1;}
            if(!dead()&&entity.getData(ModAttachments.HIT_REACTION).kind()==HitReaction.DOWN)expires=Math.max(expires,now+8);
            if(now<freezeUntil||dead()&&still>=12)return;
            float flow=RagdollDrive.flow(entity,1);
            double power=captured?RagdollDrive.power(entity):!dead()&&light?.22:0;
            Vec3 sink=captured?RagdollDrive.sink(entity,1):Vec3.ZERO;
            boolean fresh=target!=null&&now-target.time<3;
            for(int sub=0;sub<2;sub++){
                Vec3[] before=pos.clone();
                for(int i=0;i<pos.length;i++){
                    Vec3 velocity=pos[i].subtract(previous[i]).scale(.975);
                    if(power>0&&fresh)velocity=velocity.add(RagdollDrive.limit(target.points[i].subtract(pos[i]),.5).scale(power*.22));
                    if(flow>0)velocity=RagdollDrive.pull(pos[i],velocity.scale(2),sink,flow,.5).scale(.5);
                    previous[i]=pos[i];
                    boolean water=!entity.level().getFluidState(net.minecraft.core.BlockPos.containing(pos[i])).isEmpty();
                    pos[i]=pos[i].add(RagdollDrive.limit(velocity,.6)).add(0,(water?-.002:-.02)*(1-flow),0);
                }
                for(int iteration=0;iteration<7;iteration++){
                    double contraction=1-.96*flow;
                    for(int i=0;i<LINKS.length;i++)distance(LINKS[i][0],LINKS[i][1],lengths[i]*contraction,1);
                    minimum(2,1,.5*contraction);minimum(5,3,.16*contraction);minimum(6,4,.16*contraction);minimum(9,7,.24*contraction);minimum(10,8,.24*contraction);
                    if(captured&&RagdollDrive.attached(entity)&&flow==0&&fresh){
                        int grip=RagdollDrive.gripRegion(entity)==1?0:1;
                        // A single attachment preserves the grab while the remaining joints retain inertia.
                        pos[grip]=pos[grip].lerp(target.points[grip],.42);
                    }else if(!dead()&&!captured){
                        Vec3 anchor=entity.position().add(0,light?.75:entity.onGround()?.32:.75,0);
                        Vec3 correction=RagdollDrive.limit(anchor.subtract(pos[1]),.3).scale(light?.18:.055);
                        for(int i=0;i<pos.length;i++)pos[i]=pos[i].add(correction);
                    }
                }
                for(int i=0;i<pos.length;i++)collide(i,before[i]);
            }
            double motion=0;for(int i=0;i<pos.length;i++)motion+=pos[i].distanceToSqr(renderPrevious[i]);
            still=motion<.00015?still+1:0;origin=entity.position();
        }
        void distance(int a,int b,double length,double strength){
            Vec3 delta=pos[b].subtract(pos[a]);double d=delta.length();if(d<1e-6)return;
            double wa=1/MASS[a],wb=1/MASS[b];Vec3 correction=delta.scale((d-length)/d*strength);
            pos[a]=pos[a].add(correction.scale(wa/(wa+wb)));pos[b]=pos[b].subtract(correction.scale(wb/(wa+wb)));
        }
        void minimum(int a,int b,double min){if(pos[a].distanceToSqr(pos[b])<min*min)distance(a,b,min,.5);}
        void collide(int i,Vec3 from){
            double r=i==2?.18:i==0||i==1?.14:.075;Vec3 requested=pos[i].subtract(from);
            AABB box=new AABB(from.x-r,from.y-r,from.z-r,from.x+r,from.y+r,from.z+r);
            Vec3 allowed=Entity.collideBoundingBox(null,requested,box,entity.level(),List.of());pos[i]=from.add(allowed);
            Vec3 velocity=pos[i].subtract(previous[i]);
            boolean x=Math.abs(allowed.x-requested.x)>1e-5,y=Math.abs(allowed.y-requested.y)>1e-5,z=Math.abs(allowed.z-requested.z)>1e-5;
            if(x||y||z){double bounce=velocity.lengthSqr()>.018?.12:0;
                velocity=new Vec3(x?-velocity.x*bounce:velocity.x*.78,y?-velocity.y*bounce:velocity.y*.78,z?-velocity.z*bounce:velocity.z*.78);
                previous[i]=pos[i].subtract(velocity);}
        }
        Vec3 point(int index,float partial){return renderPrevious[index].lerp(pos[index],partial);}
    }
    public static void accept(RagdollImpactPayload hit){
        resetScene();var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(!(mc.level.getEntity(hit.entityId()) instanceof LivingEntity e)||!RagdollImpact.supportedBody(e)||e.isInvisible())return;
        if(!Double.isFinite(hit.vx()+hit.vy()+hit.vz())||new Vec3(hit.vx(),hit.vy(),hit.vz()).lengthSqr()>9.01)return;
        if(!hit.planes().isEmpty())e.setData(ModAttachments.DISMEMBER,e.getData(ModAttachments.DISMEMBER)|WeaponDismemberment.MESH_CUT);
        com.codex.blacklight.client.renderer.WoundBlood.accept(e,hit);
        RagdollDrive.impactFlash(e,hit);
        com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Ragdoll received victim={} id={} health={} ticks={} freeze={} adapter={}",e.getType().getDescriptionId(),e.getId(),e.getHealth(),hit.ticks(),hit.freeze(),com.codex.blacklight.client.renderer.AdaptiveRagdoll.supports(e)?"generic":e instanceof net.minecraft.world.entity.animal.Pig?"pig":"humanoid");
        if(com.codex.blacklight.client.renderer.AdaptiveRagdoll.supports(e)){
            BODIES.remove(e);PENDING.remove(e);
            com.codex.blacklight.client.renderer.PigRagdoll.release(e);
            com.codex.blacklight.client.renderer.AdaptiveRagdoll.accept(e,hit);return;
        }
        if(e instanceof net.minecraft.world.entity.animal.Pig){com.codex.blacklight.client.renderer.PigRagdoll.accept(e,hit);return;}
        if(!(mc.getEntityRenderDispatcher().getRenderer(e) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?> renderer)
                ||!(renderer.getModel() instanceof HumanoidModel<?>))return;
        if(hit.severMask()!=0)e.setData(ModAttachments.DISMEMBER,e.getData(ModAttachments.DISMEMBER)|(hit.severMask()&WeaponDismemberment.ALL));
        var body=BODIES.get(e);
        if(body!=null){if(!hit.refinement())body.impulse(hit);}
        else if(RagdollDrive.fullImpact(hit)){if(PENDING.size()>=LIMIT)PENDING.remove(PENDING.keySet().iterator().next());PENDING.put(e,hit);}
    }
    public static boolean active(LivingEntity e){
        if(com.codex.blacklight.client.renderer.PigRagdoll.active(e))return true;
        var b=BODIES.get(e);return RagdollDrive.supported(e)&&(PENDING.containsKey(e)||b!=null&&b.weight(0)>0);
    }
    public static boolean absorbing(LivingEntity e){return active(e)&&RagdollDrive.flow(e,1)>0;}
    public static Vec3 contact(LivingEntity e,int region,float partial){
        var body=BODIES.get(e);if(body==null||!active(e))return null;
        return region==1?body.point(0,partial).lerp(body.point(2,partial),4.0/7)
                :body.point(0,partial).lerp(body.point(1,partial),.5);
    }
    public static Vec3 impactVelocity(LivingEntity e){var b=BODIES.get(e);return b==null?e.getDeltaMovement():b.impactVelocity;}
    public static Vec3 cutVelocity(LivingEntity e,int region){
        var b=BODIES.get(e);if(b==null)return e.getDeltaMovement();
        Vec3 v=Vec3.ZERO;int count=0;for(int i=0;i<REGION.length;i++)if(REGION[i]==region){v=v.add(b.pos[i].subtract(b.previous[i]).scale(2));count++;}
        return b.cutVelocities.getOrDefault(region,count==0?b.impactVelocity:v.scale(1.0/count));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        resetScene();var mc=Minecraft.getInstance();if(scene==null||mc.isPaused())return;
        PENDING.keySet().removeIf(e->e.isRemoved()||e.level()!=scene||!RagdollDrive.supported(e));
        var iterator=BODIES.values().iterator();
        while(iterator.hasNext()){
            var b=iterator.next();var e=b.entity;long now=scene.getGameTime();boolean captured=RagdollDrive.captured(e);
            if(e.level()!=scene||e.isPassenger()||e.isSleeping()||e.isInvisible()||mc.player==null||mc.player.position().distanceToSqr(b.pos[1])>64*64
                    ||b.deadAt>=0&&now-b.deadAt>LIFETIME||!b.dead()&&(e.isRemoved()||!captured&&now>b.expires+16)
                    ||!scene.hasChunkAt(net.minecraft.core.BlockPos.containing(b.pos[1]))
                    ||!b.dead()&&!captured&&e.position().distanceToSqr(b.origin)>16){iterator.remove();continue;}
            b.step();
        }
    }
    /** Called after authored receiver/vanilla poses, before any shared model part is modified by physics. */
    public static boolean pose(EntityModel<?> model,LivingEntity e,float partial,Map<ModelPart,PartPose> saved){
        resetScene();if(!(model instanceof HumanoidModel<?> h)||!RagdollDrive.supported(e))return false;
        var previousPose=HISTORY.get(e);var authored=sample(h,e,partial);var b=BODIES.get(e);
        if(b==null&&(PENDING.containsKey(e)||RagdollDrive.captured(e))){
            var hit=PENDING.remove(e);
            if(BODIES.size()>=LIMIT)BODIES.remove(BODIES.keySet().iterator().next());
            b=new Body(e,hit!=null&&previousPose!=null&&authored.time-previousPose.time<3?previousPose:authored,hit);BODIES.put(e,b);
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Humanoid ragdoll built id={} joints={}",e.getId(),b.pos.length);
        }
        if(b==null)return false;b.target=authored;
        float weight=b.weight(partial);if(weight<=0)return false;
        var inverse=RagdollDrive.frame(e,partial).invert();Vec3[] p=new Vec3[15];
        for(int i=0;i<p.length;i++){var v=RagdollDrive.transform(inverse,b.point(i,partial));p[i]=new Vec3(-v.x*16,(1.501-v.y)*16,v.z*16);}
        segment(h.body,p[0],p[1],weight,saved);
        Vec3 down=p[1].subtract(p[0]).normalize(),right=p[4].subtract(p[3]).normalize();
        Vec3 front=right.cross(down).normalize();right=down.cross(front).normalize();
        if(front.lengthSqr()>.5){
            var rotation=new Quaternionf().setFromNormalized(new Matrix3f((float)right.x,(float)right.y,(float)right.z,
                    (float)down.x,(float)down.y,(float)down.z,(float)front.x,(float)front.y,(float)front.z));
            var bind=saved.get(h.body);var a=new Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot).slerp(rotation,weight).getEulerAnglesZYX(new Vector3f());
            h.body.setRotation(a.x,a.y,a.z);
        }
        segment(h.head,p[0],p[0].add(p[0].subtract(p[2])),weight,saved);
        saved.putIfAbsent(h.hat,h.hat.storePose());h.hat.copyFrom(h.head);
        limb(h.rightArm,"forearm",p[3],p[11],p[5],weight,saved);limb(h.leftArm,"forearm",p[4],p[12],p[6],weight,saved);
        limb(h.rightLeg,"shin",p[7],p[13],p[9],weight,saved);limb(h.leftLeg,"shin",p[8],p[14],p[10],weight,saved);
        return true;
    }
    private static void limb(ModelPart part,String child,Vec3 from,Vec3 middle,Vec3 end,float weight,Map<ModelPart,PartPose> saved){
        if(!part.hasChild(child)){segment(part,from,end,weight,saved);return;}
        segment(part,from,middle,weight,saved);var inverse=new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot).conjugate();
        var a=inverse.transform(new Vector3f((float)(middle.x-part.x),(float)(middle.y-part.y),(float)(middle.z-part.z)));
        var z=inverse.transform(new Vector3f((float)(end.x-part.x),(float)(end.y-part.y),(float)(end.z-part.z)));
        segment(part.getChild(child),new Vec3(a.x,a.y,a.z),new Vec3(z.x,z.y,z.z),weight,saved);
    }
    private static void segment(ModelPart part,Vec3 from,Vec3 to,float weight,Map<ModelPart,PartPose> saved){
        saved.putIfAbsent(part,part.storePose());Vec3 d=to.subtract(from).normalize();if(d.lengthSqr()<.5)return;
        var target=new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)d.x,(float)d.y,(float)d.z));
        var a=new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot).slerp(target,weight).getEulerAnglesZYX(new Vector3f());
        part.setPos(net.minecraft.util.Mth.lerp(weight,part.x,(float)from.x),net.minecraft.util.Mth.lerp(weight,part.y,(float)from.y),net.minecraft.util.Mth.lerp(weight,part.z,(float)from.z));
        part.setRotation(a.x,a.y,a.z);
    }
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void corpses(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var b:BODIES.values()){
            var e=b.entity;if(e.level()!=mc.level||!e.isRemoved()||!b.dead()||!active(e))continue;
            var renderer=mc.getEntityRenderDispatcher().getRenderer(e);var stack=event.getPoseStack();Vec3 origin=e.getPosition(partial).subtract(event.getCamera().getPosition());
            stack.pushPose();try{stack.translate(origin.x,origin.y,origin.z);renderer.render(e,e.getYRot(),partial,stack,mc.renderBuffers().bufferSource(),
                    net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(b.pos[1])));}
            finally{stack.popPose();}
        }
    }
}
