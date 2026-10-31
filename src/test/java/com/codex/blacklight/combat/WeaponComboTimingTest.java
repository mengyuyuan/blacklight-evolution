package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponComboTimingTest {
    @Test void basicLinksKeepEveryActiveContactAndACompleteNextAttack() {
        for(var move:WeaponMove.values()) {
            assertTrue(move.comboBufferTick()>0&&move.comboBufferTick()<=move.start,move.id);
            assertTrue(move.comboLinkTick()>=move.end,move.id+" cannot skip contact");
            var next=move.nextAttack()!=null?move.nextAttack():move.nextSpecial();
            if(next==null){assertEquals(move.duration,move.comboLinkTick());continue;}
            assertTrue(move.comboLinkTick()<=move.duration,move.id+" link cannot outlive recovery");
            // Same-owner combos have their own hit ledger; they deliberately
            // bypass that owner's preceding hurt cooldown (covered in GameTests).
            assertTrue(move.comboLinkTick()+next.start>move.end,move.id+" contacts cannot overlap");
            assertNotEquals(move,next);
            assertTrue(move.form==next.form||next.form==com.codex.blacklight.power.PowerForm.NONE
                            ||move==WeaponMove.COMBO_KICK1&&next==WeaponMove.MUSCLE_COMBO_C,
                    move.id+" can link only to its own form or a shared body combo");
        }
    }

    @Test void nativeAttackChainsEndInsteadOfLoopingTwoPoses(){
        assertChain(WeaponMove.BLADE_CUT,WeaponMove.BLADE_RETURN,WeaponMove.BLADE_COMBO_C,WeaponMove.BLADE_COMBO_C2,WeaponMove.BLADE_COMBO_D);
        assertChain(WeaponMove.HAMMER_JAB,WeaponMove.HAMMER_HOOK,WeaponMove.HAMMER_COMBO_C,WeaponMove.HAMMER_COMBO_D);
        assertChain(WeaponMove.WHIP_LASH,WeaponMove.WHIP_RETURN,WeaponMove.WHIP_COMBO_C);
        assertEquals(WeaponMove.WHIP_COMBO_C,WeaponMove.WHIP_RETURN_CHARGED.nextAttack());
    }
    private static void assertChain(WeaponMove... moves){
        for(int i=0;i<moves.length-1;i++)assertEquals(moves[i+1],moves[i].nextAttack());
        assertNull(moves[moves.length-1].nextAttack());
    }

    @Test void specialActionsRetainTheirCompleteCommitment() {
        for(var move:WeaponMove.values())if(!move.basicCombo())
            assertEquals(move.duration,move.comboLinkTick(),move.id);
    }
}
