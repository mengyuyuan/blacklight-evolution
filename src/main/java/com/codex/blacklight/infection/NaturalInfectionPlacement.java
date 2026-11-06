package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;

/** Natural growth uses the same cancellable placement boundary as solid biomass. */
public final class NaturalInfectionPlacement {
    private static final GameProfile GROWTH_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("blacklight:natural_growth".getBytes(StandardCharsets.UTF_8)), "[Blacklight]");
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
        boolean permitted = permits(level, snapshot, next);
        if (!level.getBlockState(pos).equals(next)) return false;
        if (!permitted || next.is(ModBlocks.BUILDING_CORE.get()) && !BuildingInfectionSystem.register(level, pos)) {
            level.setBlock(pos, before, 3);
            return false;
        }
        return true;
    }

    /** A dedicated, non-operator actor lets claim mods check growth without a null owner. */
    static boolean permits(ServerLevel level, BlockSnapshot snapshot, BlockState next) {
        // Never borrow a nearby player or the shared [Minecraft] identity: growth
        // must not inherit an owner's/operator's permission to alter protected land.
        // The loader owns the cache and clears it when the level unloads.
        var actor = FakePlayerFactory.get(level, GROWTH_PROFILE);
        var previousPosition = actor.position();
        actor.setPos(snapshot.getPos().getX() + .5, snapshot.getPos().getY(), snapshot.getPos().getZ() + .5);
        try {
            var event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(snapshot.getPos().below()), actor) {
                @Override public BlockState getPlacedBlock() { return next; }
                @Override public BlockState getState() { return next; }
            };
            MinecraftForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        } finally {
            // A synchronous listener can recursively place another natural cell.
            actor.setPos(previousPosition);
        }
    }
}
