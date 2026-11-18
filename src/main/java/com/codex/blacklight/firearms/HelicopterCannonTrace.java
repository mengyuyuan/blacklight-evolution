package com.codex.blacklight.firearms;

import net.minecraft.world.phys.Vec3;

/** Cosmetic timing only. The server chin cannon currently resolves damage by hitscan. */
public final class HelicopterCannonTrace {
    // Original GunAmmoProfile speed 200 world units/s, adapted to blocks at 20 ticks/s.
    public static final double BLOCKS_PER_TICK=10;
    public static double arrival(double length){return Math.max(0,length)/BLOCKS_PER_TICK;}
    public static double head(double age,double length){return Math.min(length,Math.max(0,age)*BLOCKS_PER_TICK);}
    public static double tail(double age,double length){return Math.min(length,Math.max(0,age*BLOCKS_PER_TICK-8));}
    public static boolean visibleFrom(Vec3 observer,Vec3 from,Vec3 to,double radius){
        return observer.distanceToSqr(from)<=radius*radius||observer.distanceToSqr(to)<=radius*radius;
    }
    private HelicopterCannonTrace(){}
}
