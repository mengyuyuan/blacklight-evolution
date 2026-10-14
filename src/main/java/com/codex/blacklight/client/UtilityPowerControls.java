package com.codex.blacklight.client;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.PowerSelectPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Quick toggles retain the last selected member of each category. */
@EventBusSubscriber(modid="blacklight", value=Dist.CLIENT)
public final class UtilityPowerControls {
    private static final KeyMapping DEFENSE = new KeyMapping("key.blacklight.defense", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.blacklight");
    private static final KeyMapping SENSE = new KeyMapping("key.blacklight.senses", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.blacklight");
    private static PowerForm lastDefense = PowerForm.SHIELD, lastSense = PowerForm.INFECTED_VISION;
    private static int senseHeld;
    private static boolean senseConsumed;
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(DEFENSE); e.register(SENSE); }
    @SubscribeEvent public static void tick(ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).isPrototypeMode()) {
            while (DEFENSE.consumeClick()) { } while (SENSE.consumeClick()) { }
            senseHeld=0;senseConsumed=false;return;
        }
        if (UtilityPowerSystem.active(mc.player, PowerForm.SHIELD)) lastDefense = PowerForm.SHIELD;
        if (UtilityPowerSystem.active(mc.player, PowerForm.ARMOR)) lastDefense = PowerForm.ARMOR;
        if (UtilityPowerSystem.active(mc.player, PowerForm.THERMAL_VISION)) lastSense = PowerForm.THERMAL_VISION;
        if (UtilityPowerSystem.active(mc.player, PowerForm.INFECTED_VISION)) lastSense = PowerForm.INFECTED_VISION;
        while (DEFENSE.consumeClick()) toggle(mc, lastDefense, lastDefense == PowerForm.SHIELD ? PowerForm.ARMOR : PowerForm.SHIELD);
        boolean sensePressed=false;
        while(SENSE.consumeClick())sensePressed=true;
        if(SENSE.isDown()) {
            if(!senseConsumed&&++senseHeld>=10){toggleSense(mc,true);senseConsumed=true;}
        }else {
            if((senseHeld>0||sensePressed)&&!senseConsumed)toggleSense(mc,false);
            senseHeld=0;senseConsumed=false;
        }
    }
    private static void toggleSense(Minecraft mc,boolean longTransform) {
        var state=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE);
        var fallback=lastSense==PowerForm.INFECTED_VISION?PowerForm.THERMAL_VISION:PowerForm.INFECTED_VISION;
        var selected=state.hasUnlocked(lastSense)?lastSense:state.hasUnlocked(fallback)?fallback:lastSense;
        PacketDistributor.sendToServer(new PowerSelectPayload(selected.ordinal(),longTransform));
    }
    private static void toggle(Minecraft mc, PowerForm wanted, PowerForm fallback) {
        var state = com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE);
        PowerForm selected = state.hasUnlocked(wanted) ? wanted : state.hasUnlocked(fallback) ? fallback : wanted;
        PacketDistributor.sendToServer(new PowerSelectPayload(selected.ordinal()));
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE).isPrototypeMode()) return;
        var g = e.getGuiGraphics(); int y = PrototypeHealthHud.utilityTop();
        if (UtilityPowerSystem.active(mc.player, PowerForm.SHIELD)) {
            var s = com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.UTILITY_STATE);
            int ticks = (int)Math.max(0, s.recoverAt() - mc.level.getGameTime());
            Component line = s.broken() ? Component.translatable("hud.blacklight.shield_rebuild", String.format(java.util.Locale.ROOT,"%.1f",ticks/20F))
                    : Component.translatable("hud.blacklight.shield",Math.round(s.shield()/UtilityState.SHIELD_CAPACITY*100))
                            .append(" · ").append(Component.translatable("hud.blacklight.shield_stage_"+s.damageStage()));
            g.drawString(mc.font,line,10,y,s.broken()?0xFFFF795A:0xFFDCE7EF,true);
            g.fill(10,y+12,110,y+15,0xA0202630);
            float progress=s.broken()?s.rebuildProgress(mc.level.getGameTime()):s.shield()/UtilityState.SHIELD_CAPACITY;
            g.fill(10,y+12,10+Math.round(100*progress),y+15,s.broken()?0xFFFF795A:0xFFCADBE6);y+=23;
        } else if (UtilityPowerSystem.active(mc.player, PowerForm.ARMOR)) {
            boolean forming=mc.level.getGameTime()-com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.UTILITY_STATE).changedAt()<81;
            g.drawString(mc.font,Component.translatable(forming?"hud.blacklight.armor_forming":"hud.blacklight.armor"),10,y,0xFFDCE7EF,true);y+=14;
        }
        PowerForm vision = UtilityPowerSystem.active(mc.player, PowerForm.INFECTED_VISION) ? PowerForm.INFECTED_VISION
                : UtilityPowerSystem.active(mc.player, PowerForm.THERMAL_VISION) ? PowerForm.THERMAL_VISION : PowerForm.NONE;
        if (vision != PowerForm.NONE) {
            g.drawString(mc.font,Component.translatable("hud.blacklight."+vision.id()),10,y,0xFFFFAA83,true);y+=14;
            if (com.codex.blacklight.client.renderer.VisionPowerRenderer.effectFailed())
                g.drawString(mc.font,Component.translatable("hud.blacklight.vision_failed"),10,y,0xFFFF795A,true);
        }
    }
}
