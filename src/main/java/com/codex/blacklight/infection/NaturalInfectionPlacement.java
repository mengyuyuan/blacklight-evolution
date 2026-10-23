package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Natural growth uses the same cancellable placement boundary as solid biomass. */
public final class NaturalInfectionPlacement {
    private record Placement(ServerLevel level, BlockPos pos) {}
    private static final ThreadLocal<Placement> PLACING = new ThreadLocal<>();
    private NaturalInfectionPlacement() {}

    public static boolean defersRegistration(ServerLevel level, BlockPos pos) {
        var placing = PLACING.get();
        return placing != null && placing.level() == level && placing.pos().equals(pos);
    }

    public static boolean place(ServerLevel level, BlockPos pos, BlockState next) {
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var before = level.getBlockState(pos);
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        var previous = PLACING.get();
        PLACING.set(new Placement(level, pos.immutable()));
        boolean changed;
        try { changed = level.setBlock(pos, next, 3); }
        finally { if (previous == null) PLACING.remove(); else PLACING.set(previous); }
        if (!changed || !level.getBlockState(pos).equals(next)) return false;
        var event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), null) {
            @Override public BlockState getPlacedBlock() { return next; }
            @Override public BlockState getState() { return next; }
        };
        NeoForge.EVENT_BUS.post(event);
        if (!level.getBlockState(pos).equals(next)) return false;
        if (event.isCanceled() || next.is(ModBlocks.BUILDING_CORE.get()) && !BuildingInfectionSystem.register(level, pos)) {
            level.setBlock(pos, before, 3);
            return false;
        }
        return true;
    }
}
