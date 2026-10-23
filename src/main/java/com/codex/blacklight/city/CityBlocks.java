package com.codex.blacklight.city;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CityBlocks {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("blacklight");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"blacklight");
    private static BlockBehaviour.Properties flesh(float strength){return BlockBehaviour.Properties.of().strength(strength,4).sound(SoundType.SLIME_BLOCK);}
    public static final DeferredBlock<Block> FLESH=BLOCKS.register("city_flesh",()->new com.codex.blacklight.world.TissueSourceBlock(flesh(2)));
    public static final DeferredBlock<RotatedPillarBlock> FIBER=BLOCKS.registerBlock("city_fiber",RotatedPillarBlock::new,flesh(2));
    public static final DeferredBlock<RotatedPillarBlock> VEIN=BLOCKS.registerBlock("city_vein",RotatedPillarBlock::new,flesh(2).lightLevel(s->1));
    public static final DeferredBlock<Block> SHELL=BLOCKS.registerSimpleBlock("city_shell",flesh(4));
    public static final DeferredBlock<Block> WOUNDED=BLOCKS.registerSimpleBlock("city_wounded",flesh(1));
    public static final DeferredBlock<SlabBlock> LAYER=BLOCKS.registerBlock("city_tissue_layer",SlabBlock::new,flesh(1));
    public static final DeferredBlock<CityHatchBlock> HATCH=BLOCKS.registerBlock("city_hatch",CityHatchBlock::new,flesh(5));
    public static final DeferredBlock<Block> SAC=BLOCKS.registerSimpleBlock("city_sac",flesh(2).lightLevel(s->2));
    public static final DeferredItem<CityTransitItem> TRANSIT=ITEMS.registerItem("city_transit",CityTransitItem::new,new Item.Properties().stacksTo(1));
    static {
        for(var block:java.util.List.of(FLESH,FIBER,VEIN,SHELL,WOUNDED,LAYER,HATCH,SAC))ITEMS.registerSimpleBlockItem(block);
        TABS.register("city",()->CreativeModeTab.builder().title(Component.translatable("city.blacklight.tab"))
            .icon(()->FLESH.get().asItem().getDefaultInstance()).displayItems((p,out)->{
                out.accept(TRANSIT.get());for(var block:java.util.List.of(FLESH,FIBER,VEIN,SHELL,WOUNDED,LAYER,HATCH,SAC))out.accept(block.get());
            }).build());
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
