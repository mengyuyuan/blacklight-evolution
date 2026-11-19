package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.firearms.FirearmBlockDamage;
import com.codex.blacklight.movement.LandingDebrisSystem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Structural damage at the Goliath's actual ground and wall contacts. */
public final class GoliathTerrainImpact {
    private GoliathTerrainImpact() {}

    static void ground(EncounterMob actor, List<TerrainImpactEffects.Surface> surfaces, int pulse, Set<Long> columns, Vec3 fist) {
        var level = (ServerLevel) actor.level();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        // The first source cone traces the rim. Fill the footprint between the
        // confirmed contacts so a flat street shears out in one shallow piece.
        if (pulse == 0) {
            Vec3 center = Vec3.ZERO;
            for (var surface : surfaces) center = center.add(surface.hit().getLocation());
            var middle = BlockPos.containing(center.scale(1.0 / surfaces.size()).add(0, -.02, 0));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var end = Vec3.atCenterOf(middle.offset(x, 0, z));
                if (!GoliathGroundImpact.available(level, fist, end)) continue;
                var hit = level.clip(new ClipContext(fist, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
                if (hit.getType() == HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection() == net.minecraft.core.Direction.UP
                        && hit.getBlockPos().equals(middle.offset(x, 0, z))) groundBlock(actor, level, hit, 56, columns);
            }
        }
        for (var surface : surfaces) {
            groundBlock(actor, level, surface.hit(), pulse == 0 ? 56 : 26, columns);
        }
    }

    private static void groundBlock(EncounterMob actor, ServerLevel level, BlockHitResult hit, float force, Set<Long> columns) {
        var pos = hit.getBlockPos();
        if (!columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()))) return;
        var outward = hit.getLocation().subtract(actor.position()).multiply(1, 0, 1).normalize();
        fragment(actor, level, hit, force, outward);
    }

    public static void wall(EncounterMob actor, Vec3 blockedTravel) {
        var level = (ServerLevel) actor.level();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        var touched = new HashSet<BlockPos>();
        if (Math.abs(blockedTravel.x) > .001) wallFace(actor, level, true, Math.signum(blockedTravel.x), touched);
        if (Math.abs(blockedTravel.z) > .001) wallFace(actor, level, false, Math.signum(blockedTravel.z), touched);
    }

    private static void wallFace(EncounterMob actor, ServerLevel level, boolean xAxis, double sign, Set<BlockPos> touched) {
        var box = actor.getBoundingBox();
        Vec3 normal = xAxis ? new Vec3(sign, 0, 0) : new Vec3(0, 0, sign);
        Vec3 across = new Vec3(normal.z, 0, -normal.x);
        Vec3 face = actor.position().add(normal.scale((xAxis ? box.getXsize() : box.getZsize()) / 2));
        // Sample just across the stopped face. A diagonal slide touches only its blocked axis.
        int firstContact = -1;
        for (int row = 0; row + .6 < box.getYsize(); row++) {
            if (firstContact >= 0 && row - firstContact >= 4) break;
            for (int col = -2; col <= 2; col++) {
                var at = face.add(across.scale(col * actor.getBbWidth() / 6)).add(0, .6 + row, 0);
                var from = at.subtract(normal.scale(.06));
                var to = at.add(normal.scale(.08));
                if (!level.hasChunkAt(BlockPos.containing(from)) || !level.hasChunkAt(BlockPos.containing(to))) continue;
                var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
                if (hit.getType() != HitResult.Type.BLOCK || !touched.add(hit.getBlockPos())) continue;
                if (firstContact < 0) firstContact = row;
                boolean core = Math.abs(col) < 2 && row - firstContact < 3;
                fragment(actor, level, hit, core ? 66 : 24, normal.scale(-1));
            }
        }
    }

    private static void fragment(EncounterMob actor, ServerLevel level, BlockHitResult hit, float force, Vec3 outward) {
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!FirearmBlockDamage.mobImpact(level, actor, pos, state, force)) return;
        var random = actor.getRandom();
        Vec3 origin = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.08));
        Vec3 tangent = new Vec3(outward.z, 0, -outward.x);
        for (int i = 0; i < 2; i++) {
            var velocity = outward.scale(.19 + random.nextDouble() * .25)
                    .add(tangent.scale((random.nextDouble() - .5) * .3)).add(0, .24 + random.nextDouble() * .27, 0);
            LandingDebrisSystem.fragment(level, origin, state, velocity, .25F + random.nextFloat() * .30F, 28);
        }
    }
}
