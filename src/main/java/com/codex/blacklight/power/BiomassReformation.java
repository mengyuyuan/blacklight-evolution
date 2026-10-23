package com.codex.blacklight.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Capture reachable space before a prey's death hook can build a corpse mound. */
public final class BiomassReformation {
    private static final String KEY = "blacklight:reformation_space";
    private BiomassReformation() {}

    public static void clear(ServerPlayer player) { player.getPersistentData().remove(KEY); }

    public static void prepare(ServerPlayer player) {
        clear(player);
        List<Vec3> spaces = new ArrayList<>();
        Vec3 origin = player.position();
        if (fits(player, origin)) spaces.add(origin);
        // Bounded, loaded-only sweep. Every escape path must be clear BEFORE the kill,
        // so a pre-existing wall cannot become a shortcut through a sealed room.
        for (int radius = 1; radius <= 6; radius++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4;
                for (int dy = 0; dy <= 3; dy++) {
                    Vec3 at = origin.add(Math.cos(angle)*radius, dy, Math.sin(angle)*radius);
                    AABB path = player.getBoundingBox().expandTowards(at.subtract(origin));
                    if (loaded(player, path) && !player.level().getBlockCollisions(player, path).iterator().hasNext()
                            && fits(player, at) && !player.level().noCollision(player, body(at).move(0, -.1, 0)))
                        spaces.add(at);
                }
            }
        }
        spaces.sort(Comparator.comparingDouble(origin::distanceToSqr));
        CompoundTag data = new CompoundTag();
        data.putString("dimension", player.level().dimension().location().toString());
        ListTag points = new ListTag();
        // Retain far candidates too: a large corpse can fill all the nearest spaces.
        for (Vec3 at : spaces) {
            CompoundTag point = new CompoundTag();
            point.putDouble("x", at.x); point.putDouble("y", at.y); point.putDouble("z", at.z);
            points.add(point);
        }
        data.put("points", points);
        player.getPersistentData().put(KEY, data);
    }

    public static Vec3 completionPosition(ServerPlayer player, Vec3 prey) {
        if (fits(player, prey)) return prey;
        if (fits(player, player.position())) return player.position();
        Vec3 safe = savedSpace(player);
        // Never teleport into the newly created block if no valid destination survives.
        return safe == null ? player.position() : safe;
    }

    public static Vec3 standingRoom(ServerPlayer player) {
        if (fits(player, player.position())) return player.position();
        // A low ceiling alone still requires crawling out. Rescue only actual embedding.
        if (!player.level().getBlockCollisions(player, player.getBoundingBox()).iterator().hasNext()) return null;
        return savedSpace(player);
    }

    private static Vec3 savedSpace(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(KEY);
        if (!data.getString("dimension").equals(player.level().dimension().location().toString())) return null;
        ListTag points = data.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(points.size(), 193); i++) {
            CompoundTag point = points.getCompound(i);
            Vec3 at = new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z"));
            if (Double.isFinite(at.x) && Double.isFinite(at.y) && Double.isFinite(at.z)
                    && at.distanceToSqr(player.position()) <= 81 && fits(player, at)) return at;
        }
        return null;
    }

    private static AABB body(Vec3 at) { return new AABB(at.x-.3,at.y,at.z-.3,at.x+.3,at.y+1.8,at.z+.3); }
    private static boolean loaded(ServerPlayer player, AABB box) {
        return box.minY >= player.level().getMinBuildHeight() && box.maxY < player.level().getMaxBuildHeight()
                && player.level().hasChunksAt(BlockPos.containing(box.minX,box.minY,box.minZ),
                    BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                && player.level().getWorldBorder().isWithinBounds(box);
    }
    private static boolean fits(ServerPlayer player, Vec3 at) {
        AABB box = body(at);
        return loaded(player, box) && !player.level().containsAnyLiquid(box) && player.level().noCollision(player, box);
    }
}
