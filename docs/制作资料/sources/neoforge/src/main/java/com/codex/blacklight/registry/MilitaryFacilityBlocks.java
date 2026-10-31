package com.codex.blacklight.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.bus.api.IEventBus;

/** Original static scenery. Crates have NO inventory; use vanilla chests for supplies.
 * Terminals have no Blacknet/quest behavior. Searchlights emit light, not a beam AI.
 */
public final class MilitaryFacilityBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("blacklight");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "blacklight");
        public static final DeferredBlock<Block> FIELD_BARRIER = prop("field_barrier", 0, Block.box(0, 0, 3, 16, 14, 13));
    public static final DeferredBlock<Block> SANDBAG_COVER = prop("sandbag_cover", 0, Block.box(0, 0, 3, 16, 9, 13));
    public static final DeferredBlock<Block> MILITARY_CRATE = prop("military_crate", 0, Block.box(0, 0, 0, 16, 15, 16));
    public static final DeferredBlock<Block> COMMAND_TERMINAL = prop("command_terminal", 4, Block.box(0, 0, 1, 16, 16, 15));
    public static final DeferredBlock<Block> COMMUNICATIONS_ANTENNA = prop("communications_antenna", 0, Block.box(6, 0, 6, 10, 16, 10));
    public static final DeferredBlock<Block> MILITARY_SEARCHLIGHT = prop("military_searchlight", 12, Block.box(2, 0, 3, 14, 16, 13));

    static {
        TABS.register("military_facilities", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.blacklight.military_facilities"))
                .icon(() -> new ItemStack(FIELD_BARRIER.get()))
                .displayItems((parameters, output) -> {
                    output.accept(FIELD_BARRIER.get()); output.accept(SANDBAG_COVER.get());
                    output.accept(MILITARY_CRATE.get()); output.accept(COMMAND_TERMINAL.get());
                    output.accept(COMMUNICATIONS_ANTENNA.get()); output.accept(MILITARY_SEARCHLIGHT.get());
                }).build());
    }

    private static DeferredBlock<Block> prop(String name, int light, VoxelShape shape) {
        DeferredBlock<Block> block = BLOCKS.register(name, () -> new FacilityBlock(BlockBehaviour.Properties.of().strength(2.5F, 6).sound(SoundType.METAL).noOcclusion().lightLevel(s -> light), shape));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); }
    private MilitaryFacilityBlocks() {}

    private static final class FacilityBlock extends HorizontalDirectionalBlock {
        private static final MapCodec<FacilityBlock> CODEC = simpleCodec(p -> new FacilityBlock(p, Block.box(0, 0, 0, 16, 16, 16)));
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        private final VoxelShape northShape;
        private final VoxelShape sideShape;
        FacilityBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
            super(properties);
            this.northShape = shape;
            var box = shape.bounds();
            this.sideShape = Block.box(box.minZ * 16, box.minY * 16, box.minX * 16,
                                      box.maxZ * 16, box.maxY * 16, box.maxX * 16);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
        @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
        @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? sideShape : northShape;
        }
    }
}
