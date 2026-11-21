package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.renderer.layer.BlacklightClawLayer;
import com.codex.blacklight.client.renderer.layer.UtilityPowerLayer;
import com.codex.blacklight.client.PrototypeAnimationController;
import com.codex.blacklight.client.animation.NativeConsumePose;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** Standalone player rendering retains vanilla equipment layers and uses Blacklight's own articulated skin. */
public final class BlacklightPlayerRenderer extends PlayerRenderer {
    private final BlacklightPlayerModel nativeBody;
    /** Dedicated native renderer retains its own rig across optional selectors. */
    public void restoreNativeModel() { if(nativeBody!=null)this.model=nativeBody; }
    @Override public void render(AbstractClientPlayer player,float yaw,float partial,PoseStack stack,
                                 net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        try(var scope=NativePlayerRenderScope.enter(player)) {
            super.render(player,yaw,partial,stack,buffers,light);
        }
    }
    public record RenderedView(com.codex.blacklight.client.animation.FirstPersonPose.Result result,double gameTime,String route) {
        public RenderedView(com.codex.blacklight.client.animation.FirstPersonPose.Result result,double gameTime){this(result,gameTime,"native arm");}
    }
    private static final java.util.Map<AbstractClientPlayer,RenderedView> VIEWS=new java.util.WeakHashMap<>();
    public static RenderedView renderedView(AbstractClientPlayer player){return VIEWS.get(player);}
    private final BlacklightClawLayer biological;
    private final UtilityPowerLayer utility;
    private final com.codex.blacklight.client.renderer.layer.SovereignWingLayer wings;
    private final net.minecraft.client.model.PlayerModel<AbstractClientPlayer> handModel;
    private final boolean slimHands;
    private com.codex.blacklight.client.model.BlockShieldModel handShield;
    public BlacklightPlayerRenderer(EntityRendererProvider.Context context,boolean slim) {
        super(context,slim);
        slimHands=slim;handModel=new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        this.model=nativeBody=new BlacklightPlayerModel(context.bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
        // Keep vanilla equipment effects, but deform its cuboids with our body.
        layers.removeIf(layer->layer instanceof net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<?,?,?>);
        var body=(BlacklightPlayerModel)this.model;
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new com.codex.blacklight.client.model.BlacklightArmorModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR),body),
                new com.codex.blacklight.client.model.BlacklightArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR),body),context.getModelManager()));
        biological=new BlacklightClawLayer(this);utility=new UtilityPowerLayer(this);
        wings=new com.codex.blacklight.client.renderer.layer.SovereignWingLayer(this);
        addLayer(biological);addLayer(utility);addLayer(wings);
    }
    /** YSM owns the body pass; only Blacklight's weapon, shield and wing attachments are drawn here. */
    public void renderYsmAttachments(AbstractClientPlayer player,float partial,PoseStack stack,
            net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        stack.pushPose();
        try {
            float facing=com.codex.blacklight.client.animation.BlacklightRenderPose.facing(player,partial);
            stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-facing));
            // YSM decodes Bedrock pivots with an X reflection. Match that basis
            // for attachments as well as the live pose channels.
            stack.scale(-1,1,1);
            stack.scale(-1,-1,1); stack.scale(.9375F*1.5F,.9375F*1.5F,.9375F*1.5F);
            stack.translate(0,-1.501F,0);
            model.prepareMobModel(player,0,0,partial);
            model.setupAnim(player,0,0,player.tickCount+partial,0,0);
            biological.render(stack,buffers,light,player,0,0,partial,player.tickCount+partial,0,0);
            utility.render(stack,buffers,light,player,0,0,partial,player.tickCount+partial,0,0);
            wings.render(stack,buffers,light,player,0,0,partial,player.tickCount+partial,0,0);
        } finally { stack.popPose(); }
    }
    public void renderPoweredArm(AbstractClientPlayer player,PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,int light,float partial,boolean left,FirstPersonConsumeTendrils.Scene weave,boolean probeOnly) {
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
            var weapon=com.codex.blacklight.animation.WeaponMotionClock.sample(com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.WEAPON_VISUAL),player.level().getGameTime()+partial);
            String preview=com.codex.blacklight.animation.PlayerMotionController.previewKey(player);
            if(preview!=null&&!preview.startsWith("basic_idle"))pose=com.codex.blacklight.animation.MotionLibrary.pose(preview,com.codex.blacklight.animation.PlayerMotionController.elapsed(player,partial),false);
            else if(weapon!=null)pose=com.codex.blacklight.animation.BlacklightPose.interpolatePose(pose,
                    com.codex.blacklight.animation.MotionLibrary.pose(weapon.key(),weapon.seconds(),weapon.looping()),weapon.blend());
            else {
                var action=com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.PLAYER_ACTION);
                if(action.active(com.codex.blacklight.animation.PlayerMotionController.actionTime(player,partial))&&(!action.clipId().startsWith("basic_")||action.clipId().equals("basic_jump_charge"))
                        ||com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.GRAB_STAGE)!=com.codex.blacklight.combat.ConsumeVisual.NONE)
                    pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(player,partial);
            }
            var matrices=com.codex.blacklight.animation.PlayerMotionController.skeleton().getPoseMatrices(pose);
            var frame=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
            var side=left?com.codex.blacklight.client.tuning.ClawTransformSettings.HandSide.LEFT:com.codex.blacklight.client.tuning.ClawTransformSettings.HandSide.RIGHT;
            var hammer=WeaponScaleRenderer.sample(player,partial)[left?1:0];
            var view=com.codex.blacklight.client.animation.FirstPersonPose.convertArm(matrices,form,plated,false,
                    com.codex.blacklight.client.animation.NativeShieldGrip.matrix(player,partial),
                    (float)com.codex.blacklight.client.tuning.ClawTransformSettings.limbWidth(side),
                    (float)com.codex.blacklight.client.tuning.ClawTransformSettings.talonLength(side),
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),frame,form==PowerForm.HAMMERFISTS?hammer.maximumScale():1F,left,
                    morph.parts().stream().map(WeaponMorphTracker.Part::form).toList(),.045F*morph.flow());
            VIEWS.put(player,new RenderedView(view,player.level().getGameTime()+partial));
            if(weave!=null){
                boolean useMesh=plated||morph.visible(left);
                String suffix=left?"_L":"_R";
                var rig=com.codex.blacklight.animation.PlayerMotionController.skeleton();
                var forearmPose=view.matrices()[rig.searchJointByName("Arm"+suffix).getId()];
                var handPose=view.matrices()[rig.searchJointByName("Hand"+suffix).getId()];
                // Plain arms keep Minecraft's static cuboid. Transformed hands use the same matrices as their visible mesh.
                var wrist=useMesh?new net.minecraft.world.phys.Vec3(handPose.getTranslation(new org.joml.Vector3f())):new net.minecraft.world.phys.Vec3(0,.65,-.08);
                var forearm=useMesh?new net.minecraft.world.phys.Vec3(forearmPose.getTranslation(new org.joml.Vector3f())).lerp(wrist,.60):new net.minecraft.world.phys.Vec3(0,.34,-.07);
                var settleWrist=wrist;var settleForearm=forearm;
                if(useMesh&&weave.residue()>.004F){
                    var endpoints=com.codex.blacklight.client.animation.FirstPersonPose.consumeSurface(form,plated,left,view.matrices(),frame,
                            com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),
                            (float)com.codex.blacklight.client.tuning.ClawTransformSettings.limbWidth(side),
                            (float)com.codex.blacklight.client.tuning.ClawTransformSettings.talonLength(side),hammer.maximumScale());
                    if(endpoints!=null){
                        settleWrist=new net.minecraft.world.phys.Vec3(endpoints[0]);settleForearm=new net.minecraft.world.phys.Vec3(endpoints[1]);
                        if(settleWrist.distanceToSqr(settleForearm)<.018)settleForearm=settleWrist.add(forearm.subtract(wrist).normalize().scale(.19));
                    }
                    // During the residue tail lift the actual arm and all its layers together if only a sliver remains on screen.
                    var projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
                    var eye=frame.transformPosition(settleWrist.lerp(settleForearm,.4).toVector3f());
                    if(eye.z<-.10F){
                        var ndc=projection.transformProject(eye,new org.joml.Vector3f());
                        float weight=Math.min(1,weave.residue()*3);
                        float dy=Math.max(0,Math.min(.83F,-.77F-ndc.y));
                        float dx=-Math.copySign(Math.max(0,Math.min(.38F,Math.abs(ndc.x)-.85F)),ndc.x);
                        var shift=new org.joml.Vector3f(dx*(-eye.z)/projection.m00(),dy*(-eye.z)/projection.m11(),0).mul(weight);
                        new org.joml.Matrix4f(frame).invert().transformDirection(shift);
                        stack.translate(shift.x,shift.y,shift.z);
                    }
                }
                FirstPersonConsumeTendrils.draw(player,stack,buffers,light,partial,left,wrist,forearm,settleWrist,settleForearm,weave);
            }
            if(probeOnly)return;
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
    @Override public net.minecraft.resources.ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        return com.codex.blacklight.client.model.MercerPlayerMesh.active(player)
                ?com.codex.blacklight.client.model.MercerPlayerMesh.TEXTURE:super.getTextureLocation(player);
    }
    @Override protected void scale(AbstractClientPlayer player,PoseStack stack,float partial) {
        super.scale(player,stack,partial);
        float armorScale=com.codex.blacklight.client.model.MercerArmorMesh.active(player)?1F:com.codex.blacklight.client.model.BlockArmorModel.get().bodyScale(player,partial);
        float characterScale=com.codex.blacklight.client.model.MercerPlayerMesh.displayScale(player,partial);
        stack.scale(armorScale*characterScale,armorScale*characterScale,armorScale*characterScale);
    }
    @Override protected void setupRotations(AbstractClientPlayer player,PoseStack stack,float bob,float bodyYaw,float partial) {
        float facing=com.codex.blacklight.client.animation.BlacklightRenderPose.facing(player,partial);
        super.setupRotations(player,stack,bob,facing,partial);
    }
}
