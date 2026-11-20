package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;

/** World-space impulse supplied to the struck joint. The body solver applies it at the wound. */
final class JointImpact {
    private JointImpact() {}

    static Vec3 impulse(Vec3 recoil, Vec3 travel, int samples, double volume, boolean sharp, boolean quill) {
        if (samples < 1 || travel.lengthSqr() < 1e-8) return Vec3.ZERO;
        double mass = Math.max(.25, Math.min(1.12, 1 / Math.cbrt(Math.max(.25, volume))));
        double sweep = Math.min(1.2, travel.length() / samples);
        double impulse = quill ? .09 + Math.min(.8, recoil.length()) * .46
                : (sharp ? .17 : .13) + sweep * (sharp ? .38 : .27) + Math.min(1.2, recoil.length()) * .2;
        Vec3 result = recoil.scale(.38).add(travel.normalize().scale(impulse * mass));
        return result.lengthSqr() > 1.44 ? result.normalize().scale(1.2) : result;
    }
}
