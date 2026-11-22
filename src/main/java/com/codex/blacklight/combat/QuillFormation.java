package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;

public final class QuillFormation {
    public static final int VISIBLE_SLOTS = 48;
    private QuillFormation() {}

    public static long harvestCount(long rank) {
        return rank == Long.MAX_VALUE ? rank : Math.max(0, rank) + 1;
    }

    /** Carry hundredths between bundles so rounding happens once per player's wave. */
    public static final class Wave {
        private final int percent;
        private int remainder = 99;
        public Wave(int percent) { this.percent = Math.max(1, Math.min(100, percent)); }
        public long take(long ready) {
            int fractional = (int)(ready % 100) * percent + remainder;
            remainder = fractional % 100;
            return ready / 100 * percent + fractional / 100;
        }
    }

    public static Vec3 offset(int slot, int total, double time, double radius, float yaw) {
        int rings = total >= 24 ? 4 : total >= 14 ? 3 : total >= 6 ? 2 : 1;
        int ring = slot % rings;
        int onRing = Math.max(1, (total + rings - 1 - ring) / rings);
        double angle = (slot / rings) * Math.PI * 2 / onRing + ring * .91
                + time * (ring % 2 == 0 ? .086 : -.103);
        double x = Math.cos(angle), z = Math.sin(angle);
        Vec3 local = switch (ring) {
            case 1 -> new Vec3(x * radius * .83, .28 - x * .38, z * radius * .83);
            case 2 -> new Vec3(x * radius * .78, z * .98, -.40 + x * .22);
            case 3 -> new Vec3(x * radius * .54, z * .91, x * radius * .57 + .18);
            default -> new Vec3(x * radius, -.08 + z * .41, z * radius);
        };
        return local.yRot((float)Math.toRadians(-yaw));
    }
}
