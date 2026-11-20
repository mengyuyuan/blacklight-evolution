package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BoneTransform;

/** Fit contacts per consume scene; a sweep uses its cutting edge, a head crush uses both palms. */
public final class ConsumeGripPose {
    private ConsumeGripPose() {}
    public static void apply(LivingEntity actor, com.codex.blacklight.animation.BlacklightSkeleton armature, BlacklightPose pose, float partial) {
        if(com.codex.blacklight.registry.ModAttachments.getData(actor, ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME)return;
        var victims=actor.level().getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(5),v->{
            var scene=com.codex.blacklight.registry.ModAttachments.getData(v, ModAttachments.CONSUME_VISUAL);
            return scene.consumerId()==actor.getId()&&scene.stage()==ConsumeVisual.CONSUME&&(scene.form()==PowerForm.CLAWS.ordinal()||scene.form()==PowerForm.BLADE.ordinal()
                    ||fittedStrike(scene));
        });
        if(victims.isEmpty())return;
        var victim=victims.get(0);var visual=com.codex.blacklight.registry.ModAttachments.getData(victim, ModAttachments.CONSUME_VISUAL);
        applyForScene(actor,victim,armature,pose,visual,partial);
    }
    static void applyForScene(LivingEntity actor,LivingEntity victim,com.codex.blacklight.animation.BlacklightSkeleton armature,
                              BlacklightPose pose,ConsumeVisual visual,float partial) {
        // A detached preview has no physical receiver to fit. Keep its authored pose.
        if(actor==null||victim==null)return;
        if(fittedStrike(visual)){
            if(visual.form()==PowerForm.NONE.ordinal() && visual.variant()==ConsumeVariant.BEATDOWN)
                BeatdownMountPose.apply(actor,victim,visual,armature,pose,partial);
            fitStrike(actor,victim,armature,pose,visual,partial);
            return;
        }
        if(visual.form()==PowerForm.BLADE.ordinal()){
            BladeConsumePose.apply(actor,victim,armature,pose,visual,partial);return;
        }
        if(!ConsumePlacementTrack.headGrip(PowerForm.CLAWS,visual.variant())){
            sweep(actor,victim,armature,pose,visual,partial);return;
        }
        float time=visual.motionProgress(actor.level().getGameTime(),partial),end=ConsumePlacementTrack.gripEnd(PowerForm.CLAWS);
        float weight=PhaseEntryBlend.CONSUME_GRIP.weight(actor,visual.started(),visual.stage(),partial,4F)*net.minecraft.util.Mth.clamp((end-time)/.06F,0,1);
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
            Vec3 contact=target.add(left.scale((side.equals("L")?1:-1)*net.minecraft.util.Mth.clamp(victim.getBbWidth()*.22,.09,.22)));
            org.joml.Vector3f desired=new org.joml.Vector3f((float)(-contact.dot(left)/.9375),(float)(contact.y/.9375),(float)(-contact.dot(forward)/.9375));
            var palm=new org.joml.Vector3f(side.equals("L")?.00267F:-.00267F,.32362F,.07666F);
            palm=WeaponModelAnchors.get(PowerForm.CLAWS,"palm_"+side,palm);
            var endpoint=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(palm);
            BlacklightBoneIk.reach(armature,pose,side,desired,endpoint,12);
        }
        var blended=BlacklightPose.interpolatePose(original,pose,weight);
        pose.getJointTransformData().putAll(blended.getJointTransformData());
    }
    private static boolean fittedStrike(ConsumeVisual scene){
        return scene.form()==PowerForm.MUSCLE_MASS.ordinal() || scene.form()==PowerForm.NONE.ordinal() && switch(scene.variant()){
            case ConsumeVariant.DEFAULT,ConsumeVariant.HEAD_STOMP,ConsumeVariant.KNEE_HEAD,ConsumeVariant.BEATDOWN -> true;
            default -> false;
        };
    }
    /** Fit the striking limb to the posed anatomy of this receiver. Outside each impact window the
     * wind-up and recoil come entirely from the original paired clip. */
    private static void fitStrike(LivingEntity actor,LivingEntity victim,
                                        com.codex.blacklight.animation.BlacklightSkeleton rig,
                                        BlacklightPose pose,ConsumeVisual visual,float partial){
        boolean muscle=visual.form()==PowerForm.MUSCLE_MASS.ordinal();
        boolean rip=muscle && visual.variant()==ConsumeVariant.DEFAULT;
        boolean heart=muscle && visual.variant()==ConsumeVariant.CLASSIC;
        var scene=ConsumeReceiverScene.profile(muscle?PowerForm.MUSCLE_MASS:PowerForm.NONE,false,visual.variant());
        if(scene==null)return;
        float seconds=visual.elapsed(actor.level().getGameTime(),partial)/20F;
        if(rip){
            float split=scene.deformSeconds();
            float hold=RagdollDrive.smooth((seconds-(split-.48F))/.22F)
                    *(1-RagdollDrive.smooth((seconds-split)/.08F));
            if(hold>0){
                for(String side:new String[]{"R","L"}){
                    float lateral=side.equals("R")?.22F:-.22F;
                    Vec3 receiver=com.codex.blacklight.client.renderer.AdaptiveRagdoll.villagerGrip(victim,lateral,partial);
                    if(receiver==null)receiver=RagdollDrive.contact(victim,side.equals("R")?4:2,partial);
                    fitPalm(actor,rig,pose,visual,partial,side,receiver,hold);
                }
            }
            return;
        }
        for(int i=0;i<scene.hitSeconds().size();i++){
            float delta=seconds-scene.hitSeconds().get(i);
            float weight=RagdollDrive.smooth(1-Math.abs(delta)/(delta<0?.29F:.23F));
            if(heart && i==1 && delta>=0)weight=1-RagdollDrive.smooth((seconds-1F)/.24F);
            if(!muscle && visual.variant()==ConsumeVariant.DEFAULT)
                weight=Math.max(weight,RagdollDrive.smooth(1-Math.abs(seconds-46F/30F)/.19F));
            if(weight<=0)continue;
            boolean stomp=visual.variant()==ConsumeVariant.HEAD_STOMP;
            int region=heart?(i==0?32:1):rip?2:visual.variant()==ConsumeVariant.DEFAULT?32:1;
            Vec3 contact=RagdollDrive.contact(victim,region,partial);
            if(region==32){
                Vec3 chest=com.codex.blacklight.client.renderer.AdaptiveRagdoll.villagerGrip(victim,0,partial);
                if(chest!=null)contact=chest;
            }
            if(heart){fitPalm(actor,rig,pose,visual,partial,"L",contact,weight);return;}
            if(stomp)contact=contact.add(0,Math.min(.24,victim.getBbWidth()*.34),0);
            Vec3 relative=contact.subtract(actor.getPosition(partial));
            if(!Double.isFinite(relative.lengthSqr())||relative.lengthSqr()>16)return;
            Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),left=new Vec3(forward.z,0,-forward.x);
            float scale=.9375F;
            var goal=new org.joml.Vector3f((float)(-relative.dot(left)/scale),(float)(relative.y/scale),(float)(-relative.dot(forward)/scale));
            var fitted=pose.copy();
            if(stomp){
                var sole=rig.getBoundTransformFor(new BlacklightPose(),rig.legL).invert()
                        .transformPosition(new org.joml.Vector3f(-.119F,.001F,0));
                BlacklightBoneIk.reachLimb(rig,fitted,rig.thighL,rig.legL,rig.legL,goal,sole);
            }else if(visual.variant()==ConsumeVariant.KNEE_HEAD){
                BlacklightBoneIk.aimJoint(rig,fitted,rig.thighR,rig.legR,goal);
            }else{
                boolean right=visual.variant()==ConsumeVariant.BEATDOWN?ConsumeReceiverScene.beatdownRightHand(i):(i&1)==1;
                String side=rip||!heart&&right?"R":"L";
                BlacklightBoneIk.reachTwoBone(rig,fitted,side,goal,new org.joml.Vector3f());
            }
            pose.getJointTransformData().putAll(BlacklightPose.interpolatePose(pose,fitted,weight).getJointTransformData());
            return;
        }
    }
    private static void fitPalm(LivingEntity actor,com.codex.blacklight.animation.BlacklightSkeleton rig,
                                BlacklightPose pose,ConsumeVisual visual,float partial,String side,Vec3 point,float weight){
        Vec3 relative=point.subtract(actor.getPosition(partial));
        if(!Double.isFinite(relative.lengthSqr())||relative.lengthSqr()>16)return;
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),left=new Vec3(forward.z,0,-forward.x);
        float scale=.9375F*actor.getScale();
        var goal=new org.joml.Vector3f((float)(-relative.dot(left)/scale),(float)(relative.y/scale),(float)(-relative.dot(forward)/scale));
        var fitted=pose.copy();var arm=rig.searchJointByName("Arm_"+side);var hand=rig.searchJointByName("Hand_"+side);
        var palm=WeaponModelAnchors.get(PowerForm.MUSCLE_MASS,"palm_"+side,new org.joml.Vector3f(0,.66F,0));
        BlacklightBoneIk.reachLimb(rig,fitted,arm,hand,hand,goal,palm);
        pose.getJointTransformData().putAll(BlacklightPose.interpolatePose(pose,fitted,weight).getJointTransformData());
    }
    private static void sweep(LivingEntity actor,LivingEntity victim,com.codex.blacklight.animation.BlacklightSkeleton rig,
                              BlacklightPose pose,ConsumeVisual visual,float partial){
        var scene=ConsumeReceiverScene.profile(PowerForm.CLAWS,false,visual.variant());
        if(scene==null||!scene.style().equals("SEVER"))return;
        float seconds=visual.elapsed(actor.level().getGameTime(),partial)/20F;
        // The right palm holds the receiver only at entry. By 0.4 s the authored
        // wrist has left the grip and is sweeping; never pin it to the head then.
        float hold=1-RagdollDrive.smooth((seconds-.20F)/.18F);
        float cut=scene.deformSeconds();
        float slash=RagdollDrive.smooth((seconds-(cut-.20F))/.15F)
                *(1-RagdollDrive.smooth((seconds-(cut+.025F))/.15F));
        float entry=PhaseEntryBlend.CONSUME_GRIP.weight(actor,visual.started(),visual.stage(),partial,2F);
        float weight=Math.max(hold,slash)*entry;if(weight<=.001F)return;
        boolean cutting=slash>hold;
        Vec3 contact=RagdollDrive.contact(victim,cutting?32:1,partial).subtract(actor.getPosition(partial));
        if(!Double.isFinite(contact.lengthSqr())||contact.lengthSqr()>16)return;
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),left=new Vec3(forward.z,0,-forward.x);
        float scale=.9375F*actor.getScale();
        var desired=new org.joml.Vector3f((float)(-contact.dot(left)/scale),(float)(contact.y/scale),(float)(-contact.dot(forward)/scale));
        var wrist=rig.searchJointByName("Tool_R");if(wrist==null)return;
        var point=WeaponModelAnchors.get(PowerForm.CLAWS,"palm_R",new org.joml.Vector3f(-.0086872F,.5761816F,.0163958F));
        if(cutting){
            var root=WeaponModelAnchors.get(PowerForm.CLAWS,"trailRoot_R",point);
            var tip=WeaponModelAnchors.get(PowerForm.CLAWS,"trailTip_R",point);
            point=root.lerp(tip,.65F);
        }
        var endpoint=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(point);
        var original=pose.copy();BlacklightBoneIk.reachTwoBone(rig,pose,"R",desired,endpoint);
        var best=pose.copy();float error=rig.getBoundTransformFor(best,wrist).transformPosition(new org.joml.Vector3f(endpoint)).distanceSquared(desired);
        if(cutting&&error>.0025F){
            // Small receivers can sit inside the minimum reach of the long claw.
            // Use a reachable point along its actual blade instead of stretching the arm
            // or pinning the receiver to the fixed 65% landmark.
            var base=WeaponModelAnchors.get(PowerForm.CLAWS,"trailRoot_R",point);
            var tip=WeaponModelAnchors.get(PowerForm.CLAWS,"trailTip_R",point);
            for(float fraction:new float[]{.4F,.2F,0F,.85F,1F}){
                var local=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(new org.joml.Vector3f(base).lerp(tip,fraction));
                var trial=original.copy();BlacklightBoneIk.reachTwoBone(rig,trial,"R",desired,local);
                float distance=rig.getBoundTransformFor(trial,wrist).transformPosition(new org.joml.Vector3f(local)).distanceSquared(desired);
                if(distance<error){error=distance;best=trial;}
                if(error<.000025F)break;
            }
        }
        var fitted=BlacklightPose.interpolatePose(original,best,weight);
        // Preserve the left claw, foot sweep, root travel and recovery choreography.
        for(String name:new String[]{"Arm_R","Hand_R"})pose.getJointTransformData().put(name,fitted.orElseEmpty(name).copy());
    }
}
