package com.codex.passives;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
@EventBusSubscriber(modid=PassivesMod.ID, value=Dist.CLIENT)
public final class PassiveClient {
    public static void accept(CompoundTag n) { var p = Minecraft.getInstance().player; if (p != null) PassiveData.decode(n).save(p); }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        if (!PassiveDisplayConfig.SHOW_STACKS.get()) return;
        var mc = Minecraft.getInstance(); var p = mc.player;
        if (p == null || !p.isAlive() || p.isSpectator() || mc.options.hideGui || mc.screen != null) return;
        var d = PassiveData.read(p); var g = e.getGuiGraphics(); int y = mc.getWindow().getGuiScaledHeight() - 75;
        if (d.adaptationTier > 0) {
            var text = Component.translatable("hud.adaptive_passives.adaptation", d.adaptation, PassiveRules.mitigation(d.adaptation, p.getHealth(), p.getMaxHealth(), d.adaptationTier));
            g.drawString(mc.font, text, mc.getWindow().getGuiScaledWidth() - mc.font.width(text) - 8, y, 0xFF9AD9EC, true); y -= 12;
        }
        if (d.regenerationTier > 0) {
            var text = Component.translatable("hud.adaptive_passives.regeneration", d.regeneration);
            g.drawString(mc.font, text, mc.getWindow().getGuiScaledWidth() - mc.font.width(text) - 8, y, 0xFFEB9C9D, true); y -= 12;
        }
        if (d.vitality) {
            var text = Component.translatable("hud.adaptive_passives.vitality", String.format(java.util.Locale.ROOT, "%.0f", d.bonusHealth));
            g.drawString(mc.font, text, mc.getWindow().getGuiScaledWidth() - mc.font.width(text) - 8, y, 0xFFFFD68A, true);
        }
    }
}
