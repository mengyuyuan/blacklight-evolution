package com.codex.blacklight.animation;

import com.codex.blacklight.power.PowerForm;

/** Gameplay action identities. Rendering engines must not define the gameplay protocol. */
public enum PlayerAction {
    IDLE, CLAW_IDLE, CLAW_WALK, CLAW_SPRINT, CHARGED_JUMP, JUMP_RISE, JUMP_FALL,
    WALL_RUN, WALL_CLIMB, WALL_CLING, WALL_LEAP, WALL_MANTLE, WALL_VAULT, WALL_TOP, PARKOUR_SMALL,
    GLIDE, TRANSFORM_CLAWS, CLAW_AUTO_1, CLAW_AUTO_2, CLAW_AUTO_3, CLAW_GROUNDSPIKE,
    CLAW_SPRINT_ATTACK, CLAW_DASHING_SLICE, GRAB_REACH, GRAB_WHIP, GRAB_THROW,
    GRAB_BLADE, GRAB_BLADE_THROW, GRAB_HOLD, GRAB_BLADE_HOLD,
    CONSUME, CONSUME_NORMAL, CONSUME_HAMMER, CONSUME_BLADE, CONSUME_WHIP, CONSUME_MUSCLE,
    CONSUME_LARGE, CONSUME_LARGE_HAMMER, CONSUME_LARGE_MUSCLE, CONSUME_LARGE_WHIP,
    AIR_DASH, AIR_DASH_BRAKE, LAND_MOVING, LAND_LIGHT, LAND_HEAVY, LAND_DEVASTATING, TACTICAL_HOP,
    CONSUME_LARGE_CLAWS;

    public static PlayerAction consume(PowerForm form) {
        return switch (form) {
            case CLAWS -> CONSUME;
            case HAMMERFISTS -> CONSUME_HAMMER;
            case BLADE -> CONSUME_BLADE;
            case WHIPFIST -> CONSUME_WHIP;
            case MUSCLE_MASS -> CONSUME_MUSCLE;
            default -> CONSUME_NORMAL;
        };
    }

    public static PlayerAction consumeLarge(PowerForm form) {
        return switch (form) {
            case CLAWS -> CONSUME_LARGE_CLAWS;
            case BLADE -> CONSUME_BLADE;
            case HAMMERFISTS -> CONSUME_LARGE_HAMMER;
            case MUSCLE_MASS -> CONSUME_LARGE_MUSCLE;
            case WHIPFIST -> CONSUME_LARGE_WHIP;
            default -> CONSUME_LARGE;
        };
    }
}
