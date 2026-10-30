package com.codex.blacklight.combat;

import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShieldBorerTest {
    @Test void upgradesAreSequentialAndPersistWithOriginalSkillBits(){
        var state=new BlacklightState();state.infect();state.unlock(PowerForm.SHIELD);state.addEvolutionPoints(1050);
        assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_1));
        assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2));
        assertEquals(0,ShieldBorerSystem.tier(state));
        assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER));
        assertEquals(1,ShieldBorerSystem.tier(state));
        assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2));
        assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_1));
        assertEquals(2,ShieldBorerSystem.tier(state));
        assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_1));
        assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2));
        assertEquals(0,state.getEvolutionPoints());
        var json=BlacklightState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,state).getOrThrow(false, message -> { throw new AssertionError(message); });
        var loaded=BlacklightState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertEquals(3,ShieldBorerSystem.tier(loaded));
        assertFalse(loaded.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2));
    }
    @Test void upgradesExtendForwardWithoutWideningOrLoweringTheCorridor(){
        var feet=new Vec3(1.5,6.5,1.5);
        for(float yaw:new float[]{0,45,90,135,180,270}){
            var previous=ShieldBorerSystem.candidates(feet,yaw,0);
            for(int tier=1;tier<=3;tier++){
                var positions=ShieldBorerSystem.candidates(feet,yaw,tier);
                assertTrue(positions.containsAll(previous));
                assertTrue(positions.size()<=30);
                for(var at:positions){
                    assertTrue(at.getY()>=6&&at.getY()<=8);
                    assertTrue(Vec3.atCenterOf(at).subtract(feet).horizontalDistance()<3.3);
                }
                previous=positions;
            }
        }
        var far=net.minecraft.core.BlockPos.containing(feet.add(0,0,2.45));
        assertFalse(ShieldBorerSystem.candidates(feet,0,1).contains(far));
        assertTrue(ShieldBorerSystem.candidates(feet,0,3).contains(far));
        assertEquals(6,ShieldBorerSystem.breakLimit(1));
        assertEquals(9,ShieldBorerSystem.breakLimit(2));
        assertEquals(12,ShieldBorerSystem.breakLimit(3));
    }
    @Test void corridorFollowsFacingAndNeverRemovesSupport(){
        for(float yaw:new float[]{0,45,90,180,270}){
            var feet=new Vec3(1.5,6,1.5);var forward=Vec3.directionFromRotation(0,yaw);
            var positions=ShieldBorerSystem.candidates(feet,yaw);
            assertTrue(positions.size()<=12);
            for(var at:positions){
                assertTrue(at.getY()>=6&&at.getY()<=7);
                assertTrue(Vec3.atCenterOf(at).subtract(feet).dot(forward)>=-.01);
                assertTrue(Vec3.atCenterOf(at).subtract(feet).horizontalDistance()<2.2);
            }
        }
    }
    @Test void purchaseRequiresShieldAndCannotChargeTwice(){
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(1000);
        assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER));assertEquals(1000,state.getEvolutionPoints());
        state.unlock(PowerForm.SHIELD);assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER));
        assertEquals(650,state.getEvolutionPoints());assertFalse(state.buyCombatSkill(CombatSkill.SHIELD_BORER));
        assertEquals(650,state.getEvolutionPoints());assertTrue(state.hasCombatSkill(CombatSkill.SHIELD_BORER));
    }
    @Test void whipAttachmentIsExactButDistantDamagePathIsUnchanged(){
        var source=java.util.List.of(Vec3.ZERO,new Vec3(0,0,.1),new Vec3(0,0,.8),new Vec3(0,0,2),new Vec3(0,0,5));
        var hand=new Vec3(.3,1,.2);var attached=WhipCurveContact.attachHand(source,hand);
        assertEquals(hand,attached.get(0));assertEquals(source.get(source.size()-1),attached.get(attached.size()-1));
        assertEquals(source.get(3),attached.get(3));assertEquals(Vec3.ZERO,source.get(0));
    }
}
