package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.BlacklightVirusVialEntity;
import com.codex.blacklight.entity.GroundSpikeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BlacklightNeoMod.MOD_ID);

    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightInfectedHost>> INFECTED_HOST =
            ENTITIES.register("infected_host", () -> EntityType.Builder
                    .<com.codex.blacklight.entity.BlacklightInfectedHost>of(com.codex.blacklight.entity.BlacklightInfectedHost::new, MobCategory.MONSTER)
                    .sized(.9F, 1.2F).clientTrackingRange(16).updateInterval(2).build("infected_host"));

    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightCommonInfected>> COMMON_INFECTED =
            ENTITIES.register("common_infected", () -> EntityType.Builder
                    .<com.codex.blacklight.entity.BlacklightCommonInfected>of(com.codex.blacklight.entity.BlacklightCommonInfected::new,MobCategory.MONSTER)
                    .sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("common_infected"));

    public static final RegistryObject<EntityType<GroundSpikeEntity>> GROUND_SPIKE =
            ENTITIES.register("ground_spike", () -> EntityType.Builder
                    .<GroundSpikeEntity>of(GroundSpikeEntity::new, MobCategory.MISC)
                    .sized(3.6F, 5.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build("ground_spike"));

    public static final RegistryObject<EntityType<BlacklightVirusVialEntity>> BLACKLIGHT_VIRUS_VIAL =
            ENTITIES.register("blacklight_virus_vial", () -> EntityType.Builder
                    .<BlacklightVirusVialEntity>of(BlacklightVirusVialEntity::new, MobCategory.MISC)
                    .sized(0.28F, 0.42F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build("blacklight_virus_vial"));

    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightHunter>> BLACKLIGHT_HUNTER =
            ENTITIES.register("blacklight_hunter", () -> EntityType.Builder
                    .<com.codex.blacklight.entity.BlacklightHunter>of(com.codex.blacklight.entity.BlacklightHunter::new, MobCategory.MONSTER)
                    .sized(1.05F, 2.35F).clientTrackingRange(10).updateInterval(1).build("blacklight_hunter"));

    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_STALKER=mutant("blacklight_stalker",com.codex.blacklight.entity.BlacklightMutant.Kind.STALKER,.9F,1.1F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_SPITTER=mutant("blacklight_spitter",com.codex.blacklight.entity.BlacklightMutant.Kind.SPITTER,.8F,2.0F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightMutant>> BLACKLIGHT_BRUTE=mutant("blacklight_brute",com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE,1.3F,2.5F);
    private static RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightMutant>> mutant(String id,com.codex.blacklight.entity.BlacklightMutant.Kind kind,float width,float height){
        return ENTITIES.register(id,()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightMutant>of((type,level)->new com.codex.blacklight.entity.BlacklightMutant(type,level,kind),MobCategory.MONSTER).sized(width,height).clientTrackingRange(10).updateInterval(2).build(id));
    }
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightHive>> BLACKLIGHT_HIVE=ENTITIES.register("blacklight_hive",()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightHive>of(com.codex.blacklight.entity.BlacklightHive::new,MobCategory.MONSTER).sized(4,3.5F).clientTrackingRange(12).build("blacklight_hive"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightSpit>> BLACKLIGHT_SPIT=ENTITIES.register("blacklight_spit",()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightSpit>of(com.codex.blacklight.entity.BlacklightSpit::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8).updateInterval(1).build("blacklight_spit"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.EvolvedQuill>> EVOLVED_QUILL=ENTITIES.register("evolved_quill",()->EntityType.Builder.<com.codex.blacklight.entity.EvolvedQuill>of(com.codex.blacklight.entity.EvolvedQuill::new,MobCategory.MISC).sized(.18F,.18F).clientTrackingRange(10).updateInterval(1).build("evolved_quill"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.InfectedTumor>> INFECTED_TUMOR=ENTITIES.register("infected_tumor",()->EntityType.Builder.<com.codex.blacklight.entity.InfectedTumor>of(com.codex.blacklight.entity.InfectedTumor::new,MobCategory.MISC).sized(.65F,.65F).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build("infected_tumor"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.HiveTendril>> HIVE_TENDRIL=ENTITIES.register("hive_tendril",()->EntityType.Builder.<com.codex.blacklight.entity.HiveTendril>of(com.codex.blacklight.entity.HiveTendril::new,MobCategory.MISC).sized(.8F,.8F).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build("hive_tendril"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.ThrownBlock>> THROWN_BLOCK=ENTITIES.register("thrown_block",()->EntityType.Builder.<com.codex.blacklight.entity.ThrownBlock>of(com.codex.blacklight.entity.ThrownBlock::new,MobCategory.MISC).sized(.9F,.9F).clientTrackingRange(10).updateInterval(1).build("thrown_block"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_SOLDIER=ENTITIES.register("blackwatch_soldier",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of(com.codex.blacklight.entity.BlackwatchSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_soldier"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_HEAVY=ENTITIES.register("blackwatch_heavy",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of(com.codex.blacklight.entity.BlackwatchSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_heavy"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.RegularSoldier>> MILITARY_SOLDIER=ENTITIES.register("military_soldier",()->EntityType.Builder.<com.codex.blacklight.entity.RegularSoldier>of(com.codex.blacklight.entity.RegularSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("military_soldier"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.RegularSoldier>> MILITARY_HEAVY=ENTITIES.register("military_heavy",()->EntityType.Builder.<com.codex.blacklight.entity.RegularSoldier>of(com.codex.blacklight.entity.RegularSoldier::new,MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("military_heavy"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BiomassHusk>> BIOMASS_HUSK=ENTITIES.register("biomass_husk",()->EntityType.Builder.<com.codex.blacklight.entity.BiomassHusk>of(com.codex.blacklight.entity.BiomassHusk::new,MobCategory.MISC).sized(.6F,1.8F).clientTrackingRange(10).updateInterval(2).build("biomass_husk"));
    public static final RegistryObject<EntityType<com.codex.blacklight.firearms.OrdnanceProjectile>> MILITARY_ORDNANCE=ENTITIES.register("military_ordnance",()->EntityType.Builder.<com.codex.blacklight.firearms.OrdnanceProjectile>of(com.codex.blacklight.firearms.OrdnanceProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(12).updateInterval(1).build("military_ordnance"));
    public static final RegistryObject<EntityType<com.codex.blacklight.firearms.VehicleOrdnanceProjectile>> VEHICLE_ORDNANCE=ENTITIES.register("vehicle_ordnance",()->EntityType.Builder.<com.codex.blacklight.firearms.VehicleOrdnanceProjectile>of(com.codex.blacklight.firearms.VehicleOrdnanceProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(12).updateInterval(1).build("vehicle_ordnance"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.RegularSoldier>> MILITARY_TEAM_LEADER=ENTITIES.register("military_team_leader",()->EntityType.Builder.<com.codex.blacklight.entity.RegularSoldier>of((type,level)->new com.codex.blacklight.entity.RegularSoldier(type,level,com.codex.blacklight.military.MilitaryRole.TEAM_LEADER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("military_team_leader"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.RegularSoldier>> MILITARY_OFFICER=ENTITIES.register("military_officer",()->EntityType.Builder.<com.codex.blacklight.entity.RegularSoldier>of((type,level)->new com.codex.blacklight.entity.RegularSoldier(type,level,com.codex.blacklight.military.MilitaryRole.OFFICER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("military_officer"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.RegularSoldier>> MILITARY_COMMANDER=ENTITIES.register("military_commander",()->EntityType.Builder.<com.codex.blacklight.entity.RegularSoldier>of((type,level)->new com.codex.blacklight.entity.RegularSoldier(type,level,com.codex.blacklight.military.MilitaryRole.COMMANDER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("military_commander"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_TEAM_LEADER=ENTITIES.register("blackwatch_team_leader",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of((type,level)->new com.codex.blacklight.entity.BlackwatchSoldier(type,level,com.codex.blacklight.military.MilitaryRole.TEAM_LEADER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_team_leader"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_OFFICER=ENTITIES.register("blackwatch_officer",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of((type,level)->new com.codex.blacklight.entity.BlackwatchSoldier(type,level,com.codex.blacklight.military.MilitaryRole.OFFICER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_officer"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlackwatchSoldier>> BLACKWATCH_COMMANDER=ENTITIES.register("blackwatch_commander",()->EntityType.Builder.<com.codex.blacklight.entity.BlackwatchSoldier>of((type,level)->new com.codex.blacklight.entity.BlackwatchSoldier(type,level,com.codex.blacklight.military.MilitaryRole.COMMANDER),MobCategory.MONSTER).sized(.6F,1.95F).clientTrackingRange(10).updateInterval(2).build("blackwatch_commander"));
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> MILITARY_TRUCK = vehicle("military_truck", "truck", com.codex.blacklight.military.MilitaryFaction.MILITARY, 2.9F, 3.7F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> MILITARY_APC = vehicle("military_apc", "apc", com.codex.blacklight.military.MilitaryFaction.MILITARY, 3.3F, 3.3F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> MILITARY_TANK = vehicle("military_tank", "tank", com.codex.blacklight.military.MilitaryFaction.MILITARY, 4.0F, 3.4F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> BLACKWATCH_TRUCK = vehicle("blackwatch_truck", "truck", com.codex.blacklight.military.MilitaryFaction.BLACKWATCH, 2.9F, 3.7F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> BLACKWATCH_APC = vehicle("blackwatch_apc", "apc", com.codex.blacklight.military.MilitaryFaction.BLACKWATCH, 3.3F, 3.3F);
    public static final RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> BLACKWATCH_TANK = vehicle("blackwatch_tank", "tank", com.codex.blacklight.military.MilitaryFaction.BLACKWATCH, 4.0F, 3.4F);
    private static RegistryObject<EntityType<com.codex.blacklight.entity.MilitaryVehicle>> vehicle(String id, String chassis, com.codex.blacklight.military.MilitaryFaction faction, float width, float height) {
        return ENTITIES.register(id, () -> EntityType.Builder.<com.codex.blacklight.entity.MilitaryVehicle>of((type, level) -> new com.codex.blacklight.entity.MilitaryVehicle(type, level, chassis, faction), MobCategory.MISC)
                .sized(width, height).clientTrackingRange(16).updateInterval(1).build(id));
    }

    private ModEntities() {
    }

    public static final RegistryObject<EntityType<com.codex.blacklight.entity.BlacklightHydra>> BLACKLIGHT_HYDRA=ENTITIES.register("blacklight_hydra",()->EntityType.Builder.<com.codex.blacklight.entity.BlacklightHydra>of(com.codex.blacklight.entity.BlacklightHydra::new,MobCategory.MONSTER).sized(2.4F,5.0F).clientTrackingRange(12).updateInterval(1).build("blacklight_hydra"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(BLACKLIGHT_HYDRA.get(),com.codex.blacklight.entity.BlacklightHydra.attributes().build()));
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(BIOMASS_HUSK.get(),com.codex.blacklight.entity.BiomassHusk.attributes().build()));
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event)->{
            event.put(BLACKWATCH_SOLDIER.get(),com.codex.blacklight.entity.BlackwatchSoldier.attributes(false).build());
            event.put(BLACKWATCH_HEAVY.get(),com.codex.blacklight.entity.BlackwatchSoldier.attributes(true).build());
            event.put(MILITARY_SOLDIER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.MILITARY,false).build());
            event.put(MILITARY_HEAVY.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.MILITARY,true).build());
            event.put(MILITARY_TEAM_LEADER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.MILITARY,false).build());
            event.put(MILITARY_OFFICER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.MILITARY,false).build());
            event.put(MILITARY_COMMANDER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.MILITARY,false).build());
            event.put(BLACKWATCH_TEAM_LEADER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.BLACKWATCH,false).build());
            event.put(BLACKWATCH_OFFICER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.BLACKWATCH,false).build());
            event.put(BLACKWATCH_COMMANDER.get(),com.codex.blacklight.entity.MilitarySoldier.attributes(com.codex.blacklight.military.MilitaryFaction.BLACKWATCH,false).build());
            event.put(HIVE_TENDRIL.get(),com.codex.blacklight.entity.HiveTendril.attributes().build());
            event.put(INFECTED_HOST.get(),com.codex.blacklight.entity.BlacklightInfectedHost.attributes().build());
            event.put(COMMON_INFECTED.get(),com.codex.blacklight.entity.BlacklightCommonInfected.attributes().build());
            event.put(BLACKLIGHT_STALKER.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.STALKER).build());
            event.put(BLACKLIGHT_SPITTER.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.SPITTER).build());
            event.put(BLACKLIGHT_BRUTE.get(),com.codex.blacklight.entity.BlacklightMutant.attributes(com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE).build());
            event.put(BLACKLIGHT_HIVE.get(),com.codex.blacklight.entity.BlacklightHive.attributes().build());
        });
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(BLACKLIGHT_HUNTER.get(), com.codex.blacklight.entity.BlacklightHunter.attributes().build()));
    }
}
