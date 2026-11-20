package com.codex.blacklight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Hinged encounter props retain their identity after impact, even across save/load. */
public final class ConvoyRail extends Block {
    public static final BooleanProperty BENT=BooleanProperty.create("bent");
    public ConvoyRail(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(BENT,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> states) {
        states.add(BENT);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BENT)?Shapes.empty():Shapes.block();
    }
    public static void bend(ServerLevel level, BlockPos at) {
        var state=level.getBlockState(at);
        if (!(state.getBlock() instanceof ConvoyRail) || state.getValue(BENT)) return;
        level.levelEvent(2001,at,Block.getId(state));
        level.setBlock(at,state.setValue(BENT,true),3);
    }
    public static void crash(ServerLevel level,AABB contact) {
        for(var at:BlockPos.betweenClosed(BlockPos.containing(contact.minX,contact.minY,contact.minZ),
                BlockPos.containing(contact.maxX,contact.minY+4,contact.maxZ))) {
            if (level.hasChunkAt(at)) bend(level,at);
        }
    }
    static void reset(ServerLevel level,BlockPos console,net.minecraft.core.Direction facing) {
        // Match the three stamped roadblocks and the gate. Leave any replacement material untouched.
        for(int z:new int[]{4,21,25}) for(int x=24;x<=28;x++) for(int y=1;y<=4;y++)
            resetAt(level,relative(console,facing,x,y,z));
        for(int x=12;x<=20;x++) for(int y=1;y<=4;y++) resetAt(level,relative(console,facing,x,y,21));
    }
    static BlockPos relative(BlockPos console,net.minecraft.core.Direction facing,int x,int y,int z) {
        return console.relative(facing.getCounterClockWise(),x-16).relative(facing,z-28).above(y-1);
    }
    private static void resetAt(ServerLevel level,BlockPos at) {
        var state=level.getBlockState(at);
        if(state.getBlock() instanceof ConvoyRail && state.getValue(BENT)
                && level.getEntities(null,new AABB(at)).isEmpty()) level.setBlock(at,state.setValue(BENT,false),3);
    }
}
