package com.codex.blacklight.power;

/**
 * Persistent Prototype-style traversal upgrades bought with evolution points.
 * Multiplier resolution is pure bitmask math so both the authoritative server and
 * the client-side prediction read identical values from the synced state.
 */
public enum MovementUpgrade {
    JUMP_1("jump_1", 1 << 0, 100, null),
    JUMP_2("jump_2", 1 << 1, 200, JUMP_1),
    JUMP_3("jump_3", 1 << 2, 300, JUMP_2),
    SPRINT_1("sprint_1", 1 << 3, 100, null),
    SPRINT_2("sprint_2", 1 << 4, 200, SPRINT_1),
    WALL_1("wall_1", 1 << 5, 100, null),
    WALL_2("wall_2", 1 << 6, 200, WALL_1),
    GLIDE_1("glide_1", 1 << 7, 100, null),
    GLIDE_2("glide_2", 1 << 8, 200, GLIDE_1),
    AIR_DASH_PLUS("air_dash_plus", 1 << 9, 250, null),
    WING_SPEED_1("wing_speed_1", 1 << 10, 600, null),
    WING_SPEED_2("wing_speed_2", 1 << 11, 1200, WING_SPEED_1),
    WING_SPEED_3("wing_speed_3", 1 << 12, 2400, WING_SPEED_2);

    private final String id;
    private final int bit;
    private final int epCost;
    private final MovementUpgrade requires;

    MovementUpgrade(String id, int bit, int epCost, MovementUpgrade requires) {
        this.id = id;
        this.bit = bit;
        this.epCost = epCost;
        this.requires = requires;
    }

    public String id() { return id; }
    public int bit() { return bit; }
    public int epCost() { return epCost; }
    public MovementUpgrade requires() { return requires; }
    public boolean wingUpgrade() { return this == WING_SPEED_1 || this == WING_SPEED_2 || this == WING_SPEED_3; }
    public String prerequisiteId() { return requires != null ? requires.id : wingUpgrade() ? PowerForm.SOVEREIGN_WINGS.id() : null; }
    public static int wingTier(int mask) {
        // A broken/partial chain must never grant a higher flight speed.
        if ((mask & WING_SPEED_1.bit) == 0) return 0;
        if ((mask & WING_SPEED_2.bit) == 0) return 1;
        return (mask & WING_SPEED_3.bit) == 0 ? 2 : 3;
    }

    public static MovementUpgrade byId(String id) {
        for (MovementUpgrade upgrade : values()) {
            if (upgrade.id.equals(id)) return upgrade;
        }
        return null;
    }

    public static double jumpMultiplier(int mask) {
        if ((mask & JUMP_3.bit) != 0) return 1.30D;
        if ((mask & JUMP_2.bit) != 0) return 1.20D;
        if ((mask & JUMP_1.bit) != 0) return 1.10D;
        return 1.0D;
    }

    public static double sprintMultiplier(int mask) {
        if ((mask & SPRINT_2.bit) != 0) return 1.45D;
        if ((mask & SPRINT_1.bit) != 0) return 1.25D;
        return 1.08D;
    }

    public static double wallMultiplier(int mask) {
        if ((mask & WALL_2.bit) != 0) return 1.60D;
        if ((mask & WALL_1.bit) != 0) return 1.30D;
        return 1.0D;
    }

    /** Glide sink-rate scale: lower means a flatter, longer glide. */
    public static double glideFallScale(int mask) {
        if ((mask & GLIDE_2.bit) != 0) return 0.45D;
        if ((mask & GLIDE_1.bit) != 0) return 0.70D;
        return 1.0D;
    }

    public static double glideSpeedScale(int mask) {
        if ((mask & GLIDE_2.bit) != 0) return 1.40D;
        if ((mask & GLIDE_1.bit) != 0) return 1.20D;
        return 1.0D;
    }

    public static int airDashCharges(int mask) {
        return (mask & AIR_DASH_PLUS.bit) != 0 ? 2 : 1;
    }
}
