package com.codex.blacklight.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** Surface tissue: support direction, growth and variation travel in ordinary block-state packets. */
public final class TissueAttachmentBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    public TissueAttachmentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(AGE, 0).setValue(VARIANT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE, VARIANT);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = state.getValue(FACING);
        BlockPos neighbor = pos.relative(support);
        return level.hasChunkAt(neighbor) && level.getBlockState(neighbor).isFaceSturdy(level, neighbor, support.getOpposite());
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState adjacent, LevelAccessor level, BlockPos pos, BlockPos neighbor) {
        return side == state.getValue(FACING) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, side, adjacent, level, pos, neighbor);
    }
    @Override public boolean isRandomlyTicking(BlockState state) { return state.getValue(AGE) < 3; }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) { level.removeBlock(pos, false); return; }
        if (state.getValue(AGE) < 3 && random.nextInt(3) == 0) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 3);
            if(state.getValue(AGE)==2 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))
                com.codex.blacklight.infection.BuildingInfectionSystem.considerNaturalCore(level,pos,random);
        }
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double maximum = switch(state.getValue(VARIANT)) { case 0 -> 2; case 1 -> 6; default -> 12; };
        double depth = Math.max(1,maximum*switch(state.getValue(AGE)) { case 0 -> .08; case 1 -> .24; case 2 -> .55; default -> 1; });
        return switch (state.getValue(FACING)) {
            case DOWN -> box(0, 0, 0, 16, depth, 16);
            case UP -> box(0, 16-depth, 0, 16, 16, 16);
            case NORTH -> box(0, 0, 0, 16, 16, depth);
            case SOUTH -> box(0, 0, 16-depth, 16, 16, 16);
            case WEST -> box(0, 0, 0, depth, 16, 16);
            case EAST -> box(16-depth, 0, 0, 16, 16, 16);
        };
    }
}
