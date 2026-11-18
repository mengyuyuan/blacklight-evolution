package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import net.minecraft.client.model.*;
import net.minecraft.world.entity.LivingEntity;

/** Project-authored latch, pull and cockpit entry paired with the real server claim clock. */
public final class HelicopterPose {
    private record Motion(float bodyX,float bodyZ,float armX,float armY,float rightLeg,float leftLeg){}
    private static Motion motion(BlackwatchHelicopter craft,LivingEntity actor,float partial){
        var hijack=craft.pilot().hijack();int slot=craft.pilot().slot(actor.getUUID());float seated=-(float)Math.PI/2;
        if(hijack.actor(actor.getUUID())){
            float t=hijack.ticks()+partial,reach=Math.min(1,t/18F),enter=Math.max(0,Math.min(1,(t-54)/22F));
            return new Motion(.28F*(1-enter),-hijack.side()*.18F*(1-enter),(-1.15F-reach*.9F)*(1-enter)-1.05F*enter,.35F*(1-enter),-.8F*(1-enter)+seated*enter,.25F*(1-enter)+seated*enter);
        }
        return new Motion(hijack.active()&&slot==(hijack.side()<0?0:1)?.45F*hijack.pull():0,0,slot<2?-1.05F:-.35F,0,seated,seated);
    }
    public static void apply(HumanoidModel<?> model,BlackwatchHelicopter craft,LivingEntity actor,float partial){
        var hijack=craft.pilot().hijack();int slot=craft.pilot().slot(actor.getUUID());
        VehicleSeatPose.apply(model,slot<2,net.minecraft.util.Mth.wrapDegrees(actor.getViewYRot(partial)+craft.getYRot()),actor.getViewXRot(partial));
        var motion=motion(craft,actor,partial);model.body.xRot=motion.bodyX;model.body.zRot=motion.bodyZ;
        model.rightArm.xRot=model.leftArm.xRot=motion.armX;model.rightArm.yRot=-motion.armY;model.leftArm.yRot=motion.armY;
        model.rightLeg.xRot=motion.rightLeg;model.leftLeg.xRot=motion.leftLeg;
        if(!hijack.actor(actor.getUUID())&&hijack.active()&&slot==(hijack.side()<0?0:1)){
            float pull=hijack.pull();model.body.xRot=.45F*pull;model.rightArm.xRot=model.leftArm.xRot=-1.3F;model.head.xRot=-.3F*pull;
        }
        model.hat.copyFrom(model.head);
        if(model instanceof PlayerModel<?> player){player.jacket.copyFrom(model.body);player.rightSleeve.copyFrom(model.rightArm);player.leftSleeve.copyFrom(model.leftArm);player.rightPants.copyFrom(model.rightLeg);player.leftPants.copyFrom(model.leftLeg);}
    }
    /** The prototype skin/armour route consumes a skeleton, so it shares the same claim-clock pose explicitly. */
    public static com.codex.blacklight.animation.BlacklightPose skeleton(BlackwatchHelicopter craft,LivingEntity actor,float partial){
        var motion=motion(craft,actor,partial);var skeleton=com.codex.blacklight.animation.BlacklightSkeleton.get();
        var pose=new com.codex.blacklight.animation.BlacklightPose();var bind=skeleton.getPoseMatrices(pose);var target=new org.joml.Matrix4f[bind.length];
        var hip=bind[skeleton.torso.getId()].getTranslation(new org.joml.Vector3f());
        var body=new org.joml.Matrix4f().translation(hip).rotateZ(motion.bodyZ).rotateX(-motion.bodyX).translate(-hip.x,-hip.y,-hip.z);
        float yaw=(float)Math.toRadians(net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(actor.getViewYRot(partial)+craft.getYRot()),-75,75));
        float pitch=(float)Math.toRadians(net.minecraft.util.Mth.clamp(actor.getViewXRot(partial),-45,45));
        for(var bone:skeleton.bones()){
            var parent=bone.parent();var base=parent==null?bone.getLocalTransform():new org.joml.Matrix4f(target[parent.getId()]).mul(bone.getLocalTransform());
            var at=bind[bone.getId()].getTranslation(new org.joml.Vector3f());var change=new org.joml.Matrix4f().translation(at);boolean upper=false;
            switch(bone.getName()){
                case "Torso" -> {target[bone.getId()]=new org.joml.Matrix4f(body).mul(bind[bone.getId()]);pose.getJointTransformData().put(bone.getName(),com.codex.blacklight.animation.BoneTransform.fromMatrix(new org.joml.Matrix4f(base).invert().mul(target[bone.getId()])));continue;}
                case "Head" -> {change.rotateY(-yaw).rotateX(-pitch);upper=true;}
                case "Arm_R" -> {change.rotateY(motion.armY).rotateX(-motion.armX);upper=true;}
                case "Arm_L" -> {change.rotateY(-motion.armY).rotateX(-motion.armX);upper=true;}
                case "Thigh_R" -> change.translate(0,1/16F,0).rotateX(-motion.rightLeg);
                case "Thigh_L" -> change.translate(0,1/16F,0).rotateX(-motion.leftLeg);
                default -> {target[bone.getId()]=new org.joml.Matrix4f(base);continue;}
            }
            change.translate(-at.x,-at.y,-at.z);if(upper)change=new org.joml.Matrix4f(body).mul(change);
            target[bone.getId()]=change.mul(bind[bone.getId()]);pose.getJointTransformData().put(bone.getName(),com.codex.blacklight.animation.BoneTransform.fromMatrix(new org.joml.Matrix4f(base).invert().mul(target[bone.getId()])));
        }
        return pose;
    }
    private HelicopterPose(){}
}
