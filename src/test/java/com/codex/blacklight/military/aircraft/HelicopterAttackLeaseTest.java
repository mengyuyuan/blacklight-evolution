package com.codex.blacklight.military.aircraft;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-derived boundary cases, intentionally not executed in the code-only investigation phase. */
final class HelicopterAttackLeaseTest {
    private static final UUID FIRST=new UUID(1,1),SECOND=new UUID(2,2);

    @Test void availabilityOnAFreeTokenDoesNotAcquireOrStartATimer(){
        var lease=new HelicopterAttackLease();
        assertTrue(lease.available(FIRST,id->true));assertTrue(lease.available(SECOND,id->true));
        assertNull(lease.owner());assertEquals(-1F,lease.remainingSeconds());
        lease.advance(10);assertNull(lease.owner());assertEquals(-1F,lease.remainingSeconds());
    }
    @Test void aValidDifferentOwnerBlocksChecksRequestsAndRefreshesWithoutExtendingItsLease(){
        var lease=new HelicopterAttackLease();assertTrue(lease.request(FIRST,id->true));lease.advance(.125F);
        assertFalse(lease.available(SECOND,id->true));assertFalse(lease.request(SECOND,id->true));assertFalse(lease.refresh(SECOND,id->true));
        assertEquals(FIRST,lease.owner());assertEquals(.375F,lease.remainingSeconds());
    }
    @Test void sameOwnerRefreshAndRequestReplaceDurationRatherThanTakeItsMaximum(){
        var lease=new HelicopterAttackLease();assertTrue(lease.request(FIRST,id->true));lease.advance(.25F);
        assertTrue(lease.available(FIRST,id->true));assertEquals(.25F,lease.remainingSeconds());
        assertTrue(lease.refresh(FIRST,id->true));assertEquals(2F,lease.remainingSeconds());
        assertTrue(lease.request(FIRST,id->true));assertEquals(.5F,lease.remainingSeconds());
    }
    @Test void exactZeroStillBelongsToItsOwnerUntilAnotherPositiveSimulationStep(){
        var lease=new HelicopterAttackLease();lease.request(FIRST,id->true);lease.advance(.5F);
        assertEquals(0F,lease.remainingSeconds());assertEquals(FIRST,lease.owner());assertFalse(lease.available(SECOND,id->true));
        lease.advance(0);assertFalse(lease.request(SECOND,id->true));
        lease.advance(.125F);assertEquals(-.125F,lease.remainingSeconds());assertNull(lease.owner());assertTrue(lease.request(SECOND,id->true));
    }
    @Test void staleOwnerPermitsChecksWithoutWritingAndPositiveRequestReplacesIt(){
        var lease=new HelicopterAttackLease();lease.refresh(FIRST,id->true);
        assertTrue(lease.available(SECOND,id->false));assertEquals(FIRST,lease.owner());assertEquals(2F,lease.remainingSeconds());
        assertTrue(lease.request(SECOND,id->false));assertEquals(SECOND,lease.owner());assertEquals(.5F,lease.remainingSeconds());
    }
    @Test void replacementEntityIdentityDoesNotInheritAnOldGenerationReservation(){
        var lease=new HelicopterAttackLease();lease.request(FIRST,id->true);
        assertTrue(lease.refresh(SECOND,SECOND::equals));assertEquals(SECOND,lease.owner());assertEquals(2F,lease.remainingSeconds());
        assertFalse(lease.request(FIRST,SECOND::equals));
    }
    @Test void separateWorldCoordinatorsDoNotShareOneJavaGlobalOwner(){
        var firstWorld=new HelicopterAttackLease();var secondWorld=new HelicopterAttackLease();
        assertTrue(firstWorld.request(FIRST,id->true));assertTrue(secondWorld.request(SECOND,id->true));
        firstWorld.advance(1);assertNull(firstWorld.owner());assertEquals(SECOND,secondWorld.owner());assertEquals(.5F,secondWorld.remainingSeconds());
    }
    @Test void invalidMinecraftDeltaCannotPoisonOrLengthenAReservation(){
        var lease=new HelicopterAttackLease();lease.request(FIRST,id->true);
        for(float dt:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.25F})
            assertThrows(IllegalArgumentException.class,()->lease.advance(dt));
        assertEquals(FIRST,lease.owner());assertEquals(.5F,lease.remainingSeconds());
    }
}
