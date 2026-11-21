package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeReturnSoundTest {
    @Test void everyPairedReceiverStartsItsReturnSoundBeforeSettlement() {
        for (var form : new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,
                PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST}) {
            for (int variant : ConsumeVariant.variants(form)) {
                int ticks=ConsumeSequence.duration(form,false,variant);
                int finish=ConsumeSequence.completionTick(form,false,variant);
                int cue=ConsumeSequence.returnSoundTick(form,variant,ConsumeVisual.CONSUME,ticks,finish);
                assertTrue(cue>0 && cue<finish,ConsumeSequence.key(form,variant));
            }
            int ticks=ConsumeSequence.duration(form,true);
            int finish=ConsumeSequence.completionTick(form,true);
            int cue=ConsumeSequence.returnSoundTick(form,0,ConsumeVisual.MOUNT_CONSUME,ticks,finish);
            assertEquals(0,ConsumeSequence.mountedReturn(cue-1,ticks,finish));
            assertTrue(ConsumeSequence.mountedReturn(cue+1,ticks,finish)>0 && cue<finish);
        }
        assertEquals(30,ConsumeSequence.returnSoundTick(PowerForm.NONE,0,ConsumeVisual.HUNTER_CONSUME,
                HunterConsume.ACTOR_TICKS,HunterConsume.COMPLETE_TICKS));
    }
}
