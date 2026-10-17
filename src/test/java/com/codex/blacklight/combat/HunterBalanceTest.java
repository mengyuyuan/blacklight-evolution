package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HunterBalanceTest {
    @Test void heavyHitsRetainTheirThreatAfterTheInfectedBudget() {
        float swipe=damage(CreatureAttack.SWIPE),slam=damage(CreatureAttack.SLAM),leap=damage(CreatureAttack.LEAP);
        assertEquals(28,swipe,.001);
        assertEquals(44.8,slam,.001);
        assertEquals(35,leap,.001);
        assertTrue(slam>swipe&&leap>swipe);
        // Fixed damage leaves vitality upgrades useful instead of scaling the enemy to the player's HP.
        assertTrue(slam/450<swipe/100);
    }
    @Test void externalAttackBuffsCannotBypassThePerMoveBudget() {
        for(var move:CreatureAttack.values()) {
            float normal=HunterBalance.infectedDamage(10000,move,1)*5;
            float easy=HunterBalance.infectedDamage(10000,move,.75F)*5;
            float hard=HunterBalance.infectedDamage(10000,move,1.5F)*5;
            assertTrue(normal<=48);
            assertEquals(normal*.75F,easy,.001);
            assertEquals(normal*1.5F,hard,.001);
        }
    }
    private static float damage(CreatureAttack move) {
        return HunterBalance.infectedDamage((float)HunterBalance.DAMAGE*HunterBalance.damageMultiplier(move),move,1)*5;
    }
}
