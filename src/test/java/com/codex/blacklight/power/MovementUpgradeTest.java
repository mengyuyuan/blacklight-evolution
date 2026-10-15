package com.codex.blacklight.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementUpgradeTest {
    @Test
    void jumpMultiplierPicksTheHighestOwnedTier() {
        assertEquals(1.0D, MovementUpgrade.jumpMultiplier(0), 0.0001D);
        assertEquals(1.10D, MovementUpgrade.jumpMultiplier(MovementUpgrade.JUMP_1.bit()), 0.0001D);
        int tier2 = MovementUpgrade.JUMP_1.bit() | MovementUpgrade.JUMP_2.bit();
        assertEquals(1.20D, MovementUpgrade.jumpMultiplier(tier2), 0.0001D);
        int tier3 = tier2 | MovementUpgrade.JUMP_3.bit();
        assertEquals(1.30D, MovementUpgrade.jumpMultiplier(tier3), 0.0001D);
    }

    @Test
    void sprintWallAndGlideScalesFollowOwnedTiers() {
        assertEquals(1.08D, MovementUpgrade.sprintMultiplier(0), 0.0001D);
        assertEquals(1.45D, MovementUpgrade.sprintMultiplier(
                MovementUpgrade.SPRINT_1.bit() | MovementUpgrade.SPRINT_2.bit()), 0.0001D);
        assertEquals(1.0D, MovementUpgrade.wallMultiplier(0), 0.0001D);
        assertEquals(1.60D, MovementUpgrade.wallMultiplier(
                MovementUpgrade.WALL_1.bit() | MovementUpgrade.WALL_2.bit()), 0.0001D);
        assertEquals(1.0D, MovementUpgrade.glideFallScale(0), 0.0001D);
        assertEquals(0.70D, MovementUpgrade.glideFallScale(MovementUpgrade.GLIDE_1.bit()), 0.0001D);
        assertEquals(0.45D, MovementUpgrade.glideFallScale(
                MovementUpgrade.GLIDE_1.bit() | MovementUpgrade.GLIDE_2.bit()), 0.0001D);
        assertEquals(1.40D, MovementUpgrade.glideSpeedScale(
                MovementUpgrade.GLIDE_1.bit() | MovementUpgrade.GLIDE_2.bit()), 0.0001D);
    }

    @Test
    void airDashChargesDefaultToOneAndDoubleWithTheUpgrade() {
        assertEquals(1, MovementUpgrade.airDashCharges(0));
        assertEquals(2, MovementUpgrade.airDashCharges(MovementUpgrade.AIR_DASH_PLUS.bit()));
    }

    @Test
    void everyIdRoundTrips() {
        for (MovementUpgrade upgrade : MovementUpgrade.values()) {
            assertEquals(upgrade, MovementUpgrade.byId(upgrade.id()));
        }
        assertEquals(null, MovementUpgrade.byId("not_an_upgrade"));
    }
}
