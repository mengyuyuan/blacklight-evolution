package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Solid face touching a thrown body's final collision box. Speed is measured into that face. */
record ThrownBodyWall(BlockPos block, Vec3 point, Vec3 normal, double speed) {
    static ThrownBodyWall find(ServerLevel level, LivingEntity victim, Vec3 incoming) {
        AABB body = victim.getBoundingBox();
        var low = BlockPos.containing(body.minX - .01, body.minY - 1, body.minZ - .01);
        var high = BlockPos.containing(body.maxX + .01, body.maxY, body.maxZ + .01);
        var context = CollisionContext.of(victim);
        ThrownBodyWall strongest = null;
        for (var pos : BlockPos.betweenClosed(low, high)) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos, context);
            for (var local : shape.toAabbs()) {
                var face = contact(body, local.move(pos), pos, incoming);
                if (face != null && (strongest == null || face.speed > strongest.speed)) strongest = face;
            }
        }
        return strongest;
    }

    static ThrownBodyWall contact(AABB body, AABB solid, BlockPos pos, Vec3 incoming) {
        double bottom = Math.max(body.minY, solid.minY);
        double top = Math.min(body.maxY, solid.maxY);
        if (top <= bottom + .001) return null;
        double y = (bottom + top) * .5;
        double minX = Math.max(body.minX, solid.minX), maxX = Math.min(body.maxX, solid.maxX);
        double minZ = Math.max(body.minZ, solid.minZ), maxZ = Math.min(body.maxZ, solid.maxZ);
        if (maxZ > minZ + .001 && Math.abs(incoming.x) > .35) {
            boolean east = incoming.x > 0;
            double x = east ? solid.minX : solid.maxX;
            if (Math.abs(x - (east ? body.maxX : body.minX)) < .01)
                return new ThrownBodyWall(pos.immutable(), new Vec3(x, y, (minZ + maxZ) * .5),
                        new Vec3(east ? -1 : 1, 0, 0), Math.abs(incoming.x));
        }
        if (maxX > minX + .001 && Math.abs(incoming.z) > .35) {
            boolean south = incoming.z > 0;
            double z = south ? solid.minZ : solid.maxZ;
            if (Math.abs(z - (south ? body.maxZ : body.minZ)) < .01)
                return new ThrownBodyWall(pos.immutable(), new Vec3((minX + maxX) * .5, y, z),
                        new Vec3(0, 0, south ? -1 : 1), Math.abs(incoming.z));
        }
        return null;
    }
}
