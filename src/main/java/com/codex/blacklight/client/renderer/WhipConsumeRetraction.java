package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** The restraint releases with tissue deformation; the cable then reels into the wrist. */
public final class WhipConsumeRetraction {
    private WhipConsumeRetraction() {}
    public static float coil(float opening) { return 1-smooth(opening); }
    public static float cable(float returnFlow) { return 1-smooth(returnFlow/.35F); }
    public static Vec3 end(Vec3 hand,Vec3 body,float deployment,float returnFlow) {
        return hand.lerp(body,com.codex.blacklight.forge.WingMath.clamp(deployment,0,1)*cable(returnFlow));
    }
    private static float smooth(float value) {
        float t=com.codex.blacklight.forge.WingMath.clamp(value,0,1);return t*t*(3-2*t);
    }
}
