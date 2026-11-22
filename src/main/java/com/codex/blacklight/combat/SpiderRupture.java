package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Ground contacts shared by the damage solver and the visible fissures on destructible terrain. */
public final class SpiderRupture {
    public static final double STAMP_RADIUS = 5.5;
    public static final double SPIKE_RADIUS = 2.65;
    public static final int ROWS = 5;
    private SpiderRupture() {}

    public static Vec3[] spikeSites(EncounterMob boss, int lastRow) {
        var sites = new Vec3[(lastRow+1)*3];
        var origin = boss.position();
        var aim = boss.spider.actions.aim();
        var axis = aim.subtract(origin);
        var side = new Vec3(axis.z,0,-axis.x).normalize();
        for (int lane=-1; lane<=1; lane++) {
            var previous = origin;
            for (int row=0; row<=lastRow; row++) {
                var spot = origin.add(axis.scale(.26 + .185 * row)).add(side.scale(lane * (1.25 + row * 1.45)));
                int segments = Math.max(1,(int)Math.ceil(previous.distanceTo(spot)/2));
                Vec3 contact = previous;
                for (int sample=1; sample<=segments && contact!=null; sample++) {
                    contact = floor(boss,contact,previous.lerp(spot,sample/(double)segments));
                }
                if (contact==null) break;
                sites[row*3+lane+1] = contact;
                previous = contact;
            }
        }
        return sites;
    }

    public static Vec3 floor(EncounterMob boss, Vec3 origin, Vec3 spot) {
        var level = boss.level();
        if (!level.hasChunkAt(BlockPos.containing(spot))) return null;
        var hit = level.clip(new ClipContext(spot.add(0,2.1,0),spot.add(0,-3.1,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) return null;
        var end = hit.getLocation();
        if (level.clip(new ClipContext(origin.add(0,1.5,0),end.add(0,1.5,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType() != HitResult.Type.MISS) return null;
        return end;
    }
}
