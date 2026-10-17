package com.codex.blacklight.movement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TacticalHopTest {
    @Test void invulnerabilityExpiresBeforeRecoveryAndIsRemovedByInterruption(){
        var s=new MovementState();s.beginHop();assertTrue(s.hopInvulnerable());
        for(int i=0;i<5;i++)s.tickHop();assertTrue(s.hopInvulnerable());
        s.tickHop();assertFalse(s.hopInvulnerable());assertTrue(s.hopping());
        s.beginHop();s.cancelHop();assertFalse(s.hopInvulnerable());
        s.beginHop();s.reset();assertFalse(s.hopInvulnerable());
    }
    @Test void hopClearsChargeAndHasCooldownButDoesNotConsumeAirDash(){
        var s=new MovementState();s.beginCharge();s.beginHop();
        assertEquals(0,s.chargeTicks());assertTrue(s.hopping());assertFalse(s.canHop());assertTrue(s.canAirDash());
        for(int i=0;i<19;i++)s.tickHop();assertFalse(s.hopping());assertFalse(s.canHop());
        for(int i=0;i<5;i++)s.tickHop();assertTrue(s.canHop());
        s.beginHop();s.reset();assertFalse(s.hopping());assertTrue(s.canHop());
    }
    @Test void sprintTapRequiresReleaseAndRejectsLongRunOrMenuTransition(){
        var tap=new SprintTap();
        for(int i=0;i<3;i++)assertFalse(tap.update(true,true));
        assertTrue(tap.update(false,true));assertFalse(tap.update(false,true));
        for(int i=0;i<10;i++)tap.update(true,true);
        assertTrue(tap.update(false,true));
        for(int i=0;i<11;i++)tap.update(true,true);
        assertFalse(tap.update(false,true));
        tap.update(true,true);tap.update(false,false);assertFalse(tap.update(false,true));
        tap.update(true,true);tap.reset();assertFalse(tap.update(false,true));
    }
}
