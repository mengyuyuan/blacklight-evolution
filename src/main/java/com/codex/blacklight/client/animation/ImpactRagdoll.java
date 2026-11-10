package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.*;
import java.util.*;
import java.lang.Math;

/** Bounded, fixed-tick Verlet bodies. Server entities remain authoritative for living targets.
 * Adult humanoid rigs only; other rigs keep their species-specific receiver animations. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ImpactRagdoll {
    private static final int LIMIT=32, LIFETIME=100;
    private static final Map<LivingEntity,Body> BODIES=new LinkedHashMap<>();
    // Neck, pelvis, crown, shoulders, hands, hips, feet in vanilla model units.
    private static final double[][] REST={{0,0,0},{0,12,0},{0,-7,0},{-5,2,0},{5,2,0},
            {-5,12,0},{5,12,0},{-2,12,0},{2,12,0},{-2,22.4,0},{2,22.4,0}};
    private static final int[][] LINKS={{0,1},{0,3},{0,4},{3,4},{3,1},{4,1},{1,7},{1,8},
            {7,8},{3,7},{4,8},{3,8},{4,7},{0,2},{2,3},{2,4},{3,5},{4,6},{7,9},{8,10}};
    private ImpactRagdoll() {}
    private static final class Body {
        final LivingEntity entity;
        final Vec3[] pos=new Vec3[REST.length],previous=new Vec3[REST.length],renderPrevious=new Vec3[REST.length];
        final double[] lengths=new double[LINKS.length];
        Vec3 origin, impactVelocity=Vec3.ZERO;
        final Map<Integer,Vec3> cutVelocities=new HashMap<>();
        long started, expires, freezeUntil, deadAt=-1;
        int still;
        Body(LivingEntity entity,RagdollImpactPayload hit){
            this.entity=entity;origin=entity.position();started=entity.level().getGameTime();
            for(int i=0;i<pos.length;i++){
                double[] r=REST[i];pos[i]=world(entity,new Vec3(r[0],r[1],r[2]),origin);
                previous[i]=renderPrevious[i]=pos[i];
            }
            for(int i=0;i<LINKS.length;i++)lengths[i]=pos[LINKS[i][0]].distanceTo(pos[LINKS[i][1]]);
            impulse(hit);
        }
        void impulse(RagdollImpactPayload hit){
            expires=entity.level().getGameTime()+hit.ticks();still=0;
            freezeUntil=entity.level().getGameTime()+Math.clamp(hit.freeze(),0,8);
            Vec3 velocity=new Vec3(hit.vx(),hit.vy(),hit.vz());
            impactVelocity=velocity;
            for(var cut:hit.cuts())cutVelocities.put(cut.region(),new Vec3(cut.x(),cut.y(),cut.z()));
            Vec3 side=Vec3.directionFromRotation(0,entity.yBodyRot+90);
            double sign=hit.motion()==ImpactProfile.LEFT?-1:1;
            for(int i=0;i<pos.length;i++){
                // Off-centre cuts torque the shoulders; blunt impacts push the whole body.
                double upper=i==0||i==2||i==3||i==4?1:0;
                boolean thrust=hit.sharp()&&(hit.motion()==ImpactProfile.BODY||hit.motion()==ImpactProfile.UP);
                Vec3 torque=thrust?velocity.normalize().scale(upper*.12):side.scale(sign*upper*(hit.sharp()?.16:.06));
                Vec3 kick=velocity.scale(.5).add(torque);
                if(i==5||i==6)kick=kick.add(side.scale((i==5?-1:1)*.06));
                previous[i]=pos[i].subtract(kick);
            }
        }
        boolean dead(){return deadAt>=0 || entity.isDeadOrDying();}
        float weight(float partial){
            if(dead())return 1;
            var hit=entity.getData(ModAttachments.HIT_REACTION);
            if(hit.kind()==HitReaction.GETUP && hit.weight(entity.level().getGameTime(),partial)>0)
                return 1-smooth(hit.progress(entity.level().getGameTime(),partial));
            return (float)Math.clamp((expires-entity.level().getGameTime()-partial)/6,0,1);
        }
        void step(){
            long now=entity.level().getGameTime();
            if(entity.isDeadOrDying()&&deadAt<0){deadAt=now;still=0;}
            System.arraycopy(pos,0,renderPrevious,0,pos.length);
            if(!dead()){
                Vec3 shift=entity.position().subtract(origin);origin=entity.position();
                for(int i=0;i<pos.length;i++){pos[i]=pos[i].add(shift);previous[i]=previous[i].add(shift);}
                if(entity.getData(ModAttachments.HIT_REACTION).kind()==HitReaction.DOWN)expires=Math.max(expires,now+8);
            }
            if(now<freezeUntil||dead()&&still>=12)return;
            // Two substeps per game tick: independent of FPS, bounded cost.
            for(int sub=0;sub<2;sub++){
                Vec3[] before=pos.clone();
                boolean water=entity.level().getFluidState(net.minecraft.core.BlockPos.containing(pos[1])).isSource();
                for(int i=0;i<pos.length;i++){
                    Vec3 velocity=pos[i].subtract(previous[i]).scale(water?.70:.985);
                    if(velocity.lengthSqr()>1)velocity=velocity.normalize();
                    previous[i]=pos[i];pos[i]=pos[i].add(velocity).add(0,water?-.003:-.02,0);
                }
                for(int iteration=0;iteration<6;iteration++){
                    for(int i=0;i<LINKS.length;i++)distance(LINKS[i][0],LINKS[i][1],lengths[i],1);
                    // Prevent the head folding into the pelvis; bound limb swing away from the torso.
                    minimum(2,1,.78);minimum(5,1,.25);minimum(6,1,.25);minimum(9,0,.85);minimum(10,0,.85);
                    if(!dead()){
                        double height=entity.onGround()?.30:.75;
                        Vec3 anchor=entity.position().add(0,height,0);
                        Vec3 correction=anchor.subtract(pos[1]).scale(.45);
                        for(int i=0;i<pos.length;i++)pos[i]=pos[i].add(correction);
                    }
                }
                for(int i=0;i<pos.length;i++)collide(i,before[i]);
            }
            double motion=0;for(int i=0;i<pos.length;i++)motion+=pos[i].distanceToSqr(renderPrevious[i]);
            still=motion<.00015?still+1:0;
        }
        void distance(int a,int b,double length,double strength){
            Vec3 delta=pos[b].subtract(pos[a]);double d=delta.length();if(d<1e-6)return;
            Vec3 correction=delta.scale((d-length)/d*.5*strength);
            pos[a]=pos[a].add(correction);pos[b]=pos[b].subtract(correction);
        }
        void minimum(int a,int b,double length){if(pos[a].distanceToSqr(pos[b])<length*length)distance(a,b,length,.65);}
        void collide(int i,Vec3 from){
            double r=i==2?.20:i==0||i==1?.16:.09;
            Vec3 requested=pos[i].subtract(from);
            AABB box=new AABB(from.x-r,from.y-r,from.z-r,from.x+r,from.y+r,from.z+r);
            Vec3 allowed=Entity.collideBoundingBox(null,requested,box,entity.level(),List.of());
            pos[i]=from.add(allowed);
            Vec3 velocity=pos[i].subtract(previous[i]);
            boolean x=Math.abs(allowed.x-requested.x)>1e-5,y=Math.abs(allowed.y-requested.y)>1e-5,z=Math.abs(allowed.z-requested.z)>1e-5;
            if(x||y||z){
                double bounce=velocity.lengthSqr()>.018?.16:0;
                velocity=new Vec3(x?-velocity.x*bounce:velocity.x*.72,y?-velocity.y*bounce:velocity.y*.72,z?-velocity.z*bounce:velocity.z*.72);
                previous[i]=pos[i].subtract(velocity);
            }
        }
        Vec3 point(int index,float partial){return renderPrevious[index].lerp(pos[index],partial);}
    }
    private static float smooth(float t){return t*t*(3-2*t);}
    private static Vec3 world(LivingEntity e,Vec3 model,Vec3 origin){
        return new Vec3(-model.x/16,1.501-model.y/16,model.z/16)
                .yRot((float)Math.toRadians(180-e.yBodyRot)).add(origin);
    }
    private static Vec3 local(LivingEntity e,Vec3 point,float partial){
        float yaw=net.minecraft.util.Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);
        Vec3 p=point.subtract(e.getPosition(partial)).yRot((float)Math.toRadians(yaw-180));
        return new Vec3(-p.x*16,(1.501-p.y)*16,p.z*16);
    }
    public static void accept(RagdollImpactPayload hit){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(!(mc.level.getEntity(hit.entityId()) instanceof LivingEntity e)||e.isBaby()||e instanceof net.minecraft.world.entity.player.Player
                ||Math.abs(e.getScale()-1)>.01)return;
        if(!Double.isFinite(hit.vx()+hit.vy()+hit.vz())||new Vec3(hit.vx(),hit.vy(),hit.vz()).lengthSqr()>9.01)return;
        com.codex.blacklight.client.renderer.WoundBlood.accept(e,hit);
        if(e instanceof net.minecraft.world.entity.animal.Pig){
            com.codex.blacklight.client.renderer.PigRagdoll.accept(e,hit);return;
        }
        if(!(mc.getEntityRenderDispatcher().getRenderer(e) instanceof LivingEntityRenderer<?,?> renderer)
                ||!(renderer.getModel() instanceof HumanoidModel<?>))return;
        if(hit.severMask()!=0)e.setData(ModAttachments.DISMEMBER,e.getData(ModAttachments.DISMEMBER)|(hit.severMask()&WeaponDismemberment.ALL));
        // Small cuts already have directional authored flinches. Physical takeover is for falls/death.
        if(hit.ticks()<12 && !e.isDeadOrDying())return;
        var body=BODIES.get(e);
        if(body==null){if(BODIES.size()>=LIMIT)BODIES.remove(BODIES.keySet().iterator().next());BODIES.put(e,new Body(e,hit));}
        else body.impulse(hit);
    }
    public static boolean active(LivingEntity e){
        if(com.codex.blacklight.client.renderer.PigRagdoll.active(e))return true;
        var b=BODIES.get(e);if(b==null||e.isInvisible())return false;
        var capture=e.getData(ModAttachments.CONSUME_VISUAL);
        return !e.isPassenger()&&!e.isSleeping()&&(!capture.active()||capture.stage()==ConsumeVisual.FLIGHT)&&b.weight(0)>0;
    }
    public static Vec3 impactVelocity(LivingEntity e){var b=BODIES.get(e);return b==null?e.getDeltaMovement():b.impactVelocity;}
    public static Vec3 cutVelocity(LivingEntity e,int region){var b=BODIES.get(e);return b==null?e.getDeltaMovement():b.cutVelocities.getOrDefault(region,b.impactVelocity.scale(.65));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null){BODIES.clear();return;}if(mc.isPaused())return;
        var iterator=BODIES.entrySet().iterator();
        while(iterator.hasNext()){
            var b=iterator.next().getValue();var e=b.entity;long now=mc.level.getGameTime();
            var capture=e.getData(ModAttachments.CONSUME_VISUAL);
            boolean captured=capture.active()&&capture.stage()!=ConsumeVisual.FLIGHT;
            boolean far=mc.player==null||mc.player.position().distanceToSqr(b.pos[1])>64*64;
            if(e.level()!=mc.level||captured||e.isPassenger()||e.isSleeping()||e.isInvisible()||far||now-b.started>160
                    ||b.deadAt>=0&&now-b.deadAt>LIFETIME ||!b.dead()&&(e.isRemoved()||now>b.expires+16)
                    ||!mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(b.pos[1]))
                    ||!b.dead()&&e.position().distanceToSqr(b.origin)>16){iterator.remove();continue;}
            b.step();
        }
    }
    /** Invoked after vanilla animation, with restoration owned by ConsumeVictimPose. */
    public static boolean pose(EntityModel<?> model,LivingEntity e,float partial,Map<ModelPart,PartPose> saved){
        if(!(model instanceof HumanoidModel<?> h)||!active(e))return false;
        var b=BODIES.get(e);float weight=b.weight(partial);
        Vec3[] p=new Vec3[REST.length];for(int i=0;i<p.length;i++)p[i]=local(e,b.point(i,partial),partial);
        segment(h.body,p[0],p[1],weight,saved);
        // The shoulder line supplies axial twist, not just the torso's pitch.
        Vec3 down=p[1].subtract(p[0]).normalize(),right=p[4].subtract(p[3]).normalize();
        Vec3 front=right.cross(down).normalize();right=down.cross(front).normalize();
        if(front.lengthSqr()>.5){
            Quaternionf rotation=new Quaternionf().setFromNormalized(new Matrix3f(
                    (float)right.x,(float)right.y,(float)right.z,(float)down.x,(float)down.y,(float)down.z,(float)front.x,(float)front.y,(float)front.z));
            var bind=saved.get(h.body);
            Vector3f a=new Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot).slerp(rotation,weight).getEulerAnglesZYX(new Vector3f());
            h.body.setRotation(a.x,a.y,a.z);
        }
        // Head geometry extends upward from its pivot; reverse the desired axis.
        segment(h.head,p[0],p[0].add(p[0].subtract(p[2])),weight,saved);
        saved.putIfAbsent(h.hat,h.hat.storePose());h.hat.copyFrom(h.head);
        segment(h.rightArm,p[3],p[5],weight,saved);segment(h.leftArm,p[4],p[6],weight,saved);
        segment(h.rightLeg,p[7],p[9],weight,saved);segment(h.leftLeg,p[8],p[10],weight,saved);
        return true;
    }
    private static void segment(ModelPart part,Vec3 from,Vec3 to,float weight,Map<ModelPart,PartPose> saved){
        saved.putIfAbsent(part,part.storePose());Vec3 d=to.subtract(from).normalize();
        if(d.lengthSqr()<.5)return;
        Quaternionf target=new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f((float)d.x,(float)d.y,(float)d.z));
        Quaternionf original=new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot);
        Vector3f angles=original.slerp(target,weight).getEulerAnglesZYX(new Vector3f());
        part.setPos(net.minecraft.util.Mth.lerp(weight,part.x,(float)from.x),net.minecraft.util.Mth.lerp(weight,part.y,(float)from.y),net.minecraft.util.Mth.lerp(weight,part.z,(float)from.z));
        part.setRotation(angles.x,angles.y,angles.z);
    }
    /** Retain only a visual model after vanilla removes the dead entity; no duplicate entity/loot. */
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void corpses(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);var camera=event.getCamera().getPosition();
        for(var b:BODIES.values()){
            var e=b.entity;if(e.level()!=mc.level||!e.isRemoved()||!b.dead()||!active(e))continue;
            var renderer=mc.getEntityRenderDispatcher().getRenderer(e);
            var stack=event.getPoseStack();Vec3 origin=e.getPosition(partial).subtract(camera);
            stack.pushPose();
            try{stack.translate(origin.x,origin.y,origin.z);renderer.render(e,e.getYRot(),partial,stack,mc.renderBuffers().bufferSource(),
                    net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,net.minecraft.core.BlockPos.containing(b.pos[1])));}
            finally{stack.popPose();}
        }
    }
}
