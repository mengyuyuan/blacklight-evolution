package com.codex.blacklight.combat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneWeaponCadenceTest {
    @Test void rejectedSpawnDoesNotConsumeCooldown(){
        var clock=new GreeneWeaponCadence();assertFalse(clock.tick(.05,true,5,()->false));assertEquals(0,clock.remainingSeconds());
        assertTrue(clock.tick(.05,true,5,()->true));assertEquals(.2,clock.remainingSeconds());
    }
    @Test void longUpdatesAndHighRatesDoNotBatchOrCarryNegativeTime(){
        var clock=new GreeneWeaponCadence();var calls=new AtomicInteger();
        assertTrue(clock.tick(1,true,150,()->{calls.incrementAndGet();return true;}));
        assertEquals(1,calls.get());assertEquals(1.0/150,clock.remainingSeconds());
        assertTrue(clock.tick(1,true,150,()->{calls.incrementAndGet();return true;}));assertEquals(2,calls.get());
    }
    @Test void endingTrackDoesNotEraseWeaponCooldown(){
        var clock=new GreeneWeaponCadence();assertTrue(clock.tick(.05,true,.2,()->true));
        assertFalse(clock.tick(.5,false,150,()->{fail("Inactive window must not fire");return true;}));
        assertEquals(4.5,clock.remainingSeconds());var restored=new GreeneWeaponCadence();restored.restore(clock.remainingSeconds());
        assertFalse(restored.tick(.05,true,150,()->{fail("Profile change must not skip cooldown");return true;}));
        assertEquals(4.45,restored.remainingSeconds(),1e-9);
    }
    @Test void binaryRoundoffDoesNotAddAWholeServerUpdate(){
        var clock=new GreeneWeaponCadence();assertTrue(clock.tick(.05,true,5,()->true));
        for(int i=0;i<3;i++)assertFalse(clock.tick(.05,true,5,()->true));
        assertTrue(clock.tick(.05,true,5,()->true));
    }
}
