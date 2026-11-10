package com.codex.blacklight.military.vehicle;

import net.minecraft.world.phys.Vec3;

/** Turret aim angles in native MC yaw/pitch, measured from the gun rather than the player's eye. */
public final class VehicleAim {
    private VehicleAim(){}
    public static float yaw(Vec3 from,Vec3 to){var d=to.subtract(from);return (float)Math.toDegrees(Math.atan2(-d.x,d.z));}
    public static float pitch(Vec3 from,Vec3 to){var d=to.subtract(from);return (float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));}
}
