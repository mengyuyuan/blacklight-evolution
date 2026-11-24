package com.codex.blacklight.registry;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.world.GreeneHost;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.*;

/** Separate registration keeps existing entity/save IDs unchanged. */
public final class EncounterEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"blacklight");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"blacklight");
    public static final Map<EncounterKind,RegistryObject<EntityType<EncounterMob>>> CREATURES=new EnumMap<>(EncounterKind.class);
    public static final List<RegistryObject<Item>> EGGS=new ArrayList<>();
    static {for(var kind:EncounterKind.values()){
        if(kind==EncounterKind.BRAWLER)continue; // Legacy ID below resolves to the original hunter.
        var type=ENTITIES.register(kind.id,()->EntityType.Builder.<EncounterMob>of((t,l)->new EncounterMob(t,l,kind),MobCategory.MONSTER).sized(kind.width,kind.height).clientTrackingRange(16).updateInterval(1).build(kind.id));
        CREATURES.put(kind,type);
        var egg=ITEMS.register(kind.id+"_spawn_egg",()->kind.enabled()?new net.minecraftforge.common.ForgeSpawnEggItem(type,kind.military?0x293638:0x381e22,kind.boss?0xd52922:0x9c7161,new Item.Properties()):new Item(new Item.Properties()));
        if(kind.enabled())EGGS.add(egg);
    }}
    /** Keep saved entities, spawners and command IDs readable; the instance uses the canonical type. */
    public static final RegistryObject<EntityType<BlacklightHunter>> LEGACY_BRAWLER=ENTITIES.register("brawler",()->EntityType.Builder.<BlacklightHunter>of((t,l)->new BlacklightHunter(ModEntities.BLACKLIGHT_HUNTER.get(),l),MobCategory.MONSTER).sized(1.05F,2.35F).clientTrackingRange(10).updateInterval(1).build("brawler"));
    // Use the legacy type here so the spawn-egg lookup for blacklight_hunter retains its original egg.
    public static final RegistryObject<Item> LEGACY_BRAWLER_EGG=legacyEgg("brawler",LEGACY_BRAWLER,0x381e22,0x9c7161);
    public static final RegistryObject<EntityType<MilitaryUav>> UAV=ENTITIES.register("recon_uav",()->EntityType.Builder.<MilitaryUav>of(MilitaryUav::new,MobCategory.MONSTER).sized(1.95F,1.76F).clientTrackingRange(14).updateInterval(1).build("recon_uav"));
    public static final RegistryObject<EntityType<EncounterProjectile>> PROJECTILE=ENTITIES.register("encounter_projectile",()->EntityType.Builder.<EncounterProjectile>of(EncounterProjectile::new,MobCategory.MISC).sized(.3F,.3F).clientTrackingRange(12).updateInterval(1).build("encounter_projectile"));
    public static final RegistryObject<EntityType<GreeneSpider>> GREENE_SPIDER = ENTITIES.register("greene_spider",
            () -> EntityType.Builder.<GreeneSpider>of(GreeneSpider::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F).clientTrackingRange(16).build("greene_spider"));
    public static final RegistryObject<EntityType<GreeneGoddess>> GREENE_GODDESS = ENTITIES.register("greene_goddess",
            () -> EntityType.Builder.<GreeneGoddess>of(GreeneGoddess::new, MobCategory.MISC)
                    .sized(0.5F, 1.0F).clientTrackingRange(16).build("greene_goddess"));
    public static final RegistryObject<EntityType<GreeneHost>> GREENE_HOST=ENTITIES.register("greene_host",()->EntityType.Builder.<GreeneHost>of(GreeneHost::new,MobCategory.CREATURE).sized(.6F,1.82F).clientTrackingRange(14).build("greene_host"));
    public static final RegistryObject<EntityType<GreeneProjectile>> GREENE_PROJECTILE=ENTITIES.register("greene_projectile",()->EntityType.Builder.<GreeneProjectile>of(GreeneProjectile::new,MobCategory.MISC).sized(.3F,.3F).clientTrackingRange(12).updateInterval(1).build("greene_projectile"));
    public static final RegistryObject<EntityType<com.codex.blacklight.combat.PlayerGazeField>> PLAYER_GAZE_FIELD=ENTITIES.register("player_gaze_field",()->EntityType.Builder.<com.codex.blacklight.combat.PlayerGazeField>of(com.codex.blacklight.combat.PlayerGazeField::new,MobCategory.MISC).sized(.3F,.3F).clientTrackingRange(12).updateInterval(1).noSave().noSummon().build("player_gaze_field"));
    public static final RegistryObject<EntityType<GoddessCommandProjectile>> GODDESS_COMMAND_PROJECTILE=ENTITIES.register("goddess_command_projectile",()->EntityType.Builder.<GoddessCommandProjectile>of(GoddessCommandProjectile::new,MobCategory.MISC).sized(1.0F,1.0F).clientTrackingRange(12).updateInterval(1).build("goddess_command_projectile"));
    public static final RegistryObject<EntityType<GreeneSegmentCorpse>> GREENE_SEGMENT_CORPSE=ENTITIES.register("greene_segment_corpse",()->EntityType.Builder.<GreeneSegmentCorpse>of(GreeneSegmentCorpse::new,MobCategory.MISC).sized(.1F,.1F).clientTrackingRange(16).updateInterval(1).build("greene_segment_corpse"));
    public static final RegistryObject<Item> UAV_EGG=ITEMS.register("recon_uav_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(UAV,0x293638,0xd73333,new Item.Properties()));
    public static final RegistryObject<Item> COMMON_EGG=legacyEgg("common_infected",ModEntities.COMMON_INFECTED,0x655951,0x792323);
    public static final RegistryObject<Item> HOST_EGG=legacyEgg("infected_host",ModEntities.INFECTED_HOST,0x4d5243,0x9c3025);
    public static final RegistryObject<Item> STALKER_EGG=legacyEgg("blacklight_stalker",ModEntities.BLACKLIGHT_STALKER,0x382522,0xad3d2a);
    public static final RegistryObject<Item> SPITTER_EGG=legacyEgg("blacklight_spitter",ModEntities.BLACKLIGHT_SPITTER,0x4d4732,0xa4ad59);
    public static final RegistryObject<Item> BRUTE_EGG=legacyEgg("blacklight_brute",ModEntities.BLACKLIGHT_BRUTE,0x382c2a,0x994438);
    public static final RegistryObject<Item> HIVE_EGG=legacyEgg("blacklight_hive",ModEntities.BLACKLIGHT_HIVE,0x351718,0x991e23);
    private static RegistryObject<Item> legacyEgg(String id,java.util.function.Supplier<? extends EntityType<? extends Mob>> type,int shell,int spots){
        return ITEMS.register(id+"_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(type,shell,spots,new Item.Properties()));
    }
    /** Enumerate after registration so old, new and future Blacklight spawn eggs share one tab. */
    public static List<Item> allSpawnEggs(){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .filter(item->item instanceof SpawnEggItem&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("blacklight"))
            .filter(item->item!=LEGACY_BRAWLER_EGG.get())
            .sorted(Comparator.comparing(item->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath())).toList();
    }
    private static final RegistryObject<CreativeModeTab> TAB=TABS.register("encounters",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.blacklight.encounters")).icon(()->new ItemStack(EGGS.get(0).get())).displayItems((p,o)->allSpawnEggs().forEach(o::accept)).build());
    public static void register(IEventBus bus){ENTITIES.register(bus);ITEMS.register(bus);TABS.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event)->{
        CREATURES.forEach((kind,type)->event.put(type.get(),EncounterMob.attributes(kind).build()));event.put(UAV.get(),MilitaryUav.attributes().build());
        event.put(GREENE_HOST.get(),Mob.createMobAttributes().build());
        event.put(LEGACY_BRAWLER.get(),BlacklightHunter.attributes().build());
    });}
    private EncounterEntities(){}
}
