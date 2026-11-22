package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;

public final class QuillFormation {
    public static final int VISIBLE_SLOTS = 48;
    private QuillFormation() {}

    public static long capacity(long rank) {
        return 6 + Math.min(Math.max(0, rank), Long.MAX_VALUE - 6);
    }

    public static long extendLife(long ticks, long extraRanks) {
        long room = Long.MAX_VALUE - ticks;
        return extraRanks > room / 20 ? Long.MAX_VALUE : ticks + 20 * extraRanks;
    }

    public static int volleySize(int ready, int percent) {
        return (int)(((long)ready * Math.max(1, Math.min(100, percent)) + 99) / 100);
    }

    public static Vec3 offset(int slot, int total, double time, double radius) {
        int rings = total >= 24 ? 4 : total >= 14 ? 3 : total >= 6 ? 2 : 1;
        int ring = slot % rings;
        int onRing = Math.max(1, (total + rings - 1 - ring) / rings);
        double angle = (slot / rings) * Math.PI * 2 / onRing + ring * .91
                + time * (ring % 2 == 0 ? .086 : -.103);
        double x = Math.cos(angle), z = Math.sin(angle);
        return switch (ring) {
            case 1 -> new Vec3(x * radius * .83, .28 - x * .38, z * radius * .83);
            case 2 -> new Vec3(x * radius * .78, z * .98, -.40 + x * .22);
            case 3 -> new Vec3(x * radius * .54, z * .91, x * radius * .57 + .18);
            default -> new Vec3(x * radius, -.08 + z * .41, z * radius);
        };
    }
}
