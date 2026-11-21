package com.codex.blacklight.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** An injected hive may disappear before the delayed emergence can run. Its dimension owns the outstanding spawn. */
final class SupremeHunterEmergence extends SavedData {
    private record Pending(UUID boss, UUID operator, Vec3 position, long due) {}
    private final List<Pending> pending = new ArrayList<>();

    static SupremeHunterEmergence get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SupremeHunterEmergence::load, SupremeHunterEmergence::new, "blacklight_hunter_emergence");
    }

    void schedule(Vec3 position, UUID operator, long due) {
        pending.add(new Pending(UUID.randomUUID(), operator, position, due));
        setDirty();
    }

    void tick(ServerLevel level) {
        if (pending.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) return;
        long now = level.getGameTime();
        // Spawn callbacks can schedule another release. Only process requests that existed this tick.
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending request = pending.get(i);
            if (now < request.due() || !level.isPositionEntityTicking(BlockPos.containing(request.position()))) continue;
            var player = level.getServer().getPlayerList().getPlayer(request.operator());
            if (level.getEntity(request.boss()) instanceof BlacklightSupremeHunter
                    || SupremeHunterRewards.summon(level, request.position(), player, request.boss()) != null) {
                pending.remove(i);
            } else {
                pending.set(i, new Pending(request.boss(), request.operator(), request.position(), now + 100));
            }
            setDirty();
        }
    }

    static SupremeHunterEmergence load(CompoundTag tag) {
        var data = new SupremeHunterEmergence();
        for (var element : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            data.pending.add(new Pending(entry.getUUID("Boss"), entry.getUUID("Operator"),
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")), entry.getLong("Due")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        for (Pending request : pending) {
            var entry = new CompoundTag();
            entry.putUUID("Boss", request.boss());
            entry.putUUID("Operator", request.operator());
            entry.putDouble("X", request.position().x);
            entry.putDouble("Y", request.position().y);
            entry.putDouble("Z", request.position().z);
            entry.putLong("Due", request.due());
            list.add(entry);
        }
        tag.put("Pending", list);
        return tag;
    }
}
