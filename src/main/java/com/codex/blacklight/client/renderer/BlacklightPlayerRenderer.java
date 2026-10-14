package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.renderer.layer.BlacklightClawLayer;
import com.codex.blacklight.client.renderer.layer.UtilityPowerLayer;
import com.codex.blacklight.client.PrototypeAnimationController;
import com.codex.blacklight.client.animation.NativeConsumePose;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** Standalone player rendering retains vanilla equipment layers and uses Blacklight's own articulated skin. */
public final class BlacklightPlayerRenderer extends PlayerRenderer {
    public record RenderedView(com.codex.blacklight.client.animation.FirstPersonPose.Result result,double gameTime,String route) {
        public RenderedView(com.codex.blacklight.client.animation.FirstPersonPose.Result result,double gameTime){this(result,gameTime,"native arm");}
    }
    private static final java.util.Map<AbstractClientPlayer,RenderedView> VIEWS=new java.util.WeakHashMap<>();
    public static RenderedView renderedView(AbstractClientPlayer player){return VIEWS.get(player);}
    private final BlacklightClawLayer biological;
    private final UtilityPowerLayer utility;
    private final net.minecraft.client.model.PlayerModel<AbstractClientPlayer> handModel;
    private final boolean slimHands;
    private com.codex.blacklight.client.model.BlockShieldModel handShield;
    public BlacklightPlayerRenderer(EntityRendererProvider.Context context,boolean slim) {
        super(context,slim);
        slimHands=slim;handModel=new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        this.model=new BlacklightPlayerModel(context.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        biological=new BlacklightClawLayer(this);utility=new UtilityPowerLayer(this);
        addLayer(biological);addLayer(utility);
    }
    public void renderPoweredArm(AbstractClientPlayer player,PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,int light,float partial,boolean left) {
        // A plain vanilla model is isolated from the third-person animated skin.
        handModel.attackTime=0;handModel.crouching=false;handModel.swimAmount=0;
        handModel.setupAnim(player,0,0,0,0,0);
        var arm=left?handModel.leftArm:handModel.rightArm;arm.xRot=0;
        stack.pushPose();
        try {
            arm.translateAndRotate(stack);
            stack.translate((left?1:-1)*(slimHands?.5F:1F)/16F,-2F/16F,0);
            var morph=WeaponMorphTracker.sample(player,partial);
            var form=morph.projectionForm(left);
            boolean plated=com.codex.blacklight.power.UtilityPowerSystem.visualWeight(player,com.codex.blacklight.power.PowerForm.ARMOR,partial)>0;
            boolean shielded=com.codex.blacklight.power.UtilityPowerSystem.visualWeight(player,com.codex.blacklight.power.PowerForm.SHIELD,partial)>0;
            var pose=new com.codex.blacklight.animation.BlacklightPose();
            var weapon=com.codex.blacklight.animation.WeaponMotionClock.sample(com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL),player.level().getGameTime()+partial);
            String preview=com.codex.blacklight.animation.PlayerMotionController.previewKey(player);
            if(preview!=null&&!preview.startsWith("basic_idle"))pose=com.codex.blacklight.animation.MotionLibrary.pose(preview,com.codex.blacklight.animation.PlayerMotionController.elapsed(player,partial),false);
            else if(weapon!=null)pose=com.codex.blacklight.animation.BlacklightPose.interpolatePose(pose,
                    com.codex.blacklight.animation.MotionLibrary.pose(weapon.key(),weapon.seconds(),weapon.looping()),weapon.blend());
            else {
                var action=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_ACTION);
                if(action.active(player.level().getGameTime()+partial)&&(!action.clipId().startsWith("basic_")||action.clipId().equals("basic_jump_charge"))
                        ||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.GRAB_STAGE)!=com.codex.blacklight.combat.ConsumeVisual.NONE)
                    pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(player,partial);
            }
            var matrices=com.codex.blacklight.animation.PlayerMotionController.skeleton().getPoseMatrices(pose);
            var frame=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
            var side=left?com.codex.blacklight.client.tuning.ClawTransformSettings.HandSide.LEFT:com.codex.blacklight.client.tuning.ClawTransformSettings.HandSide.RIGHT;
            var hammer=HammerScaleRenderer.sample(player,partial)[left?1:0];
            var view=com.codex.blacklight.client.animation.FirstPersonPose.convertArm(matrices,form,plated,false,
                    com.codex.blacklight.client.animation.NativeShieldGrip.matrix(player,partial),
                    (float)com.codex.blacklight.client.tuning.ClawTransformSettings.limbWidth(side),
                    (float)com.codex.blacklight.client.tuning.ClawTransformSettings.talonLength(side),
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),frame,Math.max(1,Math.max(hammer.fist(),hammer.forearm())),left,
                    morph.parts().stream().map(WeaponMorphTracker.Part::form).toList(),.045F*morph.flow());
            VIEWS.put(player,new RenderedView(view,player.level().getGameTime()+partial));
            biological.renderFirstPerson(player,stack,buffers,light,view.matrices(),partial,left?1:0);
            utility.renderFirstPerson(player,stack,buffers,light,view.matrices(),partial,left?1:0);
        }finally{stack.popPose();}
    }
    public void renderPoweredShield(AbstractClientPlayer player,PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,int light,float partial) {
        if(handShield==null)handShield=new com.codex.blacklight.client.model.BlockShieldModel();
        stack.pushPose();
        try {
            com.codex.blacklight.client.ClientPoseMatrices.multiply(stack,handShield.itemFrame());
            var frame=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
            var view=handShield.projectedItem(frame,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            VIEWS.put(player,new RenderedView(view,player.level().getGameTime()+partial,"native shield item"));
            handShield.render(player,stack,buffers,light,partial);
        }finally{stack.popPose();}
    }
    @Override protected void scale(AbstractClientPlayer player,PoseStack stack,float partial) {
        super.scale(player,stack,partial);
        float armorScale=com.codex.blacklight.client.model.BlockArmorModel.get().bodyScale(player,partial);
        stack.scale(armorScale,armorScale,armorScale);
    }
    @Override protected void setupRotations(AbstractClientPlayer player,PoseStack stack,float bob,float bodyYaw,float partial) {
        float facing=com.codex.blacklight.client.animation.BlacklightRenderPose.facing(player,partial);
        super.setupRotations(player,stack,bob,facing,partial);
    }
}
