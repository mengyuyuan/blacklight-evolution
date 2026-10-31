package com.codex.blacklight.infection;
import com.codex.blacklight.combat.EvolvedAttack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EvolvedAttackTest {
    @Test void telegraphsPrecedeTheMatchingBladeWindowAndIdleNeverDamages(){
        assertTrue(EvolvedAttack.charge("thrust_left",10)>.5);
        assertEquals(-1,EvolvedAttack.contact("thrust_left",10));
        assertTrue(EvolvedAttack.trail("thrust_left",20,1));
        assertFalse(EvolvedAttack.trail("thrust_left",20,0));
        for(String clip:new String[]{"guard","rush","idle_shrug","taunt_sharpen","taunt_beckon","dodge_left"})
            for(int tick=0;tick<100;tick++){assertEquals(-1,EvolvedAttack.contact(clip,tick));assertFalse(EvolvedAttack.trail(clip,tick,0));}
        assertEquals(0,EvolvedAttack.contact("slash",8));assertEquals(-1,EvolvedAttack.contact("slash",20));assertEquals(1,EvolvedAttack.contact("slash",35));
    }
}
