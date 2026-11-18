package com.codex.blacklight.registry;

import com.codex.blacklight.entity.*;
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
    static {for(var kind:List.of(EncounterKind.GOLIATH,EncounterKind.FLYER,EncounterKind.ELIZABETH_GREENE)){
        var type=ENTITIES.register(kind.id,()->EntityType.Builder.<EncounterMob>of((t,l)->new EncounterMob(t,l,kind),MobCategory.MONSTER).sized(kind.width,kind.height).clientTrackingRange(16).updateInterval(1).build(kind.id));
        CREATURES.put(kind,type);
        var egg=ITEMS.register(kind.id+"_spawn_egg",()->kind.enabled()?new net.minecraftforge.common.ForgeSpawnEggItem(type,kind.military?0x293638:0x381e22,kind.boss?0xd52922:0x9c7161,new Item.Properties()):new Item(new Item.Properties()));
        if(kind.enabled())EGGS.add(egg);
    }}
    public static final RegistryObject<EntityType<EncounterProjectile>> PROJECTILE=ENTITIES.register("encounter_projectile",()->EntityType.Builder.<EncounterProjectile>of(EncounterProjectile::new,MobCategory.MISC).sized(.3F,.3F).clientTrackingRange(12).updateInterval(1).build("encounter_projectile"));
    public static final RegistryObject<EntityType<GreeneProjectile>> GREENE_PROJECTILE=ENTITIES.register("greene_projectile",()->EntityType.Builder.<GreeneProjectile>of(GreeneProjectile::new,MobCategory.MISC).sized(.3F,.3F).clientTrackingRange(12).updateInterval(1).build("greene_projectile"));
    public static final RegistryObject<EntityType<GreeneSegmentCorpse>> GREENE_SEGMENT_CORPSE=ENTITIES.register("greene_segment_corpse",()->EntityType.Builder.<GreeneSegmentCorpse>of(GreeneSegmentCorpse::new,MobCategory.MISC).sized(.1F,.1F).clientTrackingRange(16).updateInterval(1).build("greene_segment_corpse"));
    private static final RegistryObject<CreativeModeTab> TAB=TABS.register("encounters",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.blacklight.encounters")).icon(()->new ItemStack(EGGS.get(0).get())).displayItems((p,o)->EGGS.forEach(egg->o.accept(egg.get()))).build());
    public static void register(IEventBus bus){ENTITIES.register(bus);ITEMS.register(bus);TABS.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event)->{
        CREATURES.forEach((kind,type)->event.put(type.get(),EncounterMob.attributes(kind).build()));
    });}
    private EncounterEntities(){}
}
