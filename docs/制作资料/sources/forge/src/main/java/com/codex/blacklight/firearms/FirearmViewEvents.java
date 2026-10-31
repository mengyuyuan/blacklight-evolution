package com.codex.blacklight.firearms;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderHandEvent;

/** Keeps firearms in Minecraft's item renderer without vanilla use-swing/equip bob. */
@EventBusSubscriber(modid = "blacklight", value = Dist.CLIENT)
public final class FirearmViewEvents {
    private static final FirearmViewState MAIN = new FirearmViewState();
    private static final FirearmViewState OFF = new FirearmViewState();
    private static Object world;
    private static Object player;

    private FirearmViewEvents() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson() || !visibleBody(mc)
                || !(event.getItemStack().getItem() instanceof FirearmItem)) return;
        // This hand owns its firearm. Powered empty-hand rendering must not replace it.
        event.setCanceled(true);
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            boolean main = event.getHand() == InteractionHand.MAIN_HAND;
            HumanoidArm side = main ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
            int sign = side == HumanoidArm.RIGHT ? 1 : -1;
            var frame = (main ? MAIN : OFF).frame(mc.level.getGameTime() + event.getPartialTick());
            var access = (com.codex.blacklight.mixin.client.NativeHandRendererAccess) mc.gameRenderer.itemInHandRenderer;
            access.blacklight$applyItemArmTransform(pose, side, 0);
            pose.translate(-sign * .13, .035 - frame.reload() * .11, frame.recoil() * .065);
            pose.mulPose(Axis.XP.rotationDegrees(frame.recoil() * 4.5F + frame.reload() * 8));
            pose.mulPose(Axis.ZP.rotationDegrees(sign * frame.reload() * 7));
            mc.gameRenderer.itemInHandRenderer.renderItem(mc.player, event.getItemStack(),
                    side == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    side == HumanoidArm.LEFT, pose, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            pose.popPose();
        }
    }
}
