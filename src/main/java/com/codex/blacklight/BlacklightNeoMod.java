package com.codex.blacklight;

import com.codex.blacklight.command.BlacklightCommands;
import com.codex.blacklight.combat.ClawCombatSystem;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.network.ModNetwork;
import com.codex.blacklight.movement.LandingDebrisSystem;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.codex.blacklight.mode.PrototypeSurvivalLock;
import com.codex.blacklight.mode.PrototypeModeSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModEffects;
import com.codex.blacklight.registry.ModItems;
import com.codex.blacklight.registry.ModParticles;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

@Mod(BlacklightNeoMod.MOD_ID)
public final class BlacklightNeoMod {
    public static final String MOD_ID = "blacklight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlacklightNeoMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,com.codex.blacklight.config.PassiveHudConfig.SPEC,"blacklight-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,com.codex.blacklight.city.CityConfig.SPEC,"blacklight-city-server.toml");
        com.codex.blacklight.city.CityBlocks.register(modBus);
        com.codex.blacklight.city.CityWorldgen.register(modBus);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.city.CitySystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.city.CityTravel.class);
        ModAttachments.register(modBus);
        ModEntities.register(modBus);
        ModEffects.register(modBus);
        ModItems.register(modBus);
        com.codex.blacklight.registry.ModBlocks.register(modBus);
        com.codex.blacklight.registry.ModWorldgen.register(modBus);
        ModParticles.register(modBus);
        ModNetwork.register();
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(()->{
            com.codex.blacklight.power.BiomassRules.initialize();
            com.codex.blacklight.animation.MotionLibrary.requireInstalled();
            var health=(net.minecraft.world.entity.ai.attributes.RangedAttribute)net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
            ((com.codex.blacklight.mixin.HealthAttributeLimitAccessor)health).blacklight$setMaximum(Math.max(health.getMaxValue(),1000000));
        }));
        MinecraftForge.EVENT_BUS.register(BlacklightCommands.class);
        MinecraftForge.EVENT_BUS.register(ClawCombatSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.power.GrowthSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.power.UtilityPowerSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.power.ProjectileDefenseSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.combat.WeaponCombatSystem.class);
        MinecraftForge.EVENT_BUS.register(PrototypeMovementSystem.class);
        MinecraftForge.EVENT_BUS.register(LandingDebrisSystem.class);
        MinecraftForge.EVENT_BUS.register(PrototypeSurvivalLock.class);
        MinecraftForge.EVENT_BUS.register(PrototypeModeSystem.class);
        com.codex.blacklight.animation.PlayerActions.install(new com.codex.blacklight.animation.NativePlayerActionBackend());
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.animation.NativePlayerActionBackend.class);
        MinecraftForge.EVENT_BUS.register(BlacklightInfectionSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.world.ResearchStationTrades.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.world.SurvivalGuide.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.combat.ConsumeSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.combat.BlockThrowSystem.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.power.BiomassSystem.class);
        MinecraftForge.EVENT_BUS.register(ModNetwork.class);
        MinecraftForge.EVENT_BUS.register(com.codex.blacklight.disguise.DisguiseSystem.class);
        if (net.minecraftforge.fml.ModList.get().isLoaded("parcool")) {
            MinecraftForge.EVENT_BUS.register(com.codex.blacklight.integration.parcool.ParCoolCompatibility.class);
        }
        LOGGER.info("Blacklight: Ascension initialized with independent player actions, skeletal rendering and GeckoLib entity animations");
    }
}
