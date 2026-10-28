package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedComboTest {
    @Test void ThreePartPressureAlwaysEndsWithAnActionableRecovery(){
        assertEquals("thrust_right",EvolvedCombo.followup("thrust_left",1,true,1.5,true));
        assertEquals("slash",EvolvedCombo.followup("thrust_right",2,true,1.5,true));
        assertEquals("",EvolvedCombo.followup("slash",3,true,1.5,true));
        assertEquals("",EvolvedCombo.followup("thrust_left",3,true,1.5,true));
        assertTrue(EvolvedCombo.recovery(3)>=18);
    }
    @Test void AWhiffDodgeObstructionOrLostTargetCannotGenerateAnAutomaticHitChain(){
        assertEquals("",EvolvedCombo.followup("thrust_left",1,false,1,true));
        assertEquals("",EvolvedCombo.followup("thrust_left",1,true,3,true));
        assertEquals("",EvolvedCombo.followup("thrust_left",1,true,1,false));
        assertEquals("",EvolvedCombo.followup("dodge_left",0,true,1,true));
        assertEquals("",EvolvedCombo.followup("leap",0,true,1,true));
    }
}
