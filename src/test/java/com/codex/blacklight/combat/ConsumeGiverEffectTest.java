package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeGiverEffectTest {
    @Test void bodyStartsAtItsOwnSourceCallback(){
        assertEquals(55/30F,ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.DEFAULT),.00001);
        assertEquals(130/30F,ConsumeGiverEffect.cue(PowerForm.CLAWS,ConsumeVariant.CLASSIC),.00001);
        assertEquals(88/30F,ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.BEATDOWN),.00001);
        assertTrue(Float.isNaN(ConsumeGiverEffect.cue(PowerForm.NONE,ConsumeVariant.STEALTH_NECK)));
    }
    @Test void theActorMaterialReversesIndependentlyOfTheReceiver(){
        assertEquals(0,ConsumeGiverEffect.reach(-.01F));
        assertEquals(.35F,ConsumeGiverEffect.reach(1),1e-5F);
        assertEquals(2.3F*.35F,ConsumeGiverEffect.reach(2.3F),1e-5F);
        assertEquals(ConsumeGiverEffect.reach(2.0F),ConsumeGiverEffect.reach(2.6F),1e-5F);
        assertEquals(0,ConsumeGiverEffect.reach(ConsumeGiverEffect.END_SECONDS));
    }
}
