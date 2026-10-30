package com.codex.blacklight.city;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class CityBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS,"blacklight");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS,"blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"blacklight");
    private static BlockBehaviour.Properties flesh(float strength){return BlockBehaviour.Properties.of().strength(strength,4).sound(SoundType.SLIME_BLOCK);}
    public static final RegistryObject<Block> FLESH=BLOCKS.register("city_flesh",()->new Block(flesh(2)));
    public static final RegistryObject<RotatedPillarBlock> FIBER=BLOCKS.register("city_fiber",()->new RotatedPillarBlock(flesh(2)));
    public static final RegistryObject<RotatedPillarBlock> VEIN=BLOCKS.register("city_vein",()->new RotatedPillarBlock(flesh(2).lightLevel(s->1)));
    public static final RegistryObject<Block> SHELL=BLOCKS.register("city_shell",()->new Block(flesh(4)));
    public static final RegistryObject<Block> WOUNDED=BLOCKS.register("city_wounded",()->new Block(flesh(1)));
    public static final RegistryObject<SlabBlock> LAYER=BLOCKS.register("city_tissue_layer",()->new SlabBlock(flesh(1)));
    public static final RegistryObject<CityHatchBlock> HATCH=BLOCKS.register("city_hatch",()->new CityHatchBlock(flesh(5)));
    public static final RegistryObject<Block> SAC=BLOCKS.register("city_sac",()->new Block(flesh(2).lightLevel(s->2)) {
        @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.BlockGetter level,java.util.List<Component> tooltip,TooltipFlag flag) {
            super.appendHoverText(stack,level,tooltip,flag);
            tooltip.add(Component.translatable("city.blacklight.sac_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });
    public static final RegistryObject<CityTransitItem> TRANSIT=ITEMS.register("city_transit",()->new CityTransitItem(new Item.Properties().stacksTo(1)));
    static {
        for(var block:java.util.List.of(FLESH,FIBER,VEIN,SHELL,WOUNDED,LAYER,HATCH,SAC))ITEMS.register(block.getId().getPath(),()->new BlockItem(block.get(),new Item.Properties()));
        TABS.register("city",()->CreativeModeTab.builder().title(Component.translatable("city.blacklight.tab"))
            .icon(()->FLESH.get().asItem().getDefaultInstance()).displayItems((p,out)->{
                out.accept(TRANSIT.get());for(var block:java.util.List.of(FLESH,FIBER,VEIN,SHELL,WOUNDED,LAYER,HATCH,SAC))out.accept(block.get());
            }).build());
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
