package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Ballistic drops that survive the short contact spray and can actually reach a surface. */
enum BloodFlight {
    BRUISE(5, .24, .13, .065), CUT(7, .35, .15, .035), RAKE(5, .29, .11, .045),
    LASH(5, .42, .07, .045), CRUSH(9, .20, .28, .11), DRIVE(6, .36, .14, .075),
    ENTRY(3, .23, .048, .025), BURST(9, .32, .25, .14);

    final int drops;
    private final double speed, width, rise;

    BloodFlight(int drops, double speed, double width, double rise) {
        this.drops = drops;
        this.speed = speed;
        this.width = width;
        this.rise = rise;
    }

    static BloodFlight of(FleshStrike strike, InfectedAttackStyle style) {
        if (style == InfectedAttackStyle.PIERCE) return ENTRY;
        return switch (strike) {
            case FIST -> BRUISE;
            case HAMMER -> CRUSH;
            case MUSCLE -> DRIVE;
            case LASH -> LASH;
            case EDGE -> CUT;
            case CLAW -> RAKE;
            case QUILL -> ENTRY;
            default -> switch (style) {
                case WHIP -> LASH;
                case BURST, RESONANCE -> BURST;
                case CRUSH -> CRUSH;
                default -> CUT;
            };
        };
    }

    Vec3 velocity(Vec3 axis, Vec3 across, Vec3 up, Vec3 inherited, double force, RandomSource random) {
        double side = (random.nextDouble() * 2 - 1) * width;
        double height = (random.nextDouble() * 2 - 1) * width * .45;
        double travel = speed * (.55 + .8 * random.nextDouble());
        return axis.scale(travel).add(across.scale(side)).add(up.scale(height))
                .add(0, rise, 0).scale(force).add(inherited);
    }

    /** A glancing drop leaves its long axis along the surface rather than along the face normal. */
    static Vec3 surfaceStretch(Vec3 velocity, Vec3 normal) {
        Vec3 tangent = velocity.subtract(normal.scale(velocity.dot(normal)));
        double angle = tangent.length() / Math.max(.04, Math.abs(velocity.dot(normal)));
        return tangent.normalize().scale(Math.min(2.4, angle) * .68);
    }
}
