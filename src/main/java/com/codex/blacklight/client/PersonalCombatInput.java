package com.codex.blacklight.client;

import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.power.PowerForm;

/** Shared input policy, kept in its own source for release class inventory. */
record PersonalCombatInput(boolean infected, boolean prototype, PowerForm form,
                           boolean emptyHands, boolean consuming, boolean vehicleInput) {
    boolean active() {
        return !vehicleInput && infected && prototype && WeaponMove.controls(form, emptyHands) && !consuming;
    }
}
