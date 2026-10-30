package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.item.BlacklightSampleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import net.minecraftforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, BlacklightNeoMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlacklightNeoMod.MOD_ID);

    public static final RegistryObject<BlacklightSampleItem> BLACKLIGHT_SAMPLE = ITEMS.register("blacklight_sample", () -> new BlacklightSampleItem(new Item.Properties().stacksTo(4).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> HUNTER_SPAWN_EGG = ITEMS.register("blacklight_hunter_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.BLACKLIGHT_HUNTER, 0x242626, 0x9d302b, new Item.Properties()));

    public static final RegistryObject<com.codex.blacklight.item.HiveTissueItem> LIVING_HIVE_TISSUE=ITEMS.register("living_hive_tissue",()->new com.codex.blacklight.item.HiveTissueItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> HYDRA_SPAWN_EGG=ITEMS.register("blacklight_hydra_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKLIGHT_HYDRA,0x361b22,0xcba886,new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_FLESH = ITEMS.register("hive_flesh", () -> new BlockItem(ModBlocks.HIVE_FLESH.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_CARAPACE = ITEMS.register("hive_carapace", () -> new BlockItem(ModBlocks.HIVE_CARAPACE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_SAC = ITEMS.register("hive_sac", () -> new BlockItem(ModBlocks.HIVE_SAC.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> INFECTED_SOIL = ITEMS.register("infected_soil", () -> new BlockItem(ModBlocks.INFECTED_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> INFECTED_STONE = ITEMS.register("infected_stone", () -> new BlockItem(ModBlocks.INFECTED_STONE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> TISSUE_FIBER = ITEMS.register("tissue_fiber", () -> new BlockItem(ModBlocks.TISSUE_FIBER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_VEIN = ITEMS.register("hive_vein", () -> new BlockItem(ModBlocks.HIVE_VEIN.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_MEMBRANE = ITEMS.register("hive_membrane", () -> new BlockItem(ModBlocks.HIVE_MEMBRANE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HIVE_BONE = ITEMS.register("hive_bone", () -> new BlockItem(ModBlocks.HIVE_BONE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> NUTRIENT_NODE = ITEMS.register("nutrient_node", () -> new BlockItem(ModBlocks.NUTRIENT_NODE.get(), new Item.Properties()));

    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> BLACKWATCH_SOLDIER_SPAWN_EGG=ITEMS.register("blackwatch_soldier_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKWATCH_SOLDIER,0x20262a,0x8a2222,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> BLACKWATCH_HEAVY_SPAWN_EGG=ITEMS.register("blackwatch_heavy_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKWATCH_HEAVY,0x20262a,0xb0a285,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> MILITARY_SOLDIER_SPAWN_EGG=ITEMS.register("military_soldier_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MILITARY_SOLDIER,0x4c5942,0x9a966c,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> MILITARY_HEAVY_SPAWN_EGG=ITEMS.register("military_heavy_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MILITARY_HEAVY,0x4c5942,0x333f36,new Item.Properties()));
    public static final RegistryObject<Item> MILITARY_CIRCUIT=ITEMS.register("military_circuit",()->new Item(new Item.Properties()));
    public static final RegistryObject<com.codex.blacklight.item.FieldScannerItem> FIELD_SCANNER=ITEMS.register("field_scanner",()->new com.codex.blacklight.item.FieldScannerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> MILITARY_TEAM_LEADER_SPAWN_EGG=ITEMS.register("military_team_leader_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MILITARY_TEAM_LEADER,0x4c5942,0xb59a5e,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> MILITARY_OFFICER_SPAWN_EGG=ITEMS.register("military_officer_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MILITARY_OFFICER,0x4c5942,0xd3bd80,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> MILITARY_COMMANDER_SPAWN_EGG=ITEMS.register("military_commander_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MILITARY_COMMANDER,0x4c5942,0xe7d6a0,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> BLACKWATCH_TEAM_LEADER_SPAWN_EGG=ITEMS.register("blackwatch_team_leader_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKWATCH_TEAM_LEADER,0x20262a,0xb59a5e,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> BLACKWATCH_OFFICER_SPAWN_EGG=ITEMS.register("blackwatch_officer_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKWATCH_OFFICER,0x20262a,0xd3bd80,new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> BLACKWATCH_COMMANDER_SPAWN_EGG=ITEMS.register("blackwatch_commander_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.BLACKWATCH_COMMANDER,0x20262a,0xe7d6a0,new Item.Properties()));

    // Legacy registry IDs remain loadable; new crafting and creative access live in the independent mod.
    public static final RegistryObject<com.codex.blacklight.item.PassiveSerumItem> ADAPTATION_SERUM = ITEMS.register("adaptation_serum", () -> new com.codex.blacklight.item.PassiveSerumItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 1));
    public static final RegistryObject<com.codex.blacklight.item.PassiveSerumItem> TISSUE_REGENERATION_SERUM = ITEMS.register("tissue_regeneration_serum", () -> new com.codex.blacklight.item.PassiveSerumItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 2));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
            "blacklight", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blacklight"))
                    .icon(() -> BLACKLIGHT_SAMPLE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(BLACKWATCH_SOLDIER_SPAWN_EGG.get()); output.accept(BLACKWATCH_HEAVY_SPAWN_EGG.get()); output.accept(MILITARY_CIRCUIT.get()); output.accept(FIELD_SCANNER.get());
                        output.accept(MILITARY_SOLDIER_SPAWN_EGG.get());output.accept(MILITARY_HEAVY_SPAWN_EGG.get());
                        output.accept(MILITARY_TEAM_LEADER_SPAWN_EGG.get());
                        output.accept(MILITARY_OFFICER_SPAWN_EGG.get());
                        output.accept(MILITARY_COMMANDER_SPAWN_EGG.get());
                        output.accept(BLACKWATCH_TEAM_LEADER_SPAWN_EGG.get());
                        output.accept(BLACKWATCH_OFFICER_SPAWN_EGG.get());
                        output.accept(BLACKWATCH_COMMANDER_SPAWN_EGG.get());
                        output.accept(BLACKLIGHT_SAMPLE.get()); output.accept(HUNTER_SPAWN_EGG.get());
                        output.accept(LIVING_HIVE_TISSUE.get());output.accept(HYDRA_SPAWN_EGG.get());
                        output.accept(INFECTED_SOIL.get()); output.accept(INFECTED_STONE.get());
                        output.accept(TISSUE_FIBER.get()); output.accept(HIVE_FLESH.get());
                        output.accept(HIVE_CARAPACE.get()); output.accept(HIVE_VEIN.get());
                        output.accept(HIVE_MEMBRANE.get()); output.accept(HIVE_BONE.get());
                        output.accept(HIVE_SAC.get()); output.accept(NUTRIENT_NODE.get());
                    })
                    .build());


    private ModItems() {
    }

    public static void register(IEventBus bus) {
        com.codex.blacklight.firearms.FirearmsRegistry.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                net.minecraft.world.level.block.DispenserBlock.registerBehavior(BLACKLIGHT_SAMPLE.get(),
                        new net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior() {
                            @Override protected net.minecraft.world.entity.projectile.Projectile getProjectile(
                                    net.minecraft.world.level.Level level,net.minecraft.core.Position position,net.minecraft.world.item.ItemStack stack) {
                                var vial=new com.codex.blacklight.entity.BlacklightVirusVialEntity(level,position.x(),position.y(),position.z());
                                vial.setItem(stack);
                                return vial;
                            }
                        })));
    }
}
