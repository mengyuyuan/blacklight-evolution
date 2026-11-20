package com.codex.blacklight.combat;

/** Durable organ state is separate from temporary, repeatable attack openings. */
public enum GoliathWindow {
    NONE(null, 0), HEAD(GoliathLockPart.HEAD, 60), KNEE(GoliathLockPart.RIGHT_LEG, 66),
    LEG_FALL(null, 43), ARM(GoliathLockPart.RIGHT_ARM, 58), ARM_FALL(null, 58),
    FACE(GoliathLockPart.HEAD, 96), FINISH(GoliathLockPart.HEAD, 0);

    public final GoliathLockPart part;
    public final int ticks;

    GoliathWindow(GoliathLockPart part, int ticks) {
        this.part = part;
        this.ticks = ticks;
    }

    public static GoliathWindow byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }

    public boolean matchesMask(int mask) {
        return switch (this) {
            case NONE -> true;
            case HEAD, KNEE -> mask == 0;
            case LEG_FALL, ARM -> mask == 1;
            case ARM_FALL, FACE, FINISH -> mask == 3;
        };
    }
}
