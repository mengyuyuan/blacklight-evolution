package com.codex.blacklight.combat;
import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatFeedbackTest {
    @Test void nineSweeperContactsAreFiniteAndOnlyBelongToSweeper(){
        for(var move:new WeaponMove[]{WeaponMove.WHIP_SWEEP,WeaponMove.WHIP_SWEEP_CHARGED}){
            var contacts=new java.util.HashSet<Integer>();
            for(int tick=move.start;tick<move.end;tick++)contacts.add(move.sweeperPulse(tick));
            assertEquals(9,contacts.size());
            assertEquals(-1,move.sweeperPulse(move.start-1));assertEquals(-1,move.sweeperPulse(move.end));
        }
        assertEquals(-1,WeaponMove.WHIP_LASH.sweeperPulse(WeaponMove.WHIP_LASH.start));
    }
    @Test void muscleEnhancesBodyTechniquesWithoutDoublingNativePunchesOrDevastators(){
        assertEquals(WeaponMove.AIR_AUTO1.damage*2,WeaponMove.AIR_AUTO1.damageFor(PowerForm.MUSCLE_MASS));
        assertEquals(WeaponMove.MUSCLE_JAB.damage,WeaponMove.MUSCLE_JAB.damageFor(PowerForm.MUSCLE_MASS));
        assertEquals(WeaponMove.DEV_TENDRIL.damage,WeaponMove.DEV_TENDRIL.damageFor(PowerForm.MUSCLE_MASS));
    }
}
