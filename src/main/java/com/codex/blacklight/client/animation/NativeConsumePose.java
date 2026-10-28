package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import com.codex.blacklight.animation.BlacklightPose;

/** Both participants sample the same synchronized server phase, including the controlling player. */
public final class NativeConsumePose {
    private NativeConsumePose() {}
    public static BlacklightPose sample(LivingEntity entity,ConsumeVisual visual,String role,float partial) {
        if(!visual.consuming()&&!GrappleMotion.active(visual))return null;
        var form=PowerForm.values()[Math.clamp(visual.form(),0,PowerForm.values().length-1)];
        boolean mounted=ConsumeSequence.mountedStage(visual.stage());
        String type=GrappleMotion.sceneKey(entity,visual);
        String key="consume_"+type+"_"+role;
        var accessor=MotionLibrary.find(key).orElse(null);
        if(accessor==null)return null;
        var clip=accessor;
        float time=visual.stage()==ConsumeVisual.HUNTER_CONSUME
                ?Math.min(HunterConsume.seconds(visual,entity.level().getGameTime(),partial),Math.max(0,clip.duration()-.001F))
                :(role.equals("victim")?GrappleMotion.receiverProgress(entity,visual,partial):GrappleMotion.sceneProgress(entity,visual,partial))*Math.max(0,clip.duration()-.001F);
        BlacklightPose copy=new BlacklightPose();MotionLibrary.pose(key,time,false).getJointTransformData().forEach((name,transform)->copy.getJointTransformData().put(name,transform.copy()));
        if(mounted&&role.equals("victim")){
            // A giant reacts at the contact site; a small receiver's whole-body toss must not tow it.
            copy.getJointTransformData().forEach((name,transform)->{
                transform.translation().set(0,0,0);
                float weight=name.equals("Head")?.8F:name.startsWith("Arm")||name.startsWith("Hand")?.55F:.4F;
                transform.rotation().set(new org.joml.Quaternionf().slerp(transform.rotation(),weight));
            });
            for(String joint:new String[]{"Root","Thigh_L","Thigh_R","Leg_L","Leg_R","Knee_L","Knee_R"})copy.orElseEmpty(joint).rotation().identity();
        }
        return copy;
    }
    public static boolean actor(LivingEntity entity,BlacklightPose pose,float partial) {
        if(recovery(entity,pose,partial))return true;
        int stage=entity.getData(ModAttachments.GRAB_STAGE);
        if(stage!=ConsumeVisual.CONSUME&&stage!=ConsumeVisual.CAPTURE&&stage!=ConsumeVisual.HOLD&&!ConsumeSequence.mountedStage(stage))return false;
        var victims=entity.level().getEntitiesOfClass(LivingEntity.class,entity.getBoundingBox().inflate(18),e->e.getData(ModAttachments.CONSUME_VISUAL).consumerId()==entity.getId());
        if(victims.isEmpty())return false;
        var visual=victims.getFirst().getData(ModAttachments.CONSUME_VISUAL);
        if(stage==ConsumeVisual.MOUNT||stage==ConsumeVisual.MOUNT_HOLD) {
            float age=entity.level().getGameTime()-visual.started()+partial;
            float stroke=stage==ConsumeVisual.MOUNT?(float)Math.sin(age*.55F)*.32F:0;
            for(String joint:new String[]{"Root","Torso","Chest","Head","Arm_L","Arm_R","Hand_L","Hand_R","Thigh_L","Thigh_R","Leg_L","Leg_R"})
                pose.orElseEmpty(joint).rotation().identity();
            pose.orElseEmpty("Chest").rotation().rotateX(.35F);
            pose.orElseEmpty("Arm_L").rotation().rotateX(-2.1F+stroke);
            pose.orElseEmpty("Arm_R").rotation().rotateX(-2.1F-stroke);
            pose.orElseEmpty("Hand_L").rotation().rotateX(-.7F);
            pose.orElseEmpty("Hand_R").rotation().rotateX(-.7F);
            pose.orElseEmpty("Thigh_L").rotation().rotationXYZ(-1.2F-stroke,0,.4F);
            pose.orElseEmpty("Thigh_R").rotation().rotationXYZ(-1.2F+stroke,0,-.4F);
            pose.orElseEmpty("Leg_L").rotation().rotateX(1.6F);
            pose.orElseEmpty("Leg_R").rotation().rotateX(1.6F);
            return true;
        }
        BlacklightPose sample=sample(entity,visual,"actor",partial);if(sample==null)return false;
        if(stage==ConsumeVisual.HOLD) {
            var movement=entity.onGround()?com.codex.blacklight.animation.PlayerMotionController.locomotionPose(entity,partial):pose;
            // The paired source carry clips grip with the right hand for every form.
            sample=com.codex.blacklight.animation.CarryPoseLayer.holdCreature(sample,movement,!entity.onGround(),true);
        }
        boolean hunter=victims.getFirst() instanceof com.codex.blacklight.entity.BlacklightHunter;
        if(ConsumeSequence.mountedStage(stage)&&hunter)sample.orElseEmpty("Root").translation().set(0,0,0);
        if(stage==ConsumeVisual.MOUNT_CONSUME&&!hunter) {
            sample.orElseEmpty("Root").translation().set(0,0,0);
            var form=PowerForm.values()[Math.clamp(visual.form(),0,PowerForm.values().length-1)];
            float t=visual.motionProgress(entity.level().getGameTime(),partial);
            // Keep the complete weapon torso + both-arm action. Only the supporting legs are adapted.
            var root=sample.orElseEmpty("Root").rotation();root.set(new org.joml.Quaternionf().slerp(root,.4F));
            float grip=1-Math.clamp((t-.08F)/.12F,0,1)+Math.clamp((t-.86F)/.12F,0,1);
            sample.orElseEmpty("Arm_L").rotation().slerp(new org.joml.Quaternionf().rotationXYZ(-2.1F,0,-.2F),grip);
            sample.orElseEmpty("Hand_L").rotation().slerp(new org.joml.Quaternionf().rotationXYZ(-.7F,0,0),grip);
            sample.orElseEmpty("Thigh_L").rotation().rotationXYZ(-1.1F,0,.38F);
            sample.orElseEmpty("Thigh_R").rotation().rotationXYZ(-1.1F,0,-.38F);
            sample.orElseEmpty("Leg_L").rotation().rotationXYZ(1.5F,0,0);
            sample.orElseEmpty("Leg_R").rotation().rotationXYZ(1.5F,0,0);
        }
        float enter=Math.clamp((entity.level().getGameTime()-visual.started()+partial)/4F,0,1);
        pose.getJointTransformData().putAll(BlacklightPose.interpolatePose(pose,sample,enter).getJointTransformData());return true;
    }
    private static boolean recovery(LivingEntity entity,BlacklightPose pose,float partial) {
        var visual=entity.getData(ModAttachments.CONSUME_RECOVERY);
        if(!visual.active()||!entity.isAlive()||entity.getData(ModAttachments.CONSUMING)
                ||!entity.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||entity.getData(ModAttachments.WEAPON_VISUAL).spec()!=null)return false;
        float progress=visual.phaseProgress(entity.level().getGameTime(),partial);
        if(progress>=1)return false;
        BlacklightPose sample=sample(entity,visual,"actor",partial);if(sample==null)return false;
        float remaining=visual.duration()*(1-progress),weight=Math.clamp(remaining/3F,0,1);
        boolean moving=entity.getDeltaMovement().horizontalDistance()>.018||!entity.onGround();
        if(ConsumeSequence.mountedStage(visual.stage()))sample.orElseEmpty("Root").translation().set(0,0,0);
        // When moving, keep locomotion root and legs. The absorption torso/arms
        // continue on the original scene clock, then fade into the current gait.
        BlacklightPose blended=BlacklightPose.interpolatePose(pose,sample,weight);
        if(moving) {
            for(String joint:new String[]{"Torso","Chest","Head","Shoulder_R","Arm_R","Elbow_R","Hand_R","Tool_R","Shoulder_L","Arm_L","Elbow_L","Hand_L","Tool_L"})
                pose.getJointTransformData().put(joint,blended.orElseEmpty(joint).copy());
        } else pose.getJointTransformData().putAll(blended.getJointTransformData());
        return true;
    }
    public static float facing(LivingEntity actor,float fallback,float partial){
        if(actor.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.HOLD)
            return net.minecraft.util.Mth.rotLerp(partial,actor.yRotO,actor.getYRot());
        if(actor.getData(ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME)return fallback;
        var victims=actor.level().getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(18),e->e.getData(ModAttachments.CONSUME_VISUAL).consumerId()==actor.getId());
        return victims.isEmpty()?fallback:victims.getFirst().getData(ModAttachments.CONSUME_VISUAL).facing();
    }
}
