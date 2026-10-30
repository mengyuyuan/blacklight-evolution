package com.codex.blacklight.infection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InfectedSoundBudgetTest {
    @Test void aTumorCannotCreateSixSimultaneousFullVolumeContacts(){
        var b=new InfectedSoundBudget();for(int i=0;i<6;i++)assertTrue(b.accept(4));
        for(int i=0;i<100;i++)assertFalse(b.accept(4));assertTrue(b.accept(5));
    }
    @Test void aSwarmCannotFillEveryAudioVoiceAndDoesNotQueueOldHits(){
        var b=new InfectedSoundBudget();int accepted=0;for(long t=0;t<20;t++)for(int i=0;i<100;i++)if(b.accept(t))accepted++;
        assertEquals(32,accepted);assertTrue(b.accept(20));
    }
    @Test void aWorldClockRollbackDoesNotSilenceAnArea(){var b=new InfectedSoundBudget();for(int i=0;i<6;i++)b.accept(90);assertTrue(b.accept(1));}
}
