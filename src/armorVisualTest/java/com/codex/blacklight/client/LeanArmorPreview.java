package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicit local developer launch only; prepares a playable armor scene and stops. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class LeanArmorPreview {
    private static boolean prepared;
    private LeanArmorPreview() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (prepared || !Boolean.getBoolean("blacklight.test.leanArmor")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.tickCount < 35 || mc.getSingleplayerServer() == null) return;
        var server = mc.getSingleplayerServer();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (player == null) return;
        prepared = true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        server.execute(() -> {
            var state = player.getData(ModAttachments.PLAYER_STATE);
            if (!state.isInfected()) state.infect();
            state.setPrototypeMode(true);
            state.unlock(PowerForm.ARMOR);
            state.activate(PowerForm.NONE);
            player.setData(ModAttachments.PLAYER_STATE, state);
            player.syncData(ModAttachments.PLAYER_STATE);
            player.setGameMode(GameType.CREATIVE);
            if (!UtilityPowerSystem.active(player, PowerForm.ARMOR)) UtilityPowerSystem.toggle(player, PowerForm.ARMOR);
            var pos = player.blockPosition();
            int ground = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            player.teleportTo(pos.getX() + .5, ground + .05, pos.getZ() + .5);
            player.serverLevel().setDayTime(6000);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("战甲 dev.17：收腰、斜方肌连接后颈、加厚大腿；原尺寸等比放大 1.2 倍。"), false);
            com.mojang.logging.LogUtils.getLogger().info("Original block armor preview ready: dev.17, 127 parts, original pixel atlas, connected trapezius, thicker thighs, uniform scale 1.2x.");
        });
    }
}
