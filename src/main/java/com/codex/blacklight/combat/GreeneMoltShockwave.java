package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The first displacement clears the player; the roar then rolls through the cave floor and brood. */
final class GreeneMoltShockwave {
    static final double RADIUS = 26;
    static final int ROAR_TICK = 16;
    private static final int ROAR_BANDS = 6;
    private static final double BAND_WIDTH = 9;
    private static final ImpactProfile FLINCH = new ImpactProfile(HitReaction.STAGGER, ImpactProfile.BODY, 8, 0, 0, false);
    private static final int TRAVEL_TICKS = 16;
    private final Set<UUID> affected = new HashSet<>();
    private final Set<UUID> recoiled = new HashSet<>();

    void begin(EncounterMob boss, ServerLevel level) {
        affected.clear();
        recoiled.clear();
        for (ServerPlayer player : level.players()) {
            double height = player.getY() - boss.getY();
            if (!player.isAlive() || player.isSpectator() || height < -5 || height > 46) continue;
            if (player.position().subtract(boss.position()).horizontalDistance() >= RADIUS) continue;
            player.stopRiding();
            affected.add(player.getUUID());
        }
    }

    void tick(EncounterMob boss, ServerLevel level, int age) {
        int elapsed = age - ROAR_TICK;
        if (elapsed >= 0 && elapsed < ROAR_BANDS * 3 && elapsed % 3 == 0) {
            roarBand(boss, level, elapsed / 3);
        }
        if (age > TRAVEL_TICKS) return;
        if (age % 4 == 0 && age <= 12) {
            double ring = 2 + age * 2;
            for (int i = 0; i < 16; i++) {
                double angle = i * (Math.PI * 2 / 16);
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

    private void roarBand(EncounterMob boss, ServerLevel level, int band) {
        double outer = (band + 1) * BAND_WIDTH;
        Vec3 origin = boss.position();
        var audience = level.players().stream().filter(p -> p.distanceToSqr(boss) <= 100 * 100).toList();
        // Sample the actual floor and ceiling. A missing surface sheds no fragments.
        for (int i = 0; !audience.isEmpty() && i < 12; i++) {
            double angle = (i + .5 * (band % 2)) * Math.PI / 6;
            double radius = outer - BAND_WIDTH * .5;
            Vec3 at = origin.add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
            if (!level.hasChunkAt(BlockPos.containing(at))) continue;
            var hit = level.clip(new ClipContext(at.add(0, 9, 0), at.add(0, -16, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.getDirection() != Direction.UP) continue;
            Vec3 floor = hit.getLocation().add(0, .19, 0);
            scatter(level, audience, floor, hit.getBlockPos(), 9);
            if (i % 4 == 0) {
                var roof = level.clip(new ClipContext(floor, floor.add(0, 64, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
                if (roof.getType() == HitResult.Type.BLOCK && !roof.isInside() && roof.getDirection() == Direction.DOWN)
                    scatter(level, audience, roof.getLocation().add(0, -.25, 0), roof.getBlockPos(), 6);
            }
        }
        AABB area = new AABB(origin.add(-outer, -10, -outer), origin.add(outer, 20, outer));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, SpiderPhase::edible)) {
            Vec3 offset = mob.position().subtract(origin);
            double distance = offset.horizontalDistance();
            if (distance > outer || (band > 0 && distance <= outer - BAND_WIDTH) || recoiled.contains(mob.getUUID()) || !mob.isAlive()
                    || mob instanceof com.codex.blacklight.entity.BlacklightHydra
                    || mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host && host.giantBoss()
                    || mob.noPhysics || mob.isNoAi() || mob.isPassenger() || mob.isVehicle()
                    || mob.getPersistentData().hasUUID("GreeneMoltOwner")
                    || mob.getPersistentData().hasUUID("EncounterOwner") && !boss.getUUID().equals(mob.getPersistentData().getUUID("EncounterOwner"))
                    || GroundSpikePin.held(mob) || ConsumeControl.protectedEntity(mob) || ConsumeControl.ownsMotion(mob)
                    || ImpactControl.stunned(mob)) continue;
            recoiled.add(mob.getUUID());
            double angle = distance > .01 ? Math.atan2(offset.z, offset.x) : Math.toRadians(mob.getYRot() + 90);
            float yaw = (float)Math.toDegrees(angle) - 90;
            ImpactControl.begin(mob, FLINCH, yaw);
            mob.knockback(.95 - .35 * distance / (BAND_WIDTH * ROAR_BANDS), -Math.cos(angle), -Math.sin(angle));
            mob.hurtMarked = true;
        }
        if (band == ROAR_BANDS - 1) recoiled.clear();
    }

    private static void scatter(ServerLevel level, java.util.List<ServerPlayer> audience, Vec3 pos, BlockPos surface, int count) {
        var chips = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(surface));
        for (ServerPlayer player : audience)
            level.sendParticles(player, chips, true, pos.x, pos.y, pos.z, count, .8, .14, .8, .16);
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
        ListTag brood = new ListTag();
        for (UUID id : recoiled) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Mob", id);
            brood.add(entry);
        }
        tag.put("RoarBrood", brood);
    }

    void load(CompoundTag tag) {
        affected.clear();
        recoiled.clear();
        ListTag brood = tag.getList("RoarBrood", Tag.TAG_COMPOUND);
        for (int i = 0; i < brood.size(); i++) {
            CompoundTag entry = brood.getCompound(i);
            if (entry.hasUUID("Mob")) recoiled.add(entry.getUUID("Mob"));
        }
        ListTag players = tag.getList("ShockwavePlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("Player")) affected.add(entry.getUUID("Player"));
        }
    }
}
