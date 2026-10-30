package com.codex.blacklight.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

/** Physical infection terrain and Hive tissue; nutrient nodes can be severed by mining. */
public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS,"blacklight");
    public static final RegistryObject<Block> HIVE_FLESH=BLOCKS.register("hive_flesh",()->new com.codex.blacklight.world.TissueSourceBlock(BlockBehaviour.Properties.of().strength(3,6).sound(SoundType.SLIME_BLOCK)));
    public static final RegistryObject<Block> HIVE_CARAPACE=BLOCKS.register("hive_carapace",()->new Block(BlockBehaviour.Properties.of().strength(5,12).sound(SoundType.NETHER_WOOD)));
    public static final RegistryObject<Block> HIVE_SAC=BLOCKS.register("hive_sac",()->new Block(BlockBehaviour.Properties.of().strength(2,4).sound(SoundType.SLIME_BLOCK).lightLevel(s->5)));
    public static final RegistryObject<Block> INFECTED_SOIL=BLOCKS.register("infected_soil",()->new com.codex.blacklight.world.TissueSourceBlock(BlockBehaviour.Properties.of().strength(.8F,1).sound(SoundType.ROOTED_DIRT)));
    public static final RegistryObject<Block> INFECTED_STONE=BLOCKS.register("infected_stone",()->new com.codex.blacklight.world.TissueSourceBlock(BlockBehaviour.Properties.of().strength(2,6).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> TISSUE_FIBER=BLOCKS.register("tissue_fiber",()->new com.codex.blacklight.world.TissueSourceBlock(BlockBehaviour.Properties.of().strength(1.5F,3).sound(SoundType.NETHER_WART)));
    public static final RegistryObject<Block> HIVE_VEIN=BLOCKS.register("hive_vein",()->new com.codex.blacklight.world.TissueSourceBlock(BlockBehaviour.Properties.of().strength(1.8F,3).sound(SoundType.SLIME_BLOCK)));
    public static final RegistryObject<Block> HIVE_MEMBRANE=BLOCKS.register("hive_membrane",()->new Block(BlockBehaviour.Properties.of().strength(.7F,1.5F).sound(SoundType.SLIME_BLOCK)));
    public static final RegistryObject<Block> HIVE_BONE=BLOCKS.register("hive_bone",()->new Block(BlockBehaviour.Properties.of().strength(3.5F,8).sound(SoundType.BONE_BLOCK).requiresCorrectToolForDrops()));
    // Breaking/removing this block immediately interrupts the structure's physical feed line.
    public static final RegistryObject<Block> NUTRIENT_NODE=BLOCKS.register("nutrient_node",()->new Block(BlockBehaviour.Properties.of().strength(2.5F,5).sound(SoundType.SCULK).lightLevel(s->4)));
    public static final RegistryObject<Block> BUILDING_BIOMASS=BLOCKS.register("building_biomass",()->new com.codex.blacklight.world.BuildingBiomassBlock(BlockBehaviour.Properties.of().strength(3F,6F).sound(SoundType.SLIME_BLOCK).noOcclusion().noLootTable()));
    public static final RegistryObject<Block> BUILDING_CORE=BLOCKS.register("building_infection_core",()->new com.codex.blacklight.world.BuildingInfectionCoreBlock(BlockBehaviour.Properties.of().strength(5,8).sound(SoundType.SLIME_BLOCK).noOcclusion()));
    public static final RegistryObject<Block> TISSUE_ATTACHMENT=BLOCKS.register("tissue_attachment",()->new com.codex.blacklight.world.TissueAttachmentBlock(BlockBehaviour.Properties.of().strength(.6F).sound(SoundType.SLIME_BLOCK).noCollission().noOcclusion().randomTicks()));
    public static void register(IEventBus bus){BLOCKS.register(bus);}
}
