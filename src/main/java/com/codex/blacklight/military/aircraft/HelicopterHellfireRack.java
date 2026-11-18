package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** Fixed inner Hellfire rack, distinct from the outboard rapid-rocket pod. */
public final class HelicopterHellfireRack {
    // Visual mesh bounds from the original P3D; MissileProfile.length=.52 is a
    // separate gameplay field and must not determine the visible missile length.
    public static final double MISSILE_LENGTH=1.4543548226356506;
    public static final double MISSILE_HALF_WIDTH=.1088517, MISSILE_HALF_HEIGHT=.1413175;
    public static Vec3 mount(int side){return new Vec3(side*1.9,1.4,1.3);}
    // Transport D1/D2 locator lateral ratio, applied to the existing authored hull.
    public static Vec3 rapidPod(int side){return new Vec3(side*2.6542,1.4,1.3);}
    /** Original four physical missile joints, centered on the authored rack. */
    public static Vec3 slot(int index){
        return switch(Math.floorMod(index,4)){
            case 0 -> new Vec3(.162815,.134876,.010615);
            case 1 -> new Vec3(.162933,-.134876,-.010615);
            case 2 -> new Vec3(-.162933,-.134876,-.010615);
            default -> new Vec3(-.162933,.134876,.010615);
        };
    }
    /** Source AI yaw is fixed at zero; pitch is allowed to face the observed target. */
    public static Vec3 launchDirection(Vec3 targetDelta,float yaw,float bank,boolean player){
        var aim=HelicopterArmament.aim(targetDelta,yaw,bank);
        return HelicopterArmament.rotate(HelicopterArmament.direction(0,player?0:aim.pitch()),yaw,bank);
    }
    /** Current finite player magazine is eight rounds; AI uses its infinite-ammo profile. */
    public static boolean loaded(int side,int slot,int remaining){return slot*2+(side<0?0:1)>=8-Math.max(0,Math.min(8,remaining));}
    private HelicopterHellfireRack(){}
}
