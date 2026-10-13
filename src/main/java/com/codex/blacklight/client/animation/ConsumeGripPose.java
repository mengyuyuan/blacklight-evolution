package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BoneTransform;

/** Keep the two palms on the rendered head when species proportions or terrain differ. */
public final class ConsumeGripPose {
    private ConsumeGripPose() {}
    public static void apply(LivingEntity actor, com.codex.blacklight.animation.BlacklightSkeleton armature, BlacklightPose pose, float partial) {
        if(actor.getData(ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME)return;
        var victims=actor.level().getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(5),v->{
            var scene=v.getData(ModAttachments.CONSUME_VISUAL);
            return scene.consumerId()==actor.getId()&&scene.stage()==ConsumeVisual.CONSUME&&scene.form()==PowerForm.CLAWS.ordinal();
        });
        if(victims.isEmpty())return;
        var victim=victims.getFirst();var visual=victim.getData(ModAttachments.CONSUME_VISUAL);
        float time=visual.motionProgress(actor.level().getGameTime(),partial),end=ConsumePlacementTrack.gripEnd(PowerForm.CLAWS);
        float weight=Math.clamp((actor.level().getGameTime()-visual.started()+partial)/4F,0,1)*Math.clamp((end-time)/.06F,0,1);
        if(weight<=0)return;
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),left=new Vec3(forward.z,0,-forward.x);
        boolean quad=ConsumeSequence.quadruped(victim);
        Vec3 head=ConsumeVictimPose.point(victim,new Vec3(0,victim.getBbHeight()*(quad?.65:.86),quad?-victim.getBbWidth()*.45:0),partial);
        Vec3 target=victim.getPosition(partial).add(com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(victim,partial))
                .add(head).subtract(actor.getPosition(partial));
        if(target.lengthSqr()>9)return;
        BlacklightPose original=BlacklightPose.interpolatePose(pose,pose,0);
        for(String side:new String[]{"R","L"}){
            var arm=armature.searchJointByName("Arm_"+side);var wrist=armature.searchJointByName("Tool_"+side);
            if(arm==null||wrist==null)continue;
            Vec3 contact=target.add(left.scale((side.equals("L")?1:-1)*Math.clamp(victim.getBbWidth()*.22,.09,.22)));
            org.joml.Vector3f desired=new org.joml.Vector3f((float)(-contact.dot(left)/.9375),(float)(contact.y/.9375),(float)(-contact.dot(forward)/.9375));
            var palm=new org.joml.Vector3f(side.equals("L")?.00267F:-.00267F,.32362F,.07666F);
            palm=WeaponModelAnchors.get(PowerForm.CLAWS,"palm_"+side,palm);
            var endpoint=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(palm);
            BlacklightBoneIk.reach(armature,pose,side,desired,endpoint,12);
        }
        var blended=BlacklightPose.interpolatePose(original,pose,weight);
        pose.getJointTransformData().putAll(blended.getJointTransformData());
    }
}
