package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrossModDamageRulesTest {
    @Test void recognizesUnlistedModsButLeavesVanillaAndBlacklightAlone() {
        assertTrue(CrossModDamageRules.externalNamespace("unknown_weapon_mod"));
        assertFalse(CrossModDamageRules.externalNamespace("minecraft"));
        assertFalse(CrossModDamageRules.externalNamespace("blacklight"));
        assertFalse(CrossModDamageRules.externalNamespace(null));
    }
    @Test void splitHitsShareOneBudgetAndCannotOneShot() {
        var budget = new CrossModDamageRules.Budget(100, 0);
        float total = 0;
        for (int i = 0; i < 100; i++) {
            var result = budget.accept(100 + i % 10, 10, CrossModDamageRules.multiply(1000000, .25), 150);
            budget = result.budget(); total += result.damage();
        }
        assertEquals(150, total);
        assertEquals(150, budget.accept(110, 10, 1000000, 150).damage());
    }
    @Test void smallHitsAccumulateAndTimeReversalResetsSafely() {
        var first = new CrossModDamageRules.Budget(30, 0).accept(30, 10, 3, 15);
        var second = first.budget().accept(31, 10, 5, 15);
        assertEquals(8, second.budget().spent());
        assertEquals(7, second.budget().accept(32, 10, 100, 15).damage());
        assertEquals(15, second.budget().accept(0, 10, 100, 15).damage());
    }
    @Test void amplificationDoesNotOverflowAndKeepsZeroDamageZero() {
        assertEquals(40, CrossModDamageRules.multiply(20, 2));
        assertEquals(Float.MAX_VALUE, CrossModDamageRules.multiply(Float.MAX_VALUE, 2));
        assertEquals(0, CrossModDamageRules.multiply(0, 2));
        assertEquals(0, CrossModDamageRules.multiply(Float.NaN, 2));
    }
}
