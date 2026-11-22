package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Clears space for the rooted spider before the brood arrives. */
final class GreeneMoltShockwave {
    static final double RADIUS = 26;
    private static final int TRAVEL_TICKS = 16;
    private final Set<UUID> affected = new HashSet<>();

    void begin(EncounterMob boss, ServerLevel level) {
        affected.clear();
        for (ServerPlayer player : level.players()) {
            double height = player.getY() - boss.getY();
            if (!player.isAlive() || player.isSpectator() || height < -5 || height > 46) continue;
            if (player.position().subtract(boss.position()).horizontalDistance() >= RADIUS) continue;
            player.stopRiding();
            affected.add(player.getUUID());
        }
    }

    void tick(EncounterMob boss, ServerLevel level, int age) {
        if (age > TRAVEL_TICKS) return;
        if (age % 2 == 0 && age <= 12) {
            double ring = 2 + age * 2;
            for (int i = 0; i < 28; i++) {
                double angle = i * (Math.PI * 2 / 28);
                level.sendParticles(ParticleTypes.CLOUD, boss.getX() + ring * Math.cos(angle),
                        boss.getY() + .55, boss.getZ() + ring * Math.sin(angle), 1, .35, .10, .35, .04);
            }
        }
        affected.removeIf(id -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) return true;
            Vec3 offset = player.position().subtract(boss.position());
            double distance = offset.horizontalDistance();
            if (distance >= RADIUS + 1) return true;
            double angle = distance > .01 ? Math.atan2(offset.z, offset.x) : Math.toRadians(player.getYRot() + 90);
            if (age == TRAVEL_TICKS) {
                if (distance < RADIUS) landOutside(boss, level, player, angle);
                return true;
            }
            double speed = Math.min(2.45, (RADIUS + 1 - distance) / Math.max(1, 11 - age));
            double lift = age == 0 ? .48 : player.getDeltaMovement().y;
            player.setDeltaMovement(Math.cos(angle) * speed, lift, Math.sin(angle) * speed);
            player.hurtMarked = true;
            player.fallDistance = 0;
            return false;
        });
    }

    private static void landOutside(EncounterMob boss, ServerLevel level, ServerPlayer player, double direction) {
        // If street furniture stopped the impulse, find a supported, open landing outside the ring.
        Vec3 best = null;
        double score = Double.MAX_VALUE;
        for (int i = 0; i < 24; i++) {
            double angle = direction + ((i + 1) / 2) * (i % 2 == 0 ? -1 : 1) * Math.PI / 12;
            double x = boss.getX() + (RADIUS + 2) * Math.cos(angle);
            double z = boss.getZ() + (RADIUS + 2) * Math.sin(angle);
            var from=new Vec3(x,boss.getY()+9,z);
            var ray=level.clip(new net.minecraft.world.level.ClipContext(from,from.add(0,-19,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
            if(ray.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)continue;
            Vec3 target=ray.getLocation().add(0,.012,0);
            BlockPos ground=BlockPos.containing(target);
            if (!level.getWorldBorder().isWithinBounds(ground) || !level.getBlockState(ground.below()).blocksMotion()
                    || !level.getFluidState(ground.below()).isEmpty()
                    || !level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) continue;
            double cost = Math.abs(target.y - boss.getY()) + i * 1.5;
            if (cost < score) { score = cost; best = target; }
        }
        if (best != null) {
            player.connection.teleport(best.x, best.y, best.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.fallDistance = 0;
        }
    }

    void save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (UUID id : affected) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            players.add(entry);
        }
        tag.put("ShockwavePlayers", players);
    }

    void load(CompoundTag tag) {
        affected.clear();
        ListTag players = tag.getList("ShockwavePlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("Player")) affected.add(entry.getUUID("Player"));
        }
    }
}
