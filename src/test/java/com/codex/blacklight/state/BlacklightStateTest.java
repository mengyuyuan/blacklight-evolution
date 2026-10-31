package com.codex.blacklight.state;

import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.MovementUpgrade;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlacklightStateTest {
    @Test
    void infectionUnlocksClawsButKeepsVanillaModeByDefault() {
        BlacklightState state = new BlacklightState();
        state.infect();
        assertTrue(state.isInfected());
        assertTrue(state.hasUnlocked(PowerForm.CLAWS));
        assertFalse(state.isPrototypeMode());
        assertEquals(PowerForm.NONE, state.getActivePower());
        assertEquals(250, state.getBiomass());
    }

    @Test
    void prototypeModeDeploysClawsAndVanillaModeRestoresNoActivePower() {
        BlacklightState state = new BlacklightState();
        assertFalse(state.setPrototypeMode(true));
        state.infect();

        assertTrue(state.setPrototypeMode(true));
        assertTrue(state.isPrototypeMode());
        assertEquals(PowerForm.CLAWS, state.getActivePower());

        assertTrue(state.setPrototypeMode(false));
        assertFalse(state.isPrototypeMode());
        assertEquals(PowerForm.NONE, state.getActivePower());
    }

    @Test
    void biomassIsClampedAndCannotOverspend() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.addBiomass(5000);
        assertEquals(BlacklightState.MAX_BIOMASS, state.getBiomass());
        assertFalse(state.spendBiomass(1001));
        assertTrue(state.spendBiomass(300));
        assertEquals(700, state.getBiomass());
    }

    @Test
    void rewardsAndAlertSaturateWithoutIntegerOverflow() {
        BlacklightState state=new BlacklightState();state.infect();
        state.addBiomass(Integer.MAX_VALUE);
        assertEquals(BlacklightState.MAX_BIOMASS,state.getBiomass());
        state.addEvolutionPoints(100);state.addEvolutionPoints(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE,state.getEvolutionPoints());
        state.raiseAlert(30);state.raiseAlert(Integer.MAX_VALUE);assertEquals(100,state.getAlert());
        state.addBiomass(Integer.MIN_VALUE);state.addEvolutionPoints(Integer.MIN_VALUE);state.raiseAlert(Integer.MIN_VALUE);
        assertEquals(0,state.getBiomass());assertEquals(0,state.getEvolutionPoints());assertEquals(0,state.getAlert());
    }

    @Test
    void purchasesRequireInfectionAndEnoughEvolutionPoints() {
        BlacklightState state = new BlacklightState();
        assertFalse(state.buy(PowerForm.HAMMERFISTS));
        state.infect();
        state.addEvolutionPoints(249);
        assertFalse(state.buy(PowerForm.HAMMERFISTS));
        state.addEvolutionPoints(1);
        assertTrue(state.buy(PowerForm.HAMMERFISTS));
        assertEquals(0, state.getEvolutionPoints());
    }

    @Test
    void alertTracksExposureWithoutForcingAnAppearanceChange() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.setPrototypeMode(true);
        state.captureIdentity("minecraft:villager");
        assertTrue(state.toggleDisguise());
        state.raiseAlert(30);
        assertEquals(30,state.getAlert());
        assertTrue(state.isDisguised());
        assertTrue(state.toggleDisguise());
        assertFalse(state.isDisguised());
        assertTrue(state.toggleDisguise());
    }

    @Test
    void clawUpgradesSpendEpOnceAndPersistAsFlags() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.addEvolutionPoints(350);

        assertTrue(state.buyClawUpgrade(ClawUpgrade.RANGE));
        assertTrue(state.hasClawUpgrade(ClawUpgrade.RANGE));
        assertEquals(200, state.getEvolutionPoints());
        assertFalse(state.buyClawUpgrade(ClawUpgrade.RANGE));
        assertTrue(state.buyClawUpgrade(ClawUpgrade.DAMAGE));
        assertEquals(0, state.getEvolutionPoints());
    }

    @Test
    void movementUpgradesSpendEpRespectTierOrderAndPersist() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.addEvolutionPoints(500);

        // Tier II refuses to sell before tier I.
        assertFalse(state.buyMovementUpgrade(MovementUpgrade.JUMP_2));
        assertTrue(state.buyMovementUpgrade(MovementUpgrade.JUMP_1));
        assertTrue(state.buyMovementUpgrade(MovementUpgrade.JUMP_2));
        assertEquals(200, state.getEvolutionPoints());
        assertFalse(state.buyMovementUpgrade(MovementUpgrade.JUMP_2));
        assertTrue(state.hasMovementUpgrade(MovementUpgrade.JUMP_2));
        assertFalse(state.hasMovementUpgrade(MovementUpgrade.JUMP_3));
    }

    @Test
    void movementUpgradesSurviveTheCodecRoundTrip() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.addEvolutionPoints(600);
        state.buyMovementUpgrade(MovementUpgrade.SPRINT_1);
        state.buyMovementUpgrade(MovementUpgrade.AIR_DASH_PLUS);

        var encoded = BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow(false, message -> { throw new AssertionError(message); });
        BlacklightState decoded = BlacklightState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, message -> { throw new AssertionError(message); });

        assertTrue(decoded.hasMovementUpgrade(MovementUpgrade.SPRINT_1));
        assertTrue(decoded.hasMovementUpgrade(MovementUpgrade.AIR_DASH_PLUS));
        assertEquals(250, decoded.getEvolutionPoints());
    }

    @Test
    void curingAlwaysReturnsToVanillaMode() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.setPrototypeMode(true);

        state.cure();

        assertFalse(state.isInfected());
        assertFalse(state.isPrototypeMode());
        assertEquals(PowerForm.NONE, state.getActivePower());
        assertFalse(state.hasMovementUpgrade(MovementUpgrade.JUMP_1));
    }

    @Test
    void prototypeModeSurvivesPersistentCodecRoundTrip() {
        BlacklightState state = new BlacklightState();
        state.infect();
        state.setPrototypeMode(true);

        var encoded = BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow(false, message -> { throw new AssertionError(message); });
        BlacklightState decoded = BlacklightState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, message -> { throw new AssertionError(message); });

        assertTrue(decoded.isPrototypeMode());
        assertEquals(PowerForm.CLAWS, decoded.getActivePower());
    }

    @Test
    void legacySaveWithoutModeFieldLoadsInSafeVanillaMode() {
        var legacy = JsonParser.parseString("""
                {"infected":true,"biomass":250,"unlocked":1,"active_power":"claws"}
                """);

        BlacklightState decoded = BlacklightState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow(false, message -> { throw new AssertionError(message); });

        assertTrue(decoded.isInfected());
        assertFalse(decoded.isPrototypeMode());
        assertEquals(PowerForm.NONE, decoded.getActivePower());
    }
}
