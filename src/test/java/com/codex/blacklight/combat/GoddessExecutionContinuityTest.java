package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.combat.GoddessEncounter.Action.*;

class GoddessExecutionContinuityTest {
    @Test void confirmationContinuesTheReachAtEveryGripDuration() {
        for(int held=0;held<100;held++){
            int lead=Math.min(6,held);
            assertEquals(GoddessEncounter.actorBlend(GRIPPED,0,held),
                    GoddessEncounter.actorBlend(EXTRACTING,lead,0),1e-6F);
            assertEquals(1F,GoddessEncounter.actorBlend(EXTRACTING,lead,12),1e-6F);
            assertEquals(1F,GoddessEncounter.actorBlend(EXTRACTING,lead,128),1e-6F);
            assertEquals(0F,GoddessEncounter.actorBlend(EXTRACTING,lead,GoddessEncounter.EXTRACTION_TICKS),1e-6F);
        }
    }

    @Test void risingStartsOnTheReleasedJointTransformsFromTheShippedClips() {
        for(int stage=0;stage<2;stage++){
            for(int j=0;j<GoddessRig.JOINTS;j++){
                int removed=stage==0 ? GoddessVitals.LEFT_ARM : GoddessVitals.LEFT_ARM | GoddessVitals.RIGHT_ARM;
                if((GoddessRig.severedPart(j)&removed)!=0)continue;
                var released=GoddessRig.executionOffset(stage,j,80+GoddessEncounter.EXTRACTION_TICKS*1.2F);
                assertTrue(released.equals(GoddessRig.risingOffset(stage,j,80,0),1e-5F));
                for(float t=.25F;t<12;t+=.25F)
                    assertTrue(GoddessRig.risingOffset(stage,j,80-t*1.58F,t).isFinite());
                float frame=80-12*1.58F;
                assertTrue(GoddessRig.executionOffset(stage,j,frame).equals(GoddessRig.risingOffset(stage,j,frame,12),1e-5F));
            }
        }
    }
}
