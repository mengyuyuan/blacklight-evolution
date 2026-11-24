package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.movement.LandingDebrisSystem;
import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.registry.ModInfectedSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared world-space front for the rising goddess's blood mist and its physical contact. */
public final class GreeneMoltSurge {
    public static final int GATHER = 404, RELEASE = 414, ARRIVAL = 454;
    public static final float RADIUS = 54;
    private final Set<UUID> swept = new HashSet<>();

    public static float frontRadius(float moltAge) {
        float t = Math.max(0, Math.min(1, (moltAge - RELEASE) / (ARRIVAL - RELEASE)));
        return RADIUS * t * (1.5F - .5F * t);
    }

    void tick(EncounterMob boss, ServerLevel level, int age) {
        if (age < GATHER || age > ARRIVAL) return;
        Vec3 origin = boss.position();
        if (age == GATHER || age == RELEASE || age == ARRIVAL) {
            var sound = age == GATHER ? ModInfectedSounds.Pressure.STRAIN
                    : age == RELEASE ? ModInfectedSounds.Pressure.BURST : ModInfectedSounds.Pressure.ECHO;
            if (!boss.isSilent()) level.playSound(null, origin.x, origin.y + (age == GATHER ? 18 : 1), origin.z,
                    ModInfectedSounds.pressure(sound), SoundSource.HOSTILE, age == GATHER ? 2F : 3.5F, age == RELEASE ? .70F : .84F);
        }
        int elapsed = age - RELEASE;
        if (elapsed <= 0 || elapsed % 2 != 0) return;
        double inner = frontRadius(age - 2), outer = frontRadius(age);
        if (elapsed % 8 == 0 && level.players().stream().anyMatch(player -> player.distanceToSqr(boss) <= 100 * 100)) {
            double rotation = elapsed / 8 * 1.21;
            for (int i = 0; i < 6; i++) {
                double angle = rotation + i * Math.PI / 3;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 point = origin.add(radial.scale(outer));
                var floor = floor(boss, point);
                if (floor == null || !exposed(boss, origin, floor.getLocation().add(0, .65, 0))) continue;
                var state = level.getBlockState(floor.getBlockPos());
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) continue;
                // The fragment follows gravity, collides and shatters. It never takes or places the sampled block.
                LandingDebrisSystem.fragment(level, floor.getLocation().add(0, .32, 0), state,
                        radial.scale(.32).add(0, .34, 0), .35F, 20);
            }
        }
        AABB area = new AABB(origin.add(-outer, -16, -outer), origin.add(outer, 13, outer));
        for (Entity entity : level.getEntities(boss, area, GreeneMoltSurge::movable)) {
            Vec3 offset = entity.position().subtract(origin);
            double distance = offset.horizontalDistance();
            if ((inner > 0 && distance <= inner) || distance > outer || swept.contains(entity.getUUID())) continue;
            var floor = floor(boss, entity.position());
            if (floor == null || Math.abs(entity.getY() - floor.getLocation().y) > (entity instanceof ItemEntity ? 1.25 : .65)
                    || !exposed(boss, origin, entity.position().add(0, .65, 0))) continue;
            // An impulse only: retain steering and velocity, never replace an attack, teleport, or deal impact damage.
            double falloff = 1 - .32 * distance / RADIUS;
            double push = (entity instanceof ServerPlayer ? .43 : entity instanceof ItemEntity ? .8 : .64) * falloff;
            double lift = entity instanceof ServerPlayer ? .10 : entity instanceof ItemEntity ? .25 : .19;
            Vec3 radial = distance > .01 ? new Vec3(offset.x / distance, 0, offset.z / distance) :
                    new Vec3(0, 0, 1).yRot((float)-Math.toRadians(boss.greeneBattle().collapseYaw()));
            entity.push(radial.x * push, lift * falloff, radial.z * push);
            entity.hasImpulse = true;
            entity.hurtMarked = true;
            swept.add(entity.getUUID());
        }
        if (age == ARRIVAL) swept.clear();
    }

    private static boolean movable(Entity entity) {
        if (!entity.isAlive() || entity.noPhysics || entity.isPassenger() || entity.isVehicle()) return false;
        if (entity instanceof ItemEntity) return true;
        if (entity instanceof ServerPlayer player) {
            if (player.isSpectator() || player.isCreative() || player.getAbilities().flying || player.isFallFlying()
                    || player.isSleeping() || !player.onGround() || ConsumeSystem.active(player) || BiomassSystem.active(player)) return false;
        } else if (entity instanceof Mob mob) {
            if (!SpiderPhase.edible(mob) || !mob.onGround() || mob.isNoAi() || mob instanceof BlacklightHydra
                    || mob instanceof BlacklightInfectedHost host && host.giantBoss() || mob.getPersistentData().hasUUID("GreeneMoltOwner")
                    || GroundSpikePin.held(mob) || ImpactControl.stunned(mob)) return false;
        } else return false;
        return !ConsumeControl.protectedEntity(entity) && !ConsumeControl.ownsMotion(entity);
    }

    private static BlockHitResult floor(EncounterMob boss, Vec3 point) {
        if (!boss.level().hasChunkAt(BlockPos.containing(point))) return null;
        var hit = ray(boss, point.add(0, 8, 0), point.add(0, -16, 0));
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection() == Direction.UP ? hit : null;
    }

    private static boolean exposed(EncounterMob boss, Vec3 origin, Vec3 to) {
        return ray(boss, origin.add(0, 1.6, 0), to).getType() == HitResult.Type.MISS;
    }

    private static BlockHitResult ray(EncounterMob boss, Vec3 from, Vec3 to) {
        return boss.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
    }

    void save(CompoundTag tag) {
        if (swept.isEmpty()) return;
        var ids = new ListTag();
        for (UUID id : swept) {
            var entry = new CompoundTag(); entry.putUUID("Id", id); ids.add(entry);
        }
        tag.put("MoltSurgeSwept", ids);
    }

    void load(CompoundTag tag) {
        swept.clear();
        var ids = tag.getList("MoltSurgeSwept", Tag.TAG_COMPOUND);
        for (int i = 0; i < ids.size(); i++) {
            var entry = ids.getCompound(i);
            if (entry.hasUUID("Id")) swept.add(entry.getUUID("Id"));
        }
    }
}
