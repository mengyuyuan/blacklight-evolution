package com.codex.blacklight.firearms;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Keeps firearms in Minecraft's item renderer without vanilla use-swing/equip bob. */
@EventBusSubscriber(modid = "blacklight", value = Dist.CLIENT)
public final class FirearmViewEvents {
    private static final FirearmViewState MAIN = new FirearmViewState();
    private static final FirearmViewState OFF = new FirearmViewState();
    private static Object world;
    private static Object player;

    private FirearmViewEvents() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level || player != mc.player) {
            MAIN.reset();
            OFF.reset();
            world = mc.level;
            player = mc.player;
        }
        boolean enabled = mc.player != null && mc.level != null && mc.screen == null
                && !mc.isPaused() && mc.options.getCameraType().isFirstPerson()
                && mc.getCameraEntity() == mc.player && visibleBody(mc);
        if (!enabled) {
            MAIN.reset();
            OFF.reset();
            return;
        }
        var mainSnapshot = snapshot(mc, InteractionHand.MAIN_HAND);
        var offSnapshot = snapshot(mc, InteractionHand.OFF_HAND);
        FirearmViewState.observeHands(MAIN, mainSnapshot, OFF, offSnapshot, mc.level.getGameTime(), true);
    }

    private static boolean visibleBody(Minecraft mc) {
        return mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.isScoping()
                && !com.codex.blacklight.power.BiomassSystem.active(mc.player)
                && !com.codex.blacklight.disguise.DisguiseSystem.active(mc.player)
                && !com.codex.blacklight.combat.ConsumeControl.ownsMotion(mc.player);
    }

    private static FirearmViewState.HeldSnapshot snapshot(Minecraft mc, InteractionHand hand) {
        if (!(mc.player.getItemInHand(hand).getItem() instanceof FirearmItem gun)) return null;
        var ammo = FirearmItem.state(mc.player.getItemInHand(hand), 0);
        var definition = gun.definition();
        int slot = hand == InteractionHand.MAIN_HAND ? mc.player.getInventory().selected : -1;
        return new FirearmViewState.HeldSnapshot(definition.id(), slot, ammo.magazine(), ammo.readyAt(),
                definition.cooldownTicks(), definition.reloadTicks());
    }

    /** Draw the real player skin arm with its wrist placed at the item grip. */
    private static void arm(Minecraft mc,RenderHandEvent event,FirearmHandPose.Hand hand){
        if(mc.player.isInvisible())return;
        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var model=renderer.getModel();var arm=hand.left()?model.leftArm:model.rightArm;var sleeve=hand.left()?model.leftSleeve:model.rightSleeve;
        var armPose=arm.storePose();var sleevePose=sleeve.storePose();boolean armVisible=arm.visible,sleeveVisible=sleeve.visible;
        float ax=arm.xScale,ay=arm.yScale,az=arm.zScale,sx=sleeve.xScale,sy=sleeve.yScale,sz=sleeve.zScale;
        var pose=event.getPoseStack();pose.pushPose();
        try{
            arm.resetPose();arm.setRotation(0,0,0);arm.visible=true;sleeve.copyFrom(arm);
            sleeve.visible=mc.player.isModelPartShown(hand.left()?net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE:net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE);
            boolean slim=mc.player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            FirearmHandTransforms.apply(pose,hand,arm,slim);
            var skin=mc.player.getSkin().texture();var buffers=event.getMultiBufferSource();
            arm.render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(skin)),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            sleeve.render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(skin)),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }finally{
            arm.loadPose(armPose);sleeve.loadPose(sleevePose);arm.visible=armVisible;sleeve.visible=sleeveVisible;
            arm.xScale=ax;arm.yScale=ay;arm.zScale=az;sleeve.xScale=sx;sleeve.yScale=sy;sleeve.zScale=sz;pose.popPose();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getCameraEntity() != mc.player
                || mc.screen!=null || mc.isPaused() || !mc.options.getCameraType().isFirstPerson() || !visibleBody(mc)
                || !(event.getItemStack().getItem() instanceof FirearmItem)) return;
        // This hand owns its firearm. Powered empty-hand rendering must not replace it.
        event.setCanceled(true);
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            boolean main = event.getHand() == InteractionHand.MAIN_HAND;
            HumanoidArm side = main ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
            int sign = side == HumanoidArm.RIGHT ? 1 : -1;
            var state=main?MAIN:OFF;double now=mc.level.getGameTime()+event.getPartialTick();
            var frame=state.frame(now);double reload=state.reloadProgress(now);
            // One common camera basis keeps the two real hands and the item in phase.
            FirearmHandTransforms.feedback(pose,frame,side==HumanoidArm.LEFT);
            String weapon=((FirearmItem)event.getItemStack().getItem()).definition().id();boolean leftMain=side==HumanoidArm.LEFT;
            arm(mc,event,FirearmHandPose.primary(weapon,leftMain,reload));
            var support=FirearmSupportPose.evaluate(main,true,mc.player.getOffhandItem().isEmpty(),leftMain,reload,weapon);
            if(support.visible())arm(mc,event,FirearmHandPose.support(weapon,leftMain,reload));
            var access=(com.codex.blacklight.mixin.client.NativeHandRendererAccess)mc.gameRenderer.itemInHandRenderer;
            access.blacklight$applyItemArmTransform(pose,side,0);
            pose.translate(-sign*.13,.035,0);
            mc.gameRenderer.itemInHandRenderer.renderItem(mc.player, event.getItemStack(),
                    side == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    side == HumanoidArm.LEFT, pose, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            pose.popPose();
        }
    }
}
