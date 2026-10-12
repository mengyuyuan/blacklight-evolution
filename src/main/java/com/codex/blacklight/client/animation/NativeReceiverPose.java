package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import com.codex.blacklight.mixin.client.AgeableModelAccessor;
import java.util.*;

/** Receiver animation, not a generic bend added over the walk cycle. */
public final class NativeReceiverPose {
    private NativeReceiverPose() {}
    public static boolean available(LivingEntity entity,float partial){
        if(entity instanceof BlacklightHunter h&&h.isConsumeRemains())return ReceiverMotions.clip("hunter_consume")!=null;
        var v=entity.getData(ModAttachments.CONSUME_VISUAL);
        if(v.active())return entity instanceof BlacklightHunter&&ReceiverMotions.clip(v.consuming()?"hunter_consume":"hunter_grab")!=null;
        var hit=entity.getData(ModAttachments.HIT_REACTION);return hit.weight(entity.level().getGameTime(),partial)>0&&ReceiverMotions.clip(ReceiverMotions.hitKey(entity,hit.kind(),hit.motion()))!=null;
    }
    public static boolean apply(GeoModel<?> model,LivingEntity entity,float partial){
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);String key;float progress,weight;
        if(entity instanceof BlacklightHunter hunter&&hunter.isConsumeRemains()) {
            key="hunter_consume";weight=1;
            var clip=ReceiverMotions.clip(key);if(clip==null)return false;
            progress=Math.min(HunterConsume.RECEIVER_END_SECONDS,HunterConsume.COMPLETE_SECONDS+hunter.consumeRemainsAge(partial)/20F)/clip.seconds();
        }else if(visual.active()){
            if(!(entity instanceof BlacklightHunter))return false;
            key=visual.consuming()?"hunter_consume":"hunter_grab";
            progress=visual.consuming()?visual.motionProgress(entity.level().getGameTime(),partial):
                    visual.stage()==ConsumeVisual.MOUNT_HOLD?1:visual.phaseProgress(entity.level().getGameTime(),partial);
            weight=Math.clamp((entity.level().getGameTime()-visual.started()+partial)/3F,0,1);
            var clip=ReceiverMotions.clip(key);
            if(clip!=null&&visual.consuming())progress=visual.stage()==ConsumeVisual.HUNTER_CONSUME
                    ?Math.clamp(HunterConsume.seconds(visual,entity.level().getGameTime(),partial)/clip.seconds(),0,1)
                    :Math.clamp(progress*(visual.duration()-1)/(20*clip.seconds()),0,1);
        }else{
            var hit=entity.getData(ModAttachments.HIT_REACTION);weight=hit.weight(entity.level().getGameTime(),partial);if(weight<=0)return false;
            key=ReceiverMotions.hitKey(entity,hit.kind(),hit.motion());progress=hit.progress(entity.level().getGameTime(),partial);
        }
        float[] row=ReceiverMotions.sample(key,progress);if(row==null)return false;
        applyRow(model,row,weight);
        var root=model.getAnimationProcessor().getBone("root");
        if(root!=null){root.setPosX(0);root.setPosZ(0);root.setPosY(row[1]*entity.getBbHeight()*16*weight);}
        return true;
    }
    public static void locomotion(GeoModel<?> model,BlacklightHunter entity,float partial){
        String key=entity.committedAttack()?"hunter_swipe":entity.walkAnimation.speed()>.04F
                ?entity.walkAnimation.speed()>.16F?"hunter_run":"hunter_walk":"hunter_idle";
        var clip=ReceiverMotions.clip(key);if(clip==null)return;
        float progress=entity.committedAttack()?Math.clamp((24-entity.attackTicksRemaining()+partial)/24F,0,1)
                :((entity.tickCount+partial)/20F)%clip.seconds()/clip.seconds();
        float[] row=ReceiverMotions.sample(key,progress);if(row!=null)applyRow(model,row,1);
    }
    private static void applyRow(GeoModel<?> model,float[] row,float weight){
        Map<GeoBone,Quaternionf> desired=new IdentityHashMap<>();
        for(int i=0;i<ReceiverMotions.BONES.length;i++){
            GeoBone bone=model.getAnimationProcessor().getBone(ReceiverMotions.BONES[i]);
            if(bone==null){String alias=switch(ReceiverMotions.BONES[i]){case "torso"->"body";case "right_thigh"->"right_leg";case "left_thigh"->"left_leg";default->"";};if(!alias.isEmpty())bone=model.getAnimationProcessor().getBone(alias);}
            if(bone==null)continue;
            int k=3+i*4;desired.put(bone,new Quaternionf(row[k],row[k+1],row[k+2],row[k+3]).mul(bindWorld(bone)));
        }
        for(var entry:desired.entrySet()){
            GeoBone bone=entry.getKey(),parent=bone.getParent();
            Quaternionf parentWorld=parent==null?new Quaternionf():desired.getOrDefault(parent,bindWorld(parent));
            Quaternionf local=new Quaternionf(parentWorld).invert().mul(entry.getValue());
            Quaternionf old=new Quaternionf().rotationZYX(bone.getRotZ(),bone.getRotY(),bone.getRotX());
            Vector3f angles=old.slerp(local,weight).getEulerAnglesZYX(new Vector3f());bone.updateRotation(angles.x,angles.y,angles.z);
        }
    }
    public static void resetRoot(GeoModel<?> model){var root=model.getAnimationProcessor().getBone("root");if(root!=null){root.setPosX(0);root.setPosY(0);root.setPosZ(0);}}
    public static boolean vanilla(EntityModel<?> model,LivingEntity entity,float partial,Map<ModelPart,PartPose> saved){
        var hit=entity.getData(ModAttachments.HIT_REACTION);float weight=hit.weight(entity.level().getGameTime(),partial);if(weight<=0)return false;
        float[] row=ReceiverMotions.sample(ReceiverMotions.hitKey(entity,hit.kind(),hit.motion()),hit.progress(entity.level().getGameTime(),partial));if(row==null)return false;
        if(model instanceof HumanoidModel<?> h){
            // These parts are flat siblings in vanilla, whereas the source
            // receiver is hierarchical. Keep setupAnim's actual reference pose
            // (including slim pivots/crouching), then rebuild its joint positions.
            var referenceRotation=rotation(h.body);
            var referenceBody=renderPosition(h.body.x,h.body.y,h.body.z,h.young,false);
            var referenceHead=renderPosition(h.head.x,h.head.y,h.head.z,h.young,true);
            var referenceHat=renderPosition(h.hat.x,h.hat.y,h.hat.z,h.young,false);
            var referenceRightArm=renderPosition(h.rightArm.x,h.rightArm.y,h.rightArm.z,h.young,false);
            var referenceLeftArm=renderPosition(h.leftArm.x,h.leftArm.y,h.leftArm.z,h.young,false);
            var referenceRightLeg=renderPosition(h.rightLeg.x,h.rightLeg.y,h.rightLeg.z,h.young,false);
            var referenceLeftLeg=renderPosition(h.leftLeg.x,h.leftLeg.y,h.leftLeg.z,h.young,false);
            var bindBody=h.body.getInitialPose();var bindRight=h.rightLeg.getInitialPose();var bindLeft=h.leftLeg.getInitialPose();
            var waistOffset=new Vector3f((bindRight.x+bindLeft.x)*.5F-bindBody.x,
                    (bindRight.y+bindLeft.y)*.5F-bindBody.y,(bindRight.z+bindLeft.z)*.5F-bindBody.z)
                    .mul(h.body.xScale,h.body.yScale,h.body.zScale).mul(h.young?.5F:1F);
            part(h.head,3,row,weight,saved);part(h.hat,3,row,weight,saved);part(h.body,2,row,weight,saved);
            part(h.rightArm,5,row,weight,saved);part(h.leftArm,6,row,weight,saved);part(h.rightLeg,9,row,weight,saved);part(h.leftLeg,10,row,weight,saved);
            var hips=new Quaternionf().slerp(sourceRotation(row,1),weight);
            var linked=linkHumanoid(referenceBody,referenceRotation,waistOffset,rotation(h.body),hips,
                    referenceHead,referenceHat,referenceRightArm,referenceLeftArm,referenceRightLeg,referenceLeftLeg);
            position(h.body,linked.body,h.young,false);position(h.head,linked.head,h.young,true);position(h.hat,linked.hat,h.young,false);
            position(h.rightArm,linked.rightArm,h.young,false);position(h.leftArm,linked.leftArm,h.young,false);
            position(h.rightLeg,linked.rightLeg,h.young,false);position(h.leftLeg,linked.leftLeg,h.young,false);
            if(h instanceof PlayerModel<?> player){
                copyPart(player.jacket,h.body,saved);copyPart(player.rightSleeve,h.rightArm,saved);copyPart(player.leftSleeve,h.leftArm,saved);
                copyPart(player.rightPants,h.rightLeg,saved);copyPart(player.leftPants,h.leftLeg,saved);
            }return true;
        }
        if(model instanceof HierarchicalModel<?> h){
            var mapping=Map.of("head",3,"body",2,"right_arm",5,"left_arm",6,"arms",5,"right_leg",9,"left_leg",10,"right_hind_leg",9,"left_hind_leg",10);
            mapping.forEach((name,index)->{if(h.root().hasChild(name))part(h.root().getChild(name),index,row,weight*.7F,saved);});
            if(h.root().hasChild("right_front_leg"))part(h.root().getChild("right_front_leg"),5,row,weight*.6F,saved);
            if(h.root().hasChild("left_front_leg"))part(h.root().getChild("left_front_leg"),6,row,weight*.6F,saved);return true;
        }
        if(model instanceof AgeableModelAccessor a){
            for(var head:a.blacklight$heads())part(head,3,row,weight*.6F,saved);
            int[] indices={2,9,10,5,6};int i=0;for(var body:a.blacklight$body()){if(i>=indices.length)break;part(body,indices[i++],row,weight*.5F,saved);}return true;
        }
        return false;
    }
    private static void part(ModelPart p,int index,float[] row,float weight,Map<ModelPart,PartPose> saved){
        saved.putIfAbsent(p,p.storePose());var q=sourceRotation(row,index);var bind=p.getInitialPose();
        q.mul(new Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot));
        Vector3f angles=new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot).slerp(q,weight).getEulerAnglesZYX(new Vector3f());p.xRot=angles.x;p.yRot=angles.y;p.zRot=angles.z;
    }
    private static Quaternionf sourceRotation(float[] row,int index){
        int k=3+index*4;var q=new Quaternionf(row[3],row[4],row[5],row[6]).invert().mul(new Quaternionf(row[k],row[k+1],row[k+2],row[k+3]));
        return q.set(-q.x,-q.y,q.z,q.w);
    }
    private static Quaternionf rotation(ModelPart p){return new Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot);}
    private static void copyPart(ModelPart to,ModelPart from,Map<ModelPart,PartPose> saved){
        saved.putIfAbsent(to,to.storePose());to.setPos(from.x,from.y,from.z);to.xRot=from.xRot;to.yRot=from.yRot;to.zRot=from.zRot;
    }
    private static void position(ModelPart part,Vector3f rendered,boolean young,boolean head){
        var local=modelPosition(rendered,young,head);part.setPos(local.x,local.y,local.z);
    }
    // HumanoidModel's AgeableListModel render groups: only head is scaled by
    // .75; hat and the body group use .5. Both render origins are +12 pixels.
    static Vector3f renderPosition(float x,float y,float z,boolean young,boolean head){
        return young?new Vector3f(x,y+(head?16:24),z).mul(head?.75F:.5F):new Vector3f(x,y,z);
    }
    static Vector3f modelPosition(Vector3f rendered,boolean young,boolean head){
        return young?new Vector3f(rendered).div(head?.75F:.5F).sub(0,head?16:24,0):new Vector3f(rendered);
    }
    record LinkedHumanoid(Vector3f body,Vector3f head,Vector3f hat,Vector3f rightArm,Vector3f leftArm,Vector3f rightLeg,Vector3f leftLeg){}
    /** Minecraft adaptation using target reference pivots and the decoded source
     * world rotations; receiver rows do not contain full source joint positions.
     * Forward kinematics in common rendered model pixels. Root translation and
     * support placement remain with HitBodyPose; source hips/torso orientation
     * defines the relative joint chain, never detached part translations. */
    static LinkedHumanoid linkHumanoid(Vector3f referenceBody,Quaternionf referenceRotation,Vector3f waistOffset,
                                      Quaternionf bodyRotation,Quaternionf hipsRotation,Vector3f head,Vector3f hat,
                                      Vector3f rightArm,Vector3f leftArm,Vector3f rightLeg,Vector3f leftLeg){
        var waist=referenceRotation.transform(new Vector3f(waistOffset)).add(referenceBody);
        var bodyWorld=new Matrix4f().translation(waist).rotate(bodyRotation).translate(-waistOffset.x,-waistOffset.y,-waistOffset.z);
        var referenceInverse=new Matrix4f().translation(referenceBody).rotate(referenceRotation).invert();
        var upperDelta=new Matrix4f(bodyWorld).mul(referenceInverse);
        var hipsDelta=new Matrix4f().translation(waist).rotate(hipsRotation).translate(-waist.x,-waist.y,-waist.z);
        return new LinkedHumanoid(bodyWorld.transformPosition(new Vector3f()),upperDelta.transformPosition(new Vector3f(head)),
                upperDelta.transformPosition(new Vector3f(hat)),upperDelta.transformPosition(new Vector3f(rightArm)),
                upperDelta.transformPosition(new Vector3f(leftArm)),hipsDelta.transformPosition(new Vector3f(rightLeg)),
                hipsDelta.transformPosition(new Vector3f(leftLeg)));
    }
    private static Quaternionf bindWorld(GeoBone bone){var bind=bone.getInitialSnapshot();var q=new Quaternionf().rotationZYX(bind.getRotZ(),bind.getRotY(),bind.getRotX());return bone.getParent()==null?q:bindWorld(bone.getParent()).mul(q);}
}
