package com.codex.blacklight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Inspect the damaged gate in a quarantine post or the dispatch log at the city checkpoint. */
public final class FieldPostBlock extends HorizontalDirectionalBlock {
    private static final BooleanProperty SEARCHED = BooleanProperty.create("searched");
    public FieldPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(SEARCHED, false));
    }
    private static final com.mojang.serialization.MapCodec<FieldPostBlock> CODEC = simpleCodec(FieldPostBlock::new);
    @Override protected com.mojang.serialization.MapCodec<FieldPostBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEARCHED);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer reader && level instanceof ServerLevel server && !reader.isSpectator()) {
            if (level.dimension() == com.codex.blacklight.city.CityTravel.WORLD)
                FutianDispatch.read(server, pos, reader);
            else if (level.dimension() == Level.OVERWORLD)
                investigate(server, pos, state, reader);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private void investigate(ServerLevel level, BlockPos gate, BlockState state, ServerPlayer player) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            FieldNotes.record(player, Component.translatable("survival.blacklight.peaceful"));
            return;
        }
        if (state.getValue(SEARCHED) || !level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightInfectedHost.class,
                new AABB(gate).inflate(64), mob -> mob.getPersistentData().contains("QuarantineGate")
                && mob.getPersistentData().getLong("QuarantineGate") == gate.asLong()).isEmpty()) {
            FieldNotes.record(player, Component.translatable("survival.blacklight.trail_cold"));
            return;
        }
        var direction = state.getValue(FACING);
        for (int distance : new int[]{11, 9, 7}) {
            BlockPos end = gate.relative(direction, distance);
            if (!level.hasChunkAt(end)) continue;
            if (!level.getBlockState(end.below()).isFaceSturdy(level,end.below(),Direction.UP) || !level.getBlockState(end).isAir())
                end = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, end);
            if (!level.getFluidState(end.below()).isEmpty()) continue;
            var host = com.codex.blacklight.infection.EvolvedSamplePreset.matureHost(level,
                    com.codex.blacklight.infection.EvolvedArchetype.PREDATOR);
            if (host == null) return;
            host.moveTo(end.getX()+.5, end.getY(), end.getZ()+.5, direction.toYRot(), 0);
            if (!level.noCollision(host, host.getBoundingBox()) || !host.beginEvolvedMetamorphosis()) continue;
            host.getPersistentData().putLong("QuarantineGate", gate.asLong());
            if (!level.addFreshEntity(host)) continue;
            level.setBlock(gate, state.setValue(SEARCHED, true), 3);
            level.scheduleTick(gate, this, 6000);
            level.playSound(null, end, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2F, .65F);
            FieldNotes.record(player, Component.translatable("survival.blacklight.trail_warm"));
            return;
        }
        FieldNotes.record(player, Component.translatable("survival.blacklight.trail_blocked"));
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(SEARCHED)) level.setBlock(pos, state.setValue(SEARCHED, false), 3);
    }
}
