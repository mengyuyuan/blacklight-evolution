package com.codex.blacklight.state;

import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.MovementUpgrade;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatSkillStateTest {
    @Test void secondRegenerationTierAppendsABitAndPreservesLegacyLearning() {
        assertEquals(1<<18,CombatSkill.TISSUE_REGENERATION_PERCENT.bit());
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(2000);
        assertFalse(state.buyCombatSkill(CombatSkill.TISSUE_REGENERATION_PERCENT));
        assertTrue(state.buyCombatSkill(CombatSkill.TISSUE_REGENERATION));
        assertTrue(state.buyCombatSkill(CombatSkill.TISSUE_REGENERATION_PERCENT));
        var encoded=BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow(false, message -> { throw new AssertionError(message); });
        var copy=BlacklightState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertTrue(copy.hasCombatSkill(CombatSkill.TISSUE_REGENERATION));
        assertTrue(copy.hasCombatSkill(CombatSkill.TISSUE_REGENERATION_PERCENT));
        assertEquals(1150,copy.getEvolutionPoints());
    }
    @Test void criticalMassBoostsRequireEachPriorTierAndAddStorage() {
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(3000);
        assertFalse(state.buyCombatSkill(CombatSkill.CRITICAL_MASS_BOOST_2));
        assertEquals(3000,state.getEvolutionPoints());
        assertTrue(state.buyCombatSkill(CombatSkill.CRITICAL_MASS));
        assertEquals(1,CombatSkill.criticalMassCapacity(state.getCombatSkillMask()));
        assertTrue(state.buyCombatSkill(CombatSkill.CRITICAL_MASS_BOOST_1));
        assertEquals(2,CombatSkill.criticalMassCapacity(state.getCombatSkillMask()));
        assertTrue(state.buyCombatSkill(CombatSkill.CRITICAL_MASS_BOOST_2));
        assertEquals(3,CombatSkill.criticalMassCapacity(state.getCombatSkillMask()));
        assertTrue(state.buyCombatSkill(CombatSkill.CRITICAL_MASS_BOOST_3));
        assertEquals(4,CombatSkill.criticalMassCapacity(state.getCombatSkillMask()));
        assertEquals(1200,state.getEvolutionPoints());
    }
    @Test void bulletdiveRequiresGlideAndIsIndependentOfCriticalMass() {
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(2000);
        assertNull(CombatSkill.BULLETDIVE_DROP.requires());
        assertFalse(state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP));
        assertEquals(2000,state.getEvolutionPoints());
        assertTrue(state.buyMovementUpgrade(MovementUpgrade.GLIDE_1));
        assertTrue(state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP));
        assertEquals(1550,state.getEvolutionPoints());
        assertFalse(state.hasCombatSkill(CombatSkill.CRITICAL_MASS));
        assertFalse(state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP));
        assertEquals(1550,state.getEvolutionPoints());
    }
    @Test void purchasesRequireInfectionBaseAndPayOnlyOnce() {
        var state=new BlacklightState();state.addEvolutionPoints(2000);
        assertFalse(state.buyCombatSkill(CombatSkill.CRITICAL_MASS));
        state.infect();
        assertFalse(state.buyCombatSkill(CombatSkill.TENDRIL_BARRAGE));
        assertTrue(state.buyCombatSkill(CombatSkill.CRITICAL_MASS));
        assertTrue(state.buyCombatSkill(CombatSkill.TENDRIL_BARRAGE));
        assertEquals(1250,state.getEvolutionPoints());
        assertFalse(state.buyCombatSkill(CombatSkill.TENDRIL_BARRAGE));
        assertEquals(1250,state.getEvolutionPoints());
        assertFalse(state.buyCombatSkill(null));
    }
    @Test void skillsPersistThroughModeChangesCodecAndClearOnCure() {
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(2000);
        state.buyCombatSkill(CombatSkill.CRITICAL_MASS);state.buyCombatSkill(CombatSkill.ADRENALINE_SURGE);
        state.setPrototypeMode(true);state.setPrototypeMode(false);
        var encoded=BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow(false, message -> { throw new AssertionError(message); });
        var decoded=BlacklightState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertTrue(decoded.hasCombatSkill(CombatSkill.CRITICAL_MASS));
        assertTrue(decoded.hasCombatSkill(CombatSkill.ADRENALINE_SURGE));
        assertEquals(0,decoded.getCombatSkillMask()&~CombatSkill.validMask());
        decoded.cure();assertEquals(0,decoded.getCombatSkillMask());
    }
    @Test void legacyAndUnknownFlagsCannotCreateSkills() {
        var legacy=JsonParser.parseString("{\"infected\":true,\"prototype_mode\":true}");
        assertEquals(0,BlacklightState.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow(false, message -> { throw new AssertionError(message); }).getCombatSkillMask());
        var dirty=JsonParser.parseString("{\"infected\":true,\"combat_skill_mask\":2147483647}");
        assertEquals(CombatSkill.validMask(),BlacklightState.CODEC.parse(JsonOps.INSTANCE,dirty).getOrThrow(false, message -> { throw new AssertionError(message); }).getCombatSkillMask());
        var negative=JsonParser.parseString("{\"combat_skill_mask\":-1}");
        assertEquals(0,BlacklightState.CODEC.parse(JsonOps.INSTANCE,negative).getOrThrow(false, message -> { throw new AssertionError(message); }).getCombatSkillMask());
    }
}
