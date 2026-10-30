package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.item.BlacklightSampleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlacklightNeoMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlacklightNeoMod.MOD_ID);

    public static final DeferredItem<BlacklightSampleItem> BLACKLIGHT_SAMPLE = ITEMS.registerItem(
            "blacklight_sample", BlacklightSampleItem::new,
            new Item.Properties().stacksTo(4).rarity(Rarity.EPIC).fireResistant());

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> HUNTER_SPAWN_EGG = ITEMS.registerItem(
            "blacklight_hunter_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.BLACKLIGHT_HUNTER, 0x242626, 0x9d302b, props), new Item.Properties());

    public static final DeferredItem<com.codex.blacklight.item.HiveTissueItem> LIVING_HIVE_TISSUE = ITEMS.registerItem("living_hive_tissue",com.codex.blacklight.item.HiveTissueItem::new,new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> HYDRA_SPAWN_EGG = ITEMS.registerItem("blacklight_hydra_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKLIGHT_HYDRA,0x361b22,0xcba886,p),new Item.Properties());
    public static final DeferredItem<BlockItem> TISSUE_ATTACHMENT = ITEMS.registerSimpleBlockItem(ModBlocks.TISSUE_ATTACHMENT);
    public static final DeferredItem<BlockItem> BUILDING_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.BUILDING_CORE);
    public static final DeferredItem<BlockItem> HIVE_FLESH = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_FLESH);
    public static final DeferredItem<BlockItem> HIVE_CARAPACE = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_CARAPACE);
    public static final DeferredItem<BlockItem> HIVE_SAC = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_SAC);
    public static final DeferredItem<BlockItem> INFECTED_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.INFECTED_SOIL);
    public static final DeferredItem<BlockItem> INFECTED_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.INFECTED_STONE);
    public static final DeferredItem<BlockItem> TISSUE_FIBER = ITEMS.registerSimpleBlockItem(ModBlocks.TISSUE_FIBER);
    public static final DeferredItem<BlockItem> HIVE_VEIN = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_VEIN);
    public static final DeferredItem<BlockItem> HIVE_MEMBRANE = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_MEMBRANE);
    public static final DeferredItem<BlockItem> HIVE_BONE = ITEMS.registerSimpleBlockItem(ModBlocks.HIVE_BONE);
    public static final DeferredItem<BlockItem> NUTRIENT_NODE = ITEMS.registerSimpleBlockItem(ModBlocks.NUTRIENT_NODE);

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BLACKWATCH_SOLDIER_SPAWN_EGG=ITEMS.registerItem("blackwatch_soldier_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKWATCH_SOLDIER,0x20262a,0x8a2222,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BLACKWATCH_HEAVY_SPAWN_EGG=ITEMS.registerItem("blackwatch_heavy_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKWATCH_HEAVY,0x20262a,0xb0a285,p),new Item.Properties());
    public static final DeferredItem<Item> MILITARY_CIRCUIT=ITEMS.registerItem("military_circuit",Item::new,new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MILITARY_SOLDIER_SPAWN_EGG=ITEMS.registerItem("military_soldier_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MILITARY_SOLDIER,0x4c5942,0x9a966c,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MILITARY_HEAVY_SPAWN_EGG=ITEMS.registerItem("military_heavy_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MILITARY_HEAVY,0x4c5942,0x333f36,p),new Item.Properties());
    public static final DeferredItem<com.codex.blacklight.item.FieldScannerItem> FIELD_SCANNER=ITEMS.registerItem("field_scanner",com.codex.blacklight.item.FieldScannerItem::new,new Item.Properties().stacksTo(1));

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MILITARY_TEAM_LEADER_SPAWN_EGG=ITEMS.registerItem("military_team_leader_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MILITARY_TEAM_LEADER,0x4c5942,0xb59a5e,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MILITARY_OFFICER_SPAWN_EGG=ITEMS.registerItem("military_officer_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MILITARY_OFFICER,0x4c5942,0xd3bd80,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MILITARY_COMMANDER_SPAWN_EGG=ITEMS.registerItem("military_commander_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.MILITARY_COMMANDER,0x4c5942,0xe7d6a0,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BLACKWATCH_TEAM_LEADER_SPAWN_EGG=ITEMS.registerItem("blackwatch_team_leader_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKWATCH_TEAM_LEADER,0x20262a,0xb59a5e,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BLACKWATCH_OFFICER_SPAWN_EGG=ITEMS.registerItem("blackwatch_officer_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKWATCH_OFFICER,0x20262a,0xd3bd80,p),new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> BLACKWATCH_COMMANDER_SPAWN_EGG=ITEMS.registerItem("blackwatch_commander_spawn_egg",p->new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BLACKWATCH_COMMANDER,0x20262a,0xe7d6a0,p),new Item.Properties());

    // Legacy registry IDs remain loadable; new crafting and creative access live in the independent mod.
    public static final DeferredItem<com.codex.blacklight.item.PassiveSerumItem> ADAPTATION_SERUM = ITEMS.registerItem("adaptation_serum", props -> new com.codex.blacklight.item.PassiveSerumItem(props, 1), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<com.codex.blacklight.item.PassiveSerumItem> TISSUE_REGENERATION_SERUM = ITEMS.registerItem("tissue_regeneration_serum", props -> new com.codex.blacklight.item.PassiveSerumItem(props, 2), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
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
                        output.accept(TISSUE_ATTACHMENT.get()); output.accept(TISSUE_FIBER.get()); output.accept(HIVE_FLESH.get());
                        output.accept(BUILDING_CORE.get());
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
    }
}
