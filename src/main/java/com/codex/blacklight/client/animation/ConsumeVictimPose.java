package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.ConsumeReaction;
import com.codex.blacklight.combat.GrappleMotion;
import com.codex.blacklight.mixin.client.AgeableModelAccessor;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.world.entity.LivingEntity;
import com.codex.blacklight.animation.BlacklightPose;
import java.util.*;

/** Same authored reaction drives vanilla joints, Blacklight bones, and the Hunter's Gecko rig. */
public final class ConsumeVictimPose {
    private static final Map<EntityModel<?>,Map<ModelPart,PartPose>> SAVED=new WeakHashMap<>();
    private ConsumeVictimPose() {}
    public static ConsumeReaction.Pose reaction(LivingEntity entity,float partial) {
        var data=entity.getData(ModAttachments.CONSUME_VISUAL);
        if(com.codex.blacklight.combat.ConsumeSequence.mountedStage(data.stage())) {
            var form=PowerForm.values()[Math.clamp(data.form(),0,PowerForm.values().length-1)];
            float t=data.motionProgress(entity.level().getGameTime(),partial);
            float hit=data.consuming()?(float)Math.exp(-Math.pow((t-com.codex.blacklight.combat.ConsumeSequence.contact(form,true))*18,2)):0;
            float twist=switch(form){case BLADE->.8F;case WHIPFIST->-.85F;case MUSCLE_MASS->-.45F;case CLAWS->.45F;default->.15F;};
            float bend=form==PowerForm.HAMMERFISTS?.85F:form==PowerForm.MUSCLE_MASS?-.55F:.35F;
            return new ConsumeReaction.Pose(.1F+hit*bend,hit*twist,Math.max(0,t-.7F)*.4F,.6F,hit*.45F,-hit*(form==PowerForm.CLAWS?.95F:.5F),0);
        }
        if(data.stage()==com.codex.blacklight.combat.ConsumeVisual.WHIP_PULL)
            return new ConsumeReaction.Pose(.1F,0,0,.35F,0,-.1F,0);
        if(!data.consuming()) {
            float time=entity.level().getGameTime()+partial;
            float strength=data.stage()==com.codex.blacklight.combat.ConsumeVisual.CAPTURE
                    ? Math.clamp((data.phaseProgress(entity.level().getGameTime(),partial)-.35F)*2,0,1):1;
            return new ConsumeReaction.Pose(.12F*strength,(float)Math.sin(time*.17F)*.13F*strength,0,
                    .8F*strength,(.5F+(float)Math.sin(time*.32F)*.18F)*strength,-.2F*strength,0);
        }
        var forms=PowerForm.values();
        return ConsumeReaction.sample(forms[Math.clamp(data.form(),0,forms.length-1)],data.motionProgress(entity.level().getGameTime(),partial));
    }
    public static org.joml.Matrix4f bodyTransform(LivingEntity entity,float partial) {
        var p=reaction(entity,partial);float h=entity.getBbHeight(),g=p.grounded();
        var matrix=new org.joml.Matrix4f();
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);
        if(entity instanceof com.codex.blacklight.entity.BlacklightHunter&&NativeReceiverPose.available(entity,partial))return matrix;
        if(com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage()))
            return matrix.rotateY((float)Math.toRadians(-entity.yBodyRot)).translate(0,h*.65F,0)
                    .rotateX(p.bend()*.16F).rotateZ(p.twist()*.20F).translate(0,-h*.65F,0).rotateY((float)Math.toRadians(entity.yBodyRot));
        var nativePose=NativeConsumePose.sample(entity,visual,"victim",partial);
        if(nativePose!=null && com.codex.blacklight.combat.ConsumeSequence.nativeReceiver(entity)) {
            if(entity instanceof net.minecraft.client.player.AbstractClientPlayer) return matrix;
            var form=PowerForm.values()[Math.clamp(visual.form(),0,PowerForm.values().length-1)];
            var projected=com.codex.blacklight.combat.ConsumePlacementTrack.projection(GrappleMotion.sceneKey(entity,visual),GrappleMotion.receiverProgress(entity,visual,partial));
            if(projected.isPresent()){
                double[] frame=projected.get();float scale=com.codex.blacklight.combat.ConsumeSequence.humanoid(entity)?com.codex.blacklight.combat.ConsumeSequence.receiverScale(entity):Math.clamp(h/1.8F,.3F,1.5F);
                float yaw=(float)Math.toRadians(180-GrappleMotion.facing(entity,visual,partial));
                if(com.codex.blacklight.combat.ConsumePlacementTrack.rigidReceiver(entity))return matrix.rotateY(yaw)
                        .mul(com.codex.blacklight.combat.ConsumePlacementTrack.rigidBody(entity,GrappleMotion.sceneKey(entity,visual),GrappleMotion.receiverProgress(entity,visual,partial))).rotateY(-yaw);
                return matrix.rotateY(yaw).translate((float)frame[0]*scale,(float)frame[1]*scale,(float)frame[2]*scale)
                        .translate(0,.9F*scale,0).rotate(com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,3)).translate(0,-.9F*scale,0).rotateY(-yaw);
            }
        }
        if(visual.stage()==com.codex.blacklight.combat.ConsumeVisual.FLIGHT) {
            float t=visual.phaseProgress(entity.level().getGameTime(),partial);
            float pitch=Math.min(1,t*8)*1.25F;
            return matrix.rotateY((float)Math.toRadians(-entity.yBodyRot)).translate(0,h*.5F,0)
                    .rotateX(pitch).rotateZ((float)Math.sin(t*12)*.18F).translate(0,-h*.5F,0)
                    .rotateY((float)Math.toRadians(entity.yBodyRot));
        }
        if(visual.form()==PowerForm.BLADE.ordinal() && visual.active()) {
            // The puncture is the pivot. Recoil and hanging limbs cannot slide the torso off the blade.
            return matrix.rotateY((float)Math.toRadians(-entity.yBodyRot)).translate(0,h*.6F,0)
                    .rotateX(p.bend()*.30F).rotateZ(p.twist()*.24F).translate(0,-h*.6F,0)
                    .rotateY((float)Math.toRadians(entity.yBodyRot));
        }
        float half=h*.5F,groundHeight=Math.clamp(entity.getBbWidth()*.35F,.18F,.36F);
        matrix.translate(0,-p.slump()*h*.24F,0).rotateY((float)Math.toRadians(-entity.yBodyRot));
        // Center the entire victim on the ground as it rotates, instead of tipping a standing model around its feet.
        matrix.translate(0,half*(1-g)+groundHeight*g,0).rotateX(-g*(float)Math.PI/2+p.bend()*.22F)
                .rotateZ(p.twist()*.18F).translate(0,-half,0).rotateY((float)Math.toRadians(entity.yBodyRot));
        return matrix;
    }
    public static void body(LivingEntity entity,PoseStack stack,float partial) { stack.mulPose(bodyTransform(entity,partial)); }
    /** Effects follow the posed torso even when Blacklight already applies the pose to its mesh. */
    public static org.joml.Matrix4f effectTransform(LivingEntity entity,float partial) {
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);
        if(!(entity instanceof net.minecraft.client.player.AbstractClientPlayer)||!com.codex.blacklight.combat.ConsumeSequence.humanoid(entity)||(!visual.consuming()&&!GrappleMotion.active(visual))||com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage()))return bodyTransform(entity,partial);
        var form=PowerForm.values()[Math.clamp(visual.form(),0,PowerForm.values().length-1)];
        float t=GrappleMotion.receiverProgress(entity,visual,partial);
        String key=GrappleMotion.sceneKey(entity,visual);
        var frame=com.codex.blacklight.combat.ConsumePlacementTrack.projection(key,t).orElse(null);
        var contact=com.codex.blacklight.combat.ConsumePlacementTrack.contactFrame(key,t).orElse(null);
        if(frame==null||contact==null)return bodyTransform(entity,partial);
        float scale=com.codex.blacklight.combat.ConsumeSequence.receiverScale(entity),yaw=(float)Math.toRadians(180-GrappleMotion.facing(entity,visual,partial));
        var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,GrappleMotion.facing(entity,visual,partial));
        var right=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);
        var center=right.scale(contact[10]*scale).add(forward.scale(contact[12]*scale)).add(0,contact[11]*scale,0);
        var chest=com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,11);
        chest.set(-chest.x,-chest.y,chest.z,chest.w);
        return new org.joml.Matrix4f().translate((float)center.x,(float)center.y,(float)center.z).rotateY(yaw)
                .rotate(com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,3)).rotate(chest)
                .translate(0,-entity.getBbHeight()*.5F,0).rotateY(-yaw);
    }
    public static net.minecraft.world.phys.Vec3 effectPoint(LivingEntity entity,net.minecraft.world.phys.Vec3 point,float partial) {
        var v=effectTransform(entity,partial).transformPosition(new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z));
        return new net.minecraft.world.phys.Vec3(v.x,v.y,v.z);
    }
    public static net.minecraft.world.phys.Vec3 point(LivingEntity entity,net.minecraft.world.phys.Vec3 point,float partial) {
        var v=bodyTransform(entity,partial).transformPosition(new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z));
        return new net.minecraft.world.phys.Vec3(v.x,v.y,v.z);
    }
    public static void vanilla(EntityModel<?> model,LivingEntity entity,float partial) {
        restore(model);
        if (!entity.getData(ModAttachments.CONSUME_VISUAL).active()) { hit(model,entity,partial); return; }
        var p=reaction(entity,partial);
        var saved=new IdentityHashMap<ModelPart,PartPose>();SAVED.put(model,saved);
        restrain(model,saved);
        if(applyNative(model,saved,entity,partial)) {
            return;
        }
        if (model instanceof HumanoidModel<?> humanoid) {
            head(saved,humanoid.head,p);head(saved,humanoid.hat,p);
            rotate(saved,humanoid.body,p.bend()*.22F,p.twist()*.45F,0);
            limb(saved,humanoid.rightArm,-p.brace()*1.8F,.15F,-.25F-p.brace()*.22F);
            limb(saved,humanoid.leftArm,-p.brace()*1.65F,-.15F,.25F+p.brace()*.22F);
            limb(saved,humanoid.rightLeg,p.kick()*.66F,0,.12F);
            limb(saved,humanoid.leftLeg,-p.kick()*.48F,0,-.12F);
        } else if (model instanceof HierarchicalModel<?> hierarchy) {
            var root=hierarchy.root();
            if(root.hasChild("head"))head(saved,root.getChild("head"),p);
            if(root.hasChild("body"))rotate(saved,root.getChild("body"),p.bend()*.18F,p.twist()*.35F,0);
            if(root.hasChild("arms"))rotate(saved,root.getChild("arms"),-p.brace()*.75F,0,p.twist()*.4F);
            String[] legs={"right_leg","left_leg","right_front_leg","left_front_leg","right_hind_leg","left_hind_leg"};
            for(int i=0;i<legs.length;i++)if(root.hasChild(legs[i]))limb(saved,root.getChild(legs[i]),(i%2==0?1:-1)*p.kick()*.7F,0,(i%2==0?1:-1)*.15F);
        } else if (model instanceof AgeableModelAccessor ageable) {
            for(var head:ageable.blacklight$heads())head(saved,head,p);
            int index=0;
            for(var part:ageable.blacklight$body()) {
                if(index==0)rotate(saved,part,p.bend()*.13F,p.twist()*.3F,p.twist()*.18F);
                else {
                    float side=index%2==0?1:-1;
                    // Quadrupeds tuck the front legs and kick the rear legs while restrained.
                    limb(saved,part,(index<3?-.6F:1.05F)*p.kick()+side*p.twist()*.35F,0,side*p.brace()*.17F);
                }
                index++;
            }
        }
    }
    private static boolean applyNative(EntityModel<?> model,Map<ModelPart,PartPose> saved,LivingEntity entity,float partial) {
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);
        if(!visual.consuming()&&!GrappleMotion.active(visual))return false;
        boolean large=com.codex.blacklight.combat.ConsumeSequence.mountedStage(visual.stage());
        var projection=com.codex.blacklight.combat.ConsumePlacementTrack.projection(GrappleMotion.sceneKey(entity,visual),GrappleMotion.receiverProgress(entity,visual,partial));
        if(projection.isEmpty())return false;
        var frame=projection.get();
        var contact=large?null:com.codex.blacklight.combat.ConsumePlacementTrack.contactFrame(GrappleMotion.sceneKey(entity,visual),GrappleMotion.receiverProgress(entity,visual,partial)).orElse(null);
        if(model instanceof HumanoidModel<?> h) {
            projectHumanoid(h,saved,frame,contact,large);
            if(!large&&visual.form()==PowerForm.WHIPFIST.ordinal()
                    &&visual.variant()==com.codex.blacklight.combat.ConsumeVariant.CLASSIC) {
                var scene=com.codex.blacklight.combat.ConsumeReceiverScene.profile(PowerForm.WHIPFIST,false,visual.variant());
                float seconds=(entity.level().getGameTime()-visual.started()+partial)/20F;
                float hanging=Math.clamp((seconds-.08F)/.20F,0,1)
                        *(1-Math.clamp((seconds-scene.effectSeconds())/.4F,0,1));
                dangleHumanoid(h,saved,seconds,hanging);
            }
            ReceiverPoseDiagnostics.record(entity,model,GrappleMotion.sceneKey(entity,visual),large?5:7);return true;
        }
        int bound=ReceiverModelPose.apply(model,saved,frame,
                com.codex.blacklight.combat.ConsumePlacementTrack.rigidReceiver(entity),large);
        ReceiverPoseDiagnostics.record(entity, model, GrappleMotion.sceneKey(entity,visual), bound);
        return bound>0;
    }
    static void projectHumanoid(HumanoidModel<?> h,Map<ModelPart,PartPose> saved,double[] frame,double[] contact,boolean large) {
        var body=renderPosition(h.body,h.young,false);var head=renderPosition(h.head,h.young,true);
        var hat=renderPosition(h.hat,h.young,false);var armR=renderPosition(h.rightArm,h.young,false);var armL=renderPosition(h.leftArm,h.young,false);
        var legR=renderPosition(h.rightLeg,h.young,false);var legL=renderPosition(h.leftLeg,h.young,false);
        var referenceRotation=rotation(h.body);
        var bindBody=h.body.getInitialPose();var bindR=h.rightLeg.getInitialPose();var bindL=h.leftLeg.getInitialPose();
        var waist=new org.joml.Vector3f((bindR.x+bindL.x)*.5F-bindBody.x,(bindR.y+bindL.y)*.5F-bindBody.y,(bindR.z+bindL.z)*.5F-bindBody.z)
                .mul(h.body.xScale,h.body.yScale,h.body.zScale).mul(h.young?.5F:1);
        projectedPart(saved,h.head,frame,null,0);projectedPart(saved,h.hat,frame,null,0);
        projectedPart(saved,h.body,frame,null,1);projectedPart(saved,h.rightArm,frame,null,2);projectedPart(saved,h.leftArm,frame,null,3);
        if(!large){projectedPart(saved,h.rightLeg,frame,null,4);projectedPart(saved,h.leftLeg,frame,null,5);}
        // Source contacts describe a hierarchical, bent receiver. Vanilla's
        // cuboid limbs are flat siblings with different neck/shoulder sockets.
        // Retain the authored rotations and pelvis travel, then reconstruct
        // positions from this model's actual sockets instead of moving each
        // body part independently (which tears the neck and waist apart).
        var hips=large?new org.joml.Quaternionf():com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,31);
        var linked=NativeReceiverPose.linkHumanoid(body,referenceRotation,waist,rotation(h.body),hips,head,hat,armR,armL,legR,legL);
        var shift=new org.joml.Vector3f();
        if(contact!=null&&!large)shift.set((float)(contact[25]+contact[28]),(float)(contact[26]+contact[29]),(float)(contact[27]+contact[30])).mul(h.young?4:8);
        position(saved,h.body,linked.body().add(shift),h.young,false);position(saved,h.head,linked.head().add(shift),h.young,true);
        position(saved,h.hat,linked.hat().add(shift),h.young,false);position(saved,h.rightArm,linked.rightArm().add(shift),h.young,false);
        position(saved,h.leftArm,linked.leftArm().add(shift),h.young,false);position(saved,h.rightLeg,linked.rightLeg().add(shift),h.young,false);
        position(saved,h.leftLeg,linked.leftLeg().add(shift),h.young,false);
        if(h instanceof PlayerModel<?> player) {
            copyPart(saved,player.jacket,h.body);copyPart(saved,player.rightSleeve,h.rightArm);copyPart(saved,player.leftSleeve,h.leftArm);
            copyPart(saved,player.rightPants,h.rightLeg);copyPart(saved,player.leftPants,h.leftLeg);
        }
        // Segmented receivers have actual elbow/knee children. Clearing their
        // locomotion without applying these joints left the captured body rigid.
        projectSegment(saved,h.rightArm,"forearm",frame,7);
        projectSegment(saved,h.leftArm,"forearm",frame,8);
        if(!large){projectSegment(saved,h.rightLeg,"shin",frame,9);projectSegment(saved,h.leftLeg,"shin",frame,10);}
    }
    private static void projectSegment(Map<ModelPart,PartPose> saved,ModelPart parent,String childName,double[] frame,int lower) {
        if(!parent.hasChild(childName))return;
        var child=parent.getChild(childName);saved.putIfAbsent(child,child.storePose());
        var bind=child.getInitialPose();var parentBind=parent.getInitialPose();
        var desired=com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,7+lower*4)
                .mul(new org.joml.Quaternionf().rotationZYX(parentBind.zRot,parentBind.yRot,parentBind.xRot))
                .mul(new org.joml.Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot));
        var angles=rotation(parent).invert().mul(desired).getEulerAnglesZYX(new org.joml.Vector3f());
        child.xRot=angles.x;child.yRot=angles.y;child.zRot=angles.z;
    }
    /** Minecraft suspended-body follow-through for the old lash's near-straight
     * receiver interval. Contact/root placement and the authored torso stay intact. */
    static void dangleHumanoid(HumanoidModel<?> h,Map<ModelPart,PartPose> saved,float seconds,float weight) {
        if(weight<=0)return;
        float lag=(float)Math.sin(seconds*4.1F),other=(float)Math.sin(seconds*4.1F+1.8F);
        rotate(saved,h.rightLeg,(-.55F+lag*.12F)*weight,-.05F*weight,.10F*weight);
        rotate(saved,h.leftLeg,(-.22F+other*.13F)*weight,.05F*weight,-.10F*weight);
        for(int i=0;i<2;i++) {
            var leg=i==0?h.rightLeg:h.leftLeg;
            if(leg.hasChild("shin"))rotate(saved,leg.getChild("shin"),((i==0?.95F:.72F)+(i==0?lag:other)*.18F)*weight,0,0);
            var arm=i==0?h.rightArm:h.leftArm;
            if(arm.hasChild("forearm"))rotate(saved,arm.getChild("forearm"),-(.25F+(i==0?other:lag)*.10F)*weight,0,0);
        }
        if(h instanceof PlayerModel<?> player) {
            copyPart(saved,player.rightPants,h.rightLeg);copyPart(saved,player.leftPants,h.leftLeg);
        }
    }
    private static org.joml.Vector3f renderPosition(ModelPart part,boolean young,boolean head){return NativeReceiverPose.renderPosition(part.x,part.y,part.z,young,head);}
    private static org.joml.Quaternionf rotation(ModelPart part){return new org.joml.Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot);}
    private static void position(Map<ModelPart,PartPose> saved,ModelPart part,org.joml.Vector3f rendered,boolean young,boolean head){
        saved.putIfAbsent(part,part.storePose());var local=NativeReceiverPose.modelPosition(rendered,young,head);part.setPos(local.x,local.y,local.z);
    }
    private static void copyPart(Map<ModelPart,PartPose> saved,ModelPart to,ModelPart from){saved.putIfAbsent(to,to.storePose());to.copyFrom(from);}
    private static void restrain(EntityModel<?> model,Map<ModelPart,PartPose> saved){
        var parts=new java.util.HashSet<ModelPart>();
        var root=ReceiverModelPose.root(model);
        if(root!=null)root.getAllParts().forEach(parts::add);
        else if(model instanceof AgeableModelAccessor a){
            for(var p:a.blacklight$heads())p.getAllParts().forEach(parts::add);
            for(var p:a.blacklight$body())p.getAllParts().forEach(parts::add);
        }
        for(var p:parts){saved.putIfAbsent(p,p.storePose());p.loadPose(p.getInitialPose());}
    }
    private static void projectedPart(Map<ModelPart,PartPose> saved,ModelPart part,double[] frame,double[] contact,int joint) {
        projectedPart(saved,part,frame,contact,joint,1,1);
    }
    private static void projectedPart(Map<ModelPart,PartPose> saved,ModelPart part,double[] frame,double[] contact,int joint,float scale,float weight) {
        saved.putIfAbsent(part,part.storePose());var bind=part.getInitialPose();
        var q=new org.joml.Quaternionf().slerp(com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(frame,7+joint*4),weight)
                .mul(new org.joml.Quaternionf().rotationZYX(bind.zRot,bind.yRot,bind.xRot));
        var angles=q.getEulerAnglesZYX(new org.joml.Vector3f());
        part.xRot=angles.x;part.yRot=angles.y;part.zRot=angles.z;
        if(contact!=null){int n=13+joint*3;part.x=bind.x+(float)contact[n]*16*scale;part.y=bind.y+(float)contact[n+1]*16*scale;part.z=bind.z+(float)contact[n+2]*16*scale;}
    }
    private static void head(Map<ModelPart,PartPose> saved,ModelPart part,ConsumeReaction.Pose p) {
        rotate(saved,part,p.head(),p.twist(),p.twist()*.55F);
    }
    private static void rotate(Map<ModelPart,PartPose> saved,ModelPart part,float x,float y,float z) {
        saved.putIfAbsent(part,part.storePose());part.xRot+=x;part.yRot+=y;part.zRot+=z;
    }
    private static void limb(Map<ModelPart,PartPose> saved,ModelPart part,float x,float y,float z) {
        saved.putIfAbsent(part,part.storePose());part.xRot=x;part.yRot=y;part.zRot=z;
    }
    public static void restore(EntityModel<?> model) {
        var saved=SAVED.remove(model);if(saved!=null)saved.forEach(ModelPart::loadPose);
    }
    public static void epic(LivingEntity entity,BlacklightPose pose,float partial) {
        if(!entity.getData(ModAttachments.CONSUME_VISUAL).active()) {
            var hit=entity.getData(ModAttachments.HIT_REACTION);float w=hit.weight(entity.level().getGameTime(),partial);
            if(w>0){
                float bend=(hit.kind()==com.codex.blacklight.combat.HitReaction.SLAM?.65F:-.22F)*w;
                pose.orElseEmpty("Torso").rotation().rotateX(bend*.55F);
                pose.orElseEmpty("Chest").rotation().rotateX(bend);
                pose.orElseEmpty("Head").rotation().rotateX(-bend*.55F);
                pose.orElseEmpty("Arm_R").rotation().rotateZ(-.30F*w);
                pose.orElseEmpty("Arm_L").rotation().rotateZ(.30F*w);
                pose.orElseEmpty("Leg_R").rotation().rotateX(.25F*w);
                pose.orElseEmpty("Leg_L").rotation().rotateX(.18F*w);
            }
            return;
        }
        var source=NativeConsumePose.sample(entity,entity.getData(ModAttachments.CONSUME_VISUAL),"victim",partial);
        if(source!=null && (com.codex.blacklight.combat.ConsumeSequence.humanoid(entity)||com.codex.blacklight.combat.ConsumeSequence.mountedStage(entity.getData(ModAttachments.CONSUME_VISUAL).stage()))) {
            if(com.codex.blacklight.combat.ConsumeSequence.mountedStage(entity.getData(ModAttachments.CONSUME_VISUAL).stage()))source.orElseEmpty("Root").translation().set(0,0,0);
            pose.getJointTransformData().putAll(source.getJointTransformData());return;
        }
        var p=reaction(entity,partial);
        // Override the captured locomotion rather than adding perpetual walking or attack poses.
        pose.orElseEmpty("Torso").rotation().rotationXYZ(p.bend()*.55F,p.twist()*.4F,0);
        pose.orElseEmpty("Chest").rotation().rotationXYZ(p.bend()*.45F,p.twist()*.6F,0);
        pose.orElseEmpty("Head").rotation().rotationXYZ(-p.head()*.6F,-p.twist(),p.twist()*.5F);
        for(String side:new String[]{"R","L"}) {
            float sign=side.equals("R")?1:-1;
            pose.orElseEmpty("Arm_"+side).rotation().rotationXYZ(-p.brace()*1.25F,sign*.18F,sign*(.18F+p.brace()*.25F));
            pose.orElseEmpty("Hand_"+side).rotation().rotationXYZ(-.3F-p.brace()*.85F,0,0);
            pose.orElseEmpty("Thigh_"+side).rotation().rotationXYZ(-p.kick()*.55F+sign*p.twist()*.2F,0,sign*.12F);
            pose.orElseEmpty("Leg_"+side).rotation().rotationXYZ(p.kick()*.95F,0,0);
        }
    }
    private static void hit(EntityModel<?> model,LivingEntity entity,float partial) {
        var hit=entity.getData(ModAttachments.HIT_REACTION);float w=hit.weight(entity.level().getGameTime(),partial);
        if(w<=0)return;
        float bend=(hit.kind()==com.codex.blacklight.combat.HitReaction.SLAM?.60F:-.24F)*w;
        float twist=(float)Math.sin(Math.toRadians(hit.yaw()-entity.yBodyRot))*.28F*w;
        var saved=new IdentityHashMap<ModelPart,PartPose>();SAVED.put(model,saved);
        if(NativeReceiverPose.vanilla(model,entity,partial,saved))return;
        if(model instanceof HumanoidModel<?> h){
            rotate(saved,h.body,bend,twist,twist*.5F);rotate(saved,h.head,-bend*.6F,0,0);rotate(saved,h.hat,-bend*.6F,0,0);
            rotate(saved,h.rightArm,-.45F*w,0,-.22F*w);rotate(saved,h.leftArm,-.35F*w,0,.22F*w);
            rotate(saved,h.rightLeg,.18F*w,0,0);rotate(saved,h.leftLeg,-.14F*w,0,0);
        }else if(model instanceof HierarchicalModel<?> h){
            var root=h.root();
            if(root.hasChild("body"))rotate(saved,root.getChild("body"),bend,twist,0);
            if(root.hasChild("head"))rotate(saved,root.getChild("head"),-bend*.6F,0,0);
        }else if(model instanceof AgeableModelAccessor a){
            for(var head:a.blacklight$heads())rotate(saved,head,-bend*.6F,twist,0);
            int i=0;for(var part:a.blacklight$body())rotate(saved,part,i++==0?bend*.45F:(i%2==0?.30F:-.22F)*w,0,twist*.5F);
        }
    }
}
