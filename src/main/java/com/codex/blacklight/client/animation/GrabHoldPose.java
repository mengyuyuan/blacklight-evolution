package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.GrabPlacement;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.animation.BlacklightPose;

/** Move the shoulder and elbow together so the rendered palm stays on the held body. */
public final class GrabHoldPose {
    private GrabHoldPose() {}
    public static void apply(LivingEntity player,com.codex.blacklight.animation.BlacklightSkeleton armature,BlacklightPose pose,float partial) {
        int stage=player.getData(ModAttachments.GRAB_STAGE);
        if(stage==ConsumeVisual.NONE||stage==ConsumeVisual.CONSUME||com.codex.blacklight.combat.ConsumeSequence.mountedStage(stage))return;
        boolean blade=player.getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.BLADE;
        var held=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(4),e->{
            var visual=e.getData(ModAttachments.CONSUME_VISUAL);
            return visual.consumerId()==player.getId()&&(visual.stage()==ConsumeVisual.HOLD || blade &&
                    (visual.stage()==ConsumeVisual.CONSUME || visual.stage()==ConsumeVisual.THROW || visual.stage()==ConsumeVisual.CAPTURE &&
                     visual.phaseProgress(player.level().getGameTime(),partial)>=.6F));
        });
        if(held.isEmpty())return;
        var mob=held.getFirst();
        float yaw=Mth.rotLerp(partial,player.yRotO,player.getYRot());
        float pitch=Mth.lerp(partial,player.xRotO,player.getXRot());
        var forward=Vec3.directionFromRotation(0,yaw);
        var left=new Vec3(forward.z,0,-forward.x);
        var ownerPos=player.getPosition(partial);var mobPos=mob.getPosition(partial);
        double gripHeight=Math.clamp(GrabPlacement.palm(ownerPos,yaw,pitch).y-mobPos.y,
                mob.getBbHeight()*.15,mob.getBbHeight()*.85);
        var target=mobPos.add(0,gripHeight,0).subtract(forward.scale(mob.getBbWidth()*.4)).subtract(ownerPos);
        if(blade) {
            var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
            float progress=visual.consuming()?visual.progress(player.level().getGameTime(),partial):0;
            double height=Math.clamp(GrabPlacement.bladeContact(ownerPos,yaw,pitch,progress).y-mobPos.y,mob.getBbHeight()*.15,mob.getBbHeight()*.85);
            target=mobPos.add(ConsumeVictimPose.point(mob,new Vec3(0,height,0),partial)).subtract(ownerPos);
        }
        var desired=new org.joml.Vector3f((float)(-target.dot(left)/.9375),(float)(target.y/.9375),(float)(-target.dot(forward)/.9375));
        String side=blade?"R":"L",tool="Tool_"+side;
        boolean claws=player.getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.CLAWS;
        var palmLocal=blade?new org.joml.Vector3f(-.0636396F,.68F,-.0636396F):claws?new org.joml.Vector3f(.00267F,.32362F,.07666F):new org.joml.Vector3f(0,.28F,0);
        palmLocal=WeaponModelAnchors.get(player.getData(ModAttachments.PLAYER_STATE).getActivePower(),blade?"bladeGrip_R":"palm_L",palmLocal);
        var wrist=armature.searchJointByName(tool);
        if(wrist==null)return;
        var endpoint=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(palmLocal);
        BlacklightBoneIk.reach(armature,pose,side,desired,endpoint,16);
    }
}
