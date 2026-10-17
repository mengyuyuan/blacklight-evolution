package com.codex.blacklight.client.tuning;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.Properties;

/** Local cosmetic preference; never changes another player's model or gameplay state. */
public final class MercerAppearance {
    private static boolean loaded, enabled;
    private MercerAppearance() { }
    private static java.nio.file.Path file() { return FMLPaths.CONFIGDIR.get().resolve("blacklight-appearance.properties"); }
    private static void load() {
        if (loaded) return;
        loaded=true;
        if (!Files.isRegularFile(file())) return;
        try (var input=Files.newInputStream(file())) {
            var values=new Properties(); values.load(input);
            enabled=Boolean.parseBoolean(values.getProperty("mercer", "false"));
        } catch (java.io.IOException e) { BlacklightNeoMod.LOGGER.warn("Cannot load Mercer appearance preference", e); }
    }
    public static boolean active(AbstractClientPlayer player) {
        load();
        return enabled && player!=null && player==Minecraft.getInstance().player
                && ModAttachments.getData(player,ModAttachments.PLAYER_STATE).isPrototypeMode();
    }
    public static Component label() { load(); return Component.literal(enabled?"外观：A哥 · 黑光动作":"外观：玩家皮肤"); }
    public static void toggle() {
        load();enabled=!enabled;
        var values=new Properties();values.setProperty("mercer", Boolean.toString(enabled));
        try {
            Files.createDirectories(file().getParent());
            try(var output=Files.newOutputStream(file())) { values.store(output,"Blacklight local character appearance"); }
        } catch(java.io.IOException e) { BlacklightNeoMod.LOGGER.warn("Cannot save Mercer appearance preference",e); }
    }
}
