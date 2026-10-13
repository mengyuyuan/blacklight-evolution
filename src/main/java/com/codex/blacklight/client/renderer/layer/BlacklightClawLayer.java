package com.codex.blacklight.client.renderer.layer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.model.ClawArmModel;
import com.codex.blacklight.client.renderer.WeaponMorphTracker;
import com.codex.blacklight.client.renderer.LiquidBiomassMaterial;
import com.codex.blacklight.client.animation.BiomassSurface;
import com.codex.blacklight.client.animation.WeaponMorphTiming;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.client.tuning.ClawTransformSettings;
import com.codex.blacklight.client.tuning.ClawTransformSettings.HandSide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Renders directly on Blacklight's animated shoulder and elbow joints; no held-item transform is involved. */
public final class BlacklightClawLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    public BlacklightClawLayer(net.minecraft.client.renderer.entity.RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){
        super(parent);
        com.codex.blacklight.client.renderer.AuthoredDevastatorMesh.available();
        // Parse authored surfaces during renderer setup, rather than hitching the
        // first visible liquid frame each time a weapon is selected.
        for(var form:PowerForm.values())if(WeaponMorphTiming.weapon(form))
            forms.put(form,new ClawArmModel(form==PowerForm.CLAWS?"claw_blender":"form_"+form.id()));
    }
    @Override public void render(PoseStack stack,MultiBufferSource buffer,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!(getParentModel() instanceof com.codex.blacklight.client.model.BlacklightPlayerModel parent))return;
        var poses=parent.matrices();if(poses==null)return;
        stack.pushPose();
        try {
            com.codex.blacklight.client.renderer.BlacklightBoneRendering.skeletonFrame(stack);
            renderSkeleton(player,stack,buffer,light,poses,age,yaw,pitch,partial);
        }finally{stack.popPose();}
    }
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BlacklightNeoMod.MOD_ID, "textures/entity/claw_white.png");
    // Load at renderer setup, after resources are available. These meshes do not
    // depend on LayerDefinition registration order.
    private final java.util.Map<com.codex.blacklight.power.PowerForm, ClawArmModel> forms = new java.util.EnumMap<>(com.codex.blacklight.power.PowerForm.class);
    private record Coil(float intensity,float lastCharged){}
    private final java.util.Map<AbstractClientPlayer,Coil> coils=new java.util.WeakHashMap<>();

    /** Camera-space arms share the exact third-person skeleton and morph clock. */
    public void renderFirstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial) {
        renderFirstPerson(player,stack,buffer,light,poses,partial,-1);
    }
    public void renderFirstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial,int side) {
        renderParts(player,stack,buffer,light,poses,partial,side);
    }

    private void renderSkeleton(AbstractClientPlayer player,
                               PoseStack poseStack,
                               MultiBufferSource buffer,
                               int packedLight,
                               org.joml.Matrix4f[] poses,
                               float bob,
                               float yRot,
                               float xRot,
                               float partialTicks) {
        var state = player.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
        var form = state.getActivePower();
        if(player.getData(com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL).kind()>=0){
            for(int side=0;side<2;side++){
                var bone=com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName(side==0?"Tool_R":"Tool_L");
                poseStack.pushPose();applyJoint(poseStack,poses[bone.getId()]);
                com.codex.blacklight.client.renderer.DevastatorEffectRenderer.captureHand(player,poseStack,side==1);poseStack.popPose();
            }
        }
        if(state.isPrototypeMode()) {
            int charge=player==Minecraft.getInstance().player?com.codex.blacklight.client.ClientMovementEvents.visualJumpCharge():player.getData(com.codex.blacklight.registry.ModAttachments.JUMP_CHARGE);
            float now=player.tickCount+partialTicks;
            if(charge>0)coils.put(player,new Coil(Math.clamp(charge/(float)com.codex.blacklight.movement.MovementTuningSettings.jumpChargeTicks(),0,1),now));
            var coil=coils.get(player);
            float intensity=coil==null?0:coil.intensity()*Math.clamp(1-(now-coil.lastCharged())/6,0,1);
            if(intensity>.001F){
                var out=buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
                for(String joint:new String[]{"Thigh_R","Leg_R","Thigh_L","Leg_L"}) {
                    var bone=com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName(joint);
                    poseStack.pushPose();applyJoint(poseStack,poses[bone.getId()]);
                    BiomassTendrils.charge(poseStack,out,packedLight,intensity,.43F,player.tickCount+partialTicks);
                    poseStack.popPose();
                }
            }else coils.remove(player);
        }
        if(state.isPrototypeMode()&&form==com.codex.blacklight.power.PowerForm.NONE){
            for(int side=0;side<2;side++){
                var bone=com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName(side==0?"Hand_R":"Hand_L");
                poseStack.pushPose();applyJoint(poseStack,poses[bone.getId()]);
                com.codex.blacklight.client.renderer.WeaponTrailRenderer.capture(player,poseStack,side==1);poseStack.popPose();
            }
        }
        var attack=player.getData(com.codex.blacklight.registry.ModAttachments.WEAPON_VISUAL).spec();
        if(state.isPrototypeMode()&&attack!=null&&(attack.id.contains("kick")||attack.id.contains("knee")||attack.id.contains("spin")||attack==com.codex.blacklight.combat.WeaponMove.COMBO_AXE)){
            for(int side=0;side<2;side++){
                var bone=com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName(side==0?"Leg_R":"Leg_L");
                poseStack.pushPose();applyJoint(poseStack,poses[bone.getId()]);
                com.codex.blacklight.client.renderer.WeaponTrailRenderer.captureLocal(player,poseStack,side+2,.23F,.56F);poseStack.popPose();
            }
        }
        renderParts(player,poseStack,buffer,packedLight,poses,partialTicks,-1);
    }

    private void renderParts(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,
                             org.joml.Matrix4f[] poses,float partial,int side) {
        var morph=WeaponMorphTracker.sample(player,partial);
        var parts=morph.parts();
        if(ClawTransformSettings.preview()&&(morph.target()==PowerForm.NONE||morph.target()==PowerForm.CLAWS))
            parts=java.util.List.of(new WeaponMorphTracker.Part(PowerForm.CLAWS,BiomassSurface.Frame.stable(1)));
        var hammer=com.codex.blacklight.client.renderer.HammerScaleRenderer.sample(player,partial);
        // All wave frequencies repeat after 50,000 ticks. Reduce before casting
        // to float so old worlds retain sub-tick liquid motion.
        float materialTime=(float)(morph.materialTime()%50_000D);
        for(var part:parts) {
            if(part.surface().growth()<=.001F)continue;
            var form=part.form();var mesh=forms.computeIfAbsent(form,
                    f->new ClawArmModel(f==PowerForm.CLAWS?"claw_blender":"form_"+f.id()));
            if(side!=1)renderHand(player,InteractionHand.MAIN_HAND,poses,false,stack,buffer,light,mesh,form,part.surface(),partial,materialTime,
                    form==PowerForm.HAMMERFISTS?hammer[0]:null);
            if(side!=0&&WeaponMorphTiming.arm(form,true))renderHand(player,InteractionHand.OFF_HAND,poses,true,stack,buffer,light,mesh,form,part.surface(),partial,materialTime,
                    form==PowerForm.HAMMERFISTS?hammer[1]:null);
        }
    }

    private void renderHand(AbstractClientPlayer player,
                            InteractionHand hand,
                            org.joml.Matrix4f[] poses,
                            boolean mirror,
                            PoseStack poseStack,
                            MultiBufferSource buffer,
                            int packedLight,
                            ClawArmModel model,PowerForm form,BiomassSurface.Frame surface,float partial,float time,
                            com.codex.blacklight.combat.HammerScales.Shape hammer) {
        float morphProgress=surface.growth();
        if (morphProgress <= 0.001F) return;
        com.codex.blacklight.animation.BlacklightSkeleton armature = com.codex.blacklight.animation.PlayerMotionController.skeleton();
        var handJoint = hand == InteractionHand.MAIN_HAND ? armature.handR : armature.handL;
        var armJoint = hand == InteractionHand.MAIN_HAND ? armature.armR : armature.armL;
        HandSide side = mirror ? HandSide.LEFT : HandSide.RIGHT;
        boolean claw = form==PowerForm.CLAWS;
        boolean authoritative=form==player.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE).getActivePower();
        float width = claw ? (float) ClawTransformSettings.limbWidth(side) : 1;

        // Arm_R/L is the shoulder pivot; Hand_R/L is the actual elbow pivot.
        // Pose matrices already contain the complete animated hierarchy. Applying an
        // inverse bind-hand offset and arbitrary tuner translations detached the limb.
        // Keep both segment origins on their real joints and overlap their end rings.
        RenderType material=surface.liquid()?LiquidBiomassMaterial.mesh(TEXTURE):RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer consumer = buffer.getBuffer(material);
        poseStack.pushPose();
        applyJoint(poseStack, poses[armJoint.getId()]);
        poseStack.scale(width, 1.0F, width);
        model.renderJoint(mirror ? "Arm_L" : "Arm_R", poseStack, consumer, packedLight,
                OverlayTexture.NO_OVERLAY, morphProgress, 1.0F,null,Float.POSITIVE_INFINITY,null,surface,time);
        LiquidBiomassRenderer.render(poseStack,buffer,packedLight,surface,.34F,time,false);
        poseStack.popPose();

        poseStack.pushPose();
        applyJoint(poseStack, poses[handJoint.getId()]);
        poseStack.scale(width, 1.0F, width);
        // A different RenderType may finish the previous non-fixed buffer.
        consumer=buffer.getBuffer(material);
        var visual=player.getData(com.codex.blacklight.registry.ModAttachments.WEAPON_VISUAL);
        if(authoritative&&!mirror&&visual.charging()&&visual.spec()!=null&&visual.spec().form==com.codex.blacklight.power.PowerForm.BLADE){
            float age=player.level().getGameTime()-visual.started();
            BiomassTendrils.charge(poseStack,consumer,packedLight,Math.clamp(.35F+age/18F,0,1),.68F,player.tickCount);
        }
        org.joml.Matrix4f wristDelta=null;
        boolean traversal=com.codex.blacklight.movement.WallPoseState.get(player)!=null||!player.onGround();
        boolean paired=player.getData(com.codex.blacklight.registry.ModAttachments.GRAB_STAGE)
                !=com.codex.blacklight.combat.ConsumeVisual.NONE
                ||player.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_RECOVERY).active();
        if(claw&&(traversal||paired)){
            var wrist=armature.searchJointByName(mirror?"Tool_L":"Tool_R");
            var relative=new org.joml.Matrix4f(poses[handJoint.getId()]).invert().mul(poses[wrist.getId()]);
            wristDelta=new org.joml.Matrix4f(relative).mul(new org.joml.Matrix4f(wrist.getLocalTransform()).invert());
        }
        boolean extendingWhip=authoritative&&!mirror&&com.codex.blacklight.client.renderer.WhipTetherRenderer.extended(player);
        model.renderJoint(mirror ? "Hand_L" : "Hand_R", poseStack, consumer, packedLight,
                 OverlayTexture.NO_OVERLAY, morphProgress, claw ? (float) ClawTransformSettings.talonLength(side) : 1,wristDelta,extendingWhip?.32F:Float.POSITIVE_INFINITY,hammer,surface,time);
        if(!mirror&&!extendingWhip&&form==PowerForm.WHIPFIST)
            model.renderJoint("Tip_R",poseStack,consumer,packedLight,OverlayTexture.NO_OVERLAY,morphProgress,1,null,Float.POSITIVE_INFINITY,null,surface,time);
        if(authoritative&&mirror && claw && player.getData(com.codex.blacklight.registry.ModAttachments.GRAB_STAGE)
                ==com.codex.blacklight.combat.ConsumeVisual.HOLD)
            com.codex.blacklight.client.renderer.WhipTetherRenderer.captureGrip(player,poseStack);
        if(authoritative&&!mirror && form==PowerForm.BLADE)
            com.codex.blacklight.client.renderer.WhipTetherRenderer.captureBlade(player,poseStack);
        if (authoritative&&!mirror && form==PowerForm.WHIPFIST)
            com.codex.blacklight.client.renderer.WhipTetherRenderer.capture(player, poseStack);
        if(authoritative)com.codex.blacklight.client.renderer.WeaponTrailRenderer.capture(player,poseStack,mirror);
        LiquidBiomassRenderer.render(poseStack,buffer,packedLight,surface,.44F,time,true);
        poseStack.popPose();
    }

    private static void applyJoint(PoseStack stack, org.joml.Matrix4f joint) {
        com.codex.blacklight.client.renderer.BlacklightBoneRendering.apply(stack,joint);
    }
}
