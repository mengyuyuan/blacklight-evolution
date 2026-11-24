package com.codex.blacklight.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.combat.GoddessImpact.*;
import static org.junit.jupiter.api.Assertions.*;

class GoddessImpactTest {
    @Test void eachContactUsesTheSameFractionAcrossGrowthAndCombatUnitChanges() {
        for (float maxHp : new float[]{20, 100, 2000, 12500}) {
            for (float units : new float[]{1, 5}) {
                for (var hit : GoddessImpact.values()) {
                    double percent = switch (hit) {
                        case CROWN_NEEDLE -> 5;
                        case FINGER_NEEDLE -> 6;
                        case SPINE_BOLT -> 7;
                        case BACKHAND, CROWN_ACID -> 8;
                        case TENDRIL_ERUPTION -> 15;
                        case LOW_SURF, HIGH_SURF, FINGER_SHOT, TENDRIL_POD, GROUND_WRIST -> 10;
                        case WALL_SPEAR -> 12;
                        case HAND_SWEEP -> 13;
                        case THROWN_ROCK -> 16;
                        case PALM_CANNON -> 20;
                        case BODY_THROW -> 23;
                        case HAND_SLAM -> 24;
                        case VACUOLE -> 30;
                        case SONIC_PING -> 6;
                        case SONIC_HOWL -> 14;
                        case SPIKE_FIELD -> 10;
                        case INFECTION_WAVE -> 12;
                    };
                    assertEquals(maxHp * percent / 100, units * hit.incoming(maxHp, units), .001);
                }
            }
        }
    }

    @Test void overlappingQuickHitsAndFinisherStillRespectTheSharedBudget() {
        var boss = UUID.randomUUID();
        for (float hp : new float[]{100, 2000}) {
            var budget = new GoddessDamageBudget();
            assertEquals(.05 * hp, budget.debit(boss, 1, 0, hp, CROWN_NEEDLE.incoming(hp, 5) * 5), .001);
            assertEquals(.20 * hp, budget.debit(boss, 2, 12, hp, PALM_CANNON.incoming(hp, 5) * 5), .001);
            assertEquals(.20 * hp, budget.debit(boss, 3, 24, hp, VACUOLE.incoming(hp, 5) * 5), .001);
        }
    }
}
