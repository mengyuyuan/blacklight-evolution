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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BlacklightNeoMod.MOD_ID)
public final class BlacklightNeoMod {
    public static final String MOD_ID = "blacklight";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlacklightNeoMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.codex.blacklight.config.PassiveHudConfig.SPEC, "blacklight-client.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.city.CityConfig.SPEC, "blacklight-city-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.infection.BuildingInfectionConfig.SPEC, "blacklight-building-infection-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.infection.TissueCompetitionConfig.SPEC, "blacklight-evolution-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.infection.EvolvedBalance.SPEC, "blacklight-evolved-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.config.CrossModCombatConfig.SPEC, "blacklight-combat-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.infection.OutbreakConfig.SPEC, "blacklight-outbreak-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.power.PlayerEvolutionConfig.SPEC, "blacklight-player-evolution-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.codex.blacklight.config.GameplayBalanceConfig.SPEC, "blacklight-gameplay-server.toml");
        com.codex.blacklight.city.CityBlocks.register(modBus);
        com.codex.blacklight.city.CityWorldgen.register(modBus);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.city.CitySystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.infection.BuildingInfectionSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.city.CityTravel.class);
        ModAttachments.register(modBus);
        ModEntities.register(modBus);
        ModEffects.register(modBus);
        ModItems.register(modBus);
        com.codex.blacklight.registry.ModBlocks.register(modBus);
        com.codex.blacklight.registry.ModBlockEntities.register(modBus);
        com.codex.blacklight.registry.ModWorldgen.register(modBus);
        ModParticles.register(modBus);
        com.codex.blacklight.registry.ModWeaponSounds.register(modBus);
        com.codex.blacklight.registry.ModInfectedSounds.register(modBus);
        modBus.addListener(ModNetwork::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(()->{
            com.codex.blacklight.power.BiomassRules.initialize();
            com.codex.blacklight.animation.MotionLibrary.requireInstalled();
            var health=(net.minecraft.world.entity.ai.attributes.RangedAttribute)net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value();
            ((com.codex.blacklight.mixin.HealthAttributeLimitAccessor)health).blacklight$setMaximum(Math.max(health.getMaxValue(),1e24));
        }));
        NeoForge.EVENT_BUS.register(BlacklightCommands.class);
        NeoForge.EVENT_BUS.register(ClawCombatSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.power.GrowthSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.power.UtilityPowerSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.power.ProjectileDefenseSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.combat.WeaponCombatSystem.class);
        NeoForge.EVENT_BUS.register(PrototypeMovementSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.movement.WingFlightSystem.class);
        NeoForge.EVENT_BUS.register(LandingDebrisSystem.class);
        NeoForge.EVENT_BUS.register(PrototypeSurvivalLock.class);
        NeoForge.EVENT_BUS.register(PrototypeModeSystem.class);
        com.codex.blacklight.animation.PlayerActions.install(new com.codex.blacklight.animation.NativePlayerActionBackend());
        NeoForge.EVENT_BUS.register(com.codex.blacklight.animation.NativePlayerActionBackend.class);
        NeoForge.EVENT_BUS.register(BlacklightInfectionSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.world.ResearchStationTrades.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.world.SurvivalGuide.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.combat.ConsumeSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.combat.BlockThrowSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.power.BiomassSystem.class);
        NeoForge.EVENT_BUS.register(ModNetwork.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.disguise.DisguiseSystem.class);
        NeoForge.EVENT_BUS.register(com.codex.blacklight.disguise.SpeciesAbilities.class);
        if (net.neoforged.fml.ModList.get().isLoaded("parcool")) {
            NeoForge.EVENT_BUS.register(com.codex.blacklight.integration.parcool.ParCoolCompatibility.class);
        }
        LOGGER.info("Blacklight: Ascension initialized with independent player actions, skeletal rendering and GeckoLib entity animations");
    }
}
