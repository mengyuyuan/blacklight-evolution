package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.BlacklightVirusVialEntity;
import com.codex.blacklight.entity.GroundSpikeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BlacklightNeoMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<com.codex.blacklight.entity.BlacklightInfectedHost>> INFECTED_HOST =
            ENTITIES.register("infected_host", () -> EntityType.Builder
                    .<com.codex.blacklight.entity.BlacklightInfectedHost>of(com.codex.blacklight.entity.BlacklightInfectedHost::new, MobCategory.MONSTER)
                    .sized(.9F, 1.2F).clientTrackingRange(16).updateInterval(2).build("infected_host"));

    public static final DeferredHolder<EntityType<?>, EntityType<GroundSpikeEntity>> GROUND_SPIKE =
            ENTITIES.register("ground_spike", () -> EntityType.Builder
                    .<GroundSpikeEntity>of(GroundSpikeEntity::new, MobCategory.MISC)
                    .sized(3.6F, 5.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build("ground_spike"));

    public static final DeferredHolder<EntityType<?>, EntityType<BlacklightVirusVialEntity>> BLACKLIGHT_VIRUS_VIAL =
            ENTITIES.register("blacklight_virus_vial", () -> EntityType.Builder
                    .<BlacklightVirusVialEntity>of(BlacklightVirusVialEntity::new, MobCategory.MISC)
                    .sized(0.28F, 0.42F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build("blacklight_virus_vial"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.codex.blacklight.entity.BlacklightHunter>> BLACKLIGHT_HUNTER =
            ENTITIES.register("blacklight_hunter", () -> EntityType.Builder
                    .<com.codex.blacklight.entity.BlacklightHunter>of(com.codex.blacklight.entity.BlacklightHunter::new, MobCategory.MONSTER)
                    .sized(1.05F, 2.35F).clientTrackingRange(10).updateInterval(1).build("blacklight_hunter"));

    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_STALKER=mutant("blacklight_stalker",com.codex.blacklight.entity.BlacklightMutant.Kind.STALKER,.9F,1.1F);
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_SPITTER=mutant("blacklight_spitter",com.codex.blacklight.entity.BlacklightMutant.Kind.SPITTER,.8F,2.0F);
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_BRUTE=mutant("blacklight_brute",com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE,1.3F,2.5F);
    private static DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightMutant>> mutant(String id,com.codex.blacklight.entity.BlacklightMutant.Kind kind,float width,float height){
        return ENTITIES.register(id,()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightMutant>of((type,level)->new com.codex.blacklight.entity.BlacklightMutant(type,level,kind),MobCategory.MONSTER).sized(width,height).clientTrackingRange(10).updateInterval(2).build(id));
    }
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightHive>> BLACKLIGHT_HIVE=ENTITIES.register("blacklight_hive",()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightHive>of(com.codex.blacklight.entity.BlacklightHive::new,MobCategory.MONSTER).sized(4,3.5F).clientTrackingRange(12).build("blacklight_hive"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlacklightSpit>> BLACKLIGHT_SPIT=ENTITIES.register("blacklight_spit",()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightSpit>of(com.codex.blacklight.entity.BlacklightSpit::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8).updateInterval(1).build("blacklight_spit"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.HiveTendril>> HIVE_TENDRIL=ENTITIES.register("hive_tendril",()->EntityType.Builder.<com.codex.blacklight.entity.HiveTendril>of(com.codex.blacklight.entity.HiveTendril::new,MobCategory.MISC).sized(.8F,.8F).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build("hive_tendril"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.ThrownBlock>> THROWN_BLOCK=ENTITIES.register("thrown_block",()->EntityType.Builder.<com.codex.blacklight.entity.ThrownBlock>of(com.codex.blacklight.entity.ThrownBlock::new,MobCategory.MISC).sized(.9F,.9F).clientTrackingRange(10).updateInterval(1).build("thrown_block"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_SOLDIER=ENTITIES.register("blackwatch_soldier",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of(com.codex.blacklight.entity.BlackwatchSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_soldier"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_HEAVY=ENTITIES.register("blackwatch_heavy",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of(com.codex.blacklight.entity.BlackwatchSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_heavy"));
    public static final DeferredHolder<EntityType<?>,EntityType<com.codex.blacklight.entity.BiomassHusk>> BIOMASS_HUSK=ENTITIES.register("biomass_husk",()->EntityType.Builder.<com.codex.blacklight.entity.BiomassHusk>of(com.codex.blacklight.entity.BiomassHusk::new,MobCategory.MISC).sized(.6F,1.8F).clientTrackingRange(10).updateInterval(2).build("biomass_husk"));
    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e)->e.put(BIOMASS_HUSK.get(),com.codex.blacklight.entity.BiomassHusk.attributes().build()));
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event)->{
            event.put(BLACKWATCH_SOLDIER.get(),com.codex.blacklight.entity.BlackwatchSoldier.attributes(false).build());
            event.put(BLACKWATCH_HEAVY.get(),com.codex.blacklight.entity.BlackwatchSoldier.attributes(true).build());
            event.put(HIVE_TENDRIL.get(),com.codex.blacklight.entity.HiveTendril.attributes().build());
            event.put(INFECTED_HOST.get(),com.codex.blacklight.entity.BlacklightInfectedHost.attributes().build());
            event.put(BLACKLIGHT_STALKER.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.STALKER).build());
            event.put(BLACKLIGHT_SPITTER.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.SPITTER).build());
            event.put(BLACKLIGHT_BRUTE.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE).build());
            event.put(BLACKLIGHT_HIVE.get(),com.codex.blacklight.entity.BlacklightHive.attributes().build());
        });
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(BLACKLIGHT_HUNTER.get(), com.codex.blacklight.entity.BlacklightHunter.attributes().build()));
    }
}
