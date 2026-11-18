package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** A body already touching the airframe may leave it, but cannot enter farther.
 * This is a Minecraft collision policy, not a source-game flight parameter. */
public final class HelicopterMovementClearance {
    private HelicopterMovementClearance() {}

    public static boolean clear(BlackwatchHelicopter craft,AABB before,AABB after) {
        var level=craft.level();var swept=before.minmax(after);
        if(!VehicleKinematics.loaded(level,swept.inflate(1))||!level.getWorldBorder().isWithinBounds(swept))return false;
        // Keep the existing sub-millimetre contact tolerance, not a tunnelling margin.
        var start=before.deflate(.001);var end=after.deflate(.001);
        for(var shape:level.getBlockCollisions(craft,swept.deflate(.001)))
            for(var box:shape.toAabbs())if(!permits(start,end,box))return false;
        for(var shape:level.getEntityCollisions(craft,swept.deflate(.001)))
            for(var box:shape.toAabbs())if(!permits(start,end,box))return false;
        // Ordinary living bodies do not all expose solid collision shapes.
        for(var entity:level.getEntities(craft,swept,e->e instanceof LivingEntity living&&living.isAlive()
                &&!craft.hasPassenger(e)&&!(e instanceof MilitarySoldier soldier&&HelicopterRappel.belongs(soldier,craft))))
            if(!permits(start,end,entity.getBoundingBox()))return false;
        return true;
    }

    public static boolean permits(AABB before,AABB after,AABB obstacle) {
        var swept=before.minmax(after);
        if(!swept.intersects(obstacle))return true;
        if(!before.intersects(obstacle))return false;
        double initial=overlap(before,obstacle);
        // The whole path must not claim more of an existing obstacle than the initial pose.
        if(overlap(swept,obstacle)>initial+1e-9||overlap(after,obstacle)>initial+1e-9)return false;
        // Complete containment can leave the overlap volume unchanged for several steps.
        // Only movement away from that obstacle is allowed during this plateau.
        return after.getCenter().distanceToSqr(obstacle.getCenter())
                >before.getCenter().distanceToSqr(obstacle.getCenter())+1e-12;
    }

    private static double overlap(AABB a,AABB b) {
        return Math.max(0,Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX))
                *Math.max(0,Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY))
                *Math.max(0,Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ));
    }
}
