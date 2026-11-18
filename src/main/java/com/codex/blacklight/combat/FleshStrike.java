package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.power.PowerForm;

/** Presentation of one accepted flesh contact. IDs are stable on the cosmetic wire. */
public enum FleshStrike {
    GENERIC(0), FIST(1), LASH(2), HAMMER(3), MUSCLE(4), EDGE(5);

    public final int id;
    FleshStrike(int id) { this.id = id; }

    public static FleshStrike byId(int id) {
        return switch (id) {
            case 1 -> FIST;
            case 2 -> LASH;
            case 3 -> HAMMER;
            case 4 -> MUSCLE;
            case 5 -> EDGE;
            default -> GENERIC;
        };
    }

    public static FleshStrike of(PowerForm equipped, WeaponMove move) {
        if (move.id.startsWith("dev_") || move == WeaponMove.BULLETDIVE_DROP) return GENERIC;
        if (move.shared()) return equipped == PowerForm.MUSCLE_MASS ? MUSCLE : FIST;
        return switch (move.form) {
            case MUSCLE_MASS -> equipped == PowerForm.MUSCLE_MASS ? MUSCLE : FIST;
            case HAMMERFISTS -> HAMMER;
            case WHIPFIST -> LASH;
            case BLADE, CLAWS -> EDGE;
            default -> GENERIC;
        };
    }

    public InfectedAttackStyle style(WeaponCutContact.Kind kind) {
        if (this == FIST || this == HAMMER || this == MUSCLE) return InfectedAttackStyle.CRUSH;
        return switch (kind) {
            case PUNCTURE -> InfectedAttackStyle.PIERCE;
            case SLICE -> InfectedAttackStyle.TEAR;
            default -> InfectedAttackStyle.CRUSH;
        };
    }
}
