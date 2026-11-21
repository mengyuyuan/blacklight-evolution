package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.infection.InfectedAttackStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatSurfaceWoundsTest {
    @Test void fistHammerAndMuscleContactsDentTheSkin() {
        for (var strike : new FleshStrike[]{FleshStrike.FIST, FleshStrike.HAMMER, FleshStrike.MUSCLE})
            assertEquals(CombatSurfaceWounds.Shape.DENT, CombatSurfaceWounds.shape(strike,InfectedAttackStyle.CRUSH));
    }
    @Test void penetratingAndTangentialContactsUseDifferentOpenings() {
        for (var strike : new FleshStrike[]{FleshStrike.EDGE, FleshStrike.CLAW, FleshStrike.LASH}) {
            assertEquals(CombatSurfaceWounds.Shape.PUNCTURE, CombatSurfaceWounds.shape(strike,InfectedAttackStyle.PIERCE));
            assertEquals(CombatSurfaceWounds.Shape.GROOVE, CombatSurfaceWounds.shape(strike,InfectedAttackStyle.TEAR));
        }
        assertEquals(CombatSurfaceWounds.Shape.PUNCTURE, CombatSurfaceWounds.shape(FleshStrike.GENERIC,InfectedAttackStyle.PIERCE));
    }
}
