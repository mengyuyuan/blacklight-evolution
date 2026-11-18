package com.codex.blacklight.combat;

import net.minecraft.world.phys.AABB;

/** Query padding only: Minecraft AABB enumeration excludes exact tangency,
 * while the original sphere contact and our narrow phase include it. */
final class GoliathAttackBounds {
    private GoliathAttackBounds() {}
    static AABB includingTouch(AABB bounds){
        if(bounds==null)return null;
        double coordinate=Math.max(Math.max(Math.abs(bounds.minX),Math.abs(bounds.maxX)),
            Math.max(Math.max(Math.abs(bounds.minY),Math.abs(bounds.maxY)),Math.max(Math.abs(bounds.minZ),Math.abs(bounds.maxZ))));
        // The world-coordinate term remains representable near the MC border.
        // It never changes a source sphere, sweep, contact point or damage.
        return bounds.inflate(1e-7+8*Math.ulp(coordinate));
    }
}
