package com.codex.blacklight.gametest;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Suite overrides are transient. Forge 47's autosaving configs otherwise race its
 * file-watcher reload thread when hundreds of scenarios call ConfigValue.set().
 * Real config loading and a disk TOML round-trip must succeed before detaching.
 * This class is compiled only into opt-in QA runs, never a production artifact.
 */
@EventBusSubscriber(modid="blacklight")
public final class ForgeTestConfigIsolation {
    private ForgeTestConfigIsolation() {}

    @SubscribeEvent
    public static void beforeTests(ServerAboutToStartEvent event) throws IOException {
        if (!(event.getServer() instanceof GameTestServer)) return;
        int verified = 0;
        for (var config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER)) {
            if (!config.getModId().equals("blacklight")) continue;
            if (!(config.getSpec() instanceof ForgeConfigSpec spec)
                    || !(config.getConfigData() instanceof CommentedFileConfig file)) {
                throw new IllegalStateException("Expected a real loaded server config: " + config.getFileName());
            }
            // Keep a watch entry for Forge's normal unload path to remove once.
            FileWatcher.defaultInstance().setWatch(file.getNioPath(), () -> {});
            var disk = new TomlParser().parse(Files.readString(file.getNioPath()));
            if (!spec.isCorrect(disk)) {
                throw new IllegalStateException("Loaded config fails disk round-trip: " + config.getFileName());
            }
            spec.setConfig(disk);
            verified++;
        }
        if (verified == 0) throw new IllegalStateException("No server configs were verified before GameTests");
        LogUtils.getLogger().info("QA verified {} actual server config files; scenario overrides are in memory", verified);
    }
}
