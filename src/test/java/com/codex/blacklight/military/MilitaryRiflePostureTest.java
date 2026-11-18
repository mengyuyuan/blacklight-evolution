package com.codex.blacklight.military;

import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryRiflePostureTest {
    private static DoubleSupplier samples(double... values){
        int[] index={0};return ()->{assertTrue(index[0]<values.length,"unexpected posture resampling");return values[index[0]++];};
    }
    @Test void originalThreeToOneBoundaryAndWaitRangesAreRetained(){
        var clock=new MilitaryRiflePosture();
        assertEquals(MilitaryRiflePosture.Stance.STANDING,clock.choose(100,()->true,()->true,samples(.75,0)));
        assertEquals(160,clock.until());clock.clear();
        assertEquals(MilitaryRiflePosture.Stance.CROUCHING,clock.choose(100,()->true,()->true,samples(.7501,.999999)));
        assertEquals(220,clock.until());
    }
    @Test void roofEdgeExcludesCrouchBeforeDrawingAndLowLineUsesStandingFallback(){
        var clock=new MilitaryRiflePosture();
        assertEquals(MilitaryRiflePosture.Stance.STANDING,clock.choose(0,()->false,()->{fail("edge-excluded crouch must not probe LoF");return true;},samples(.99,0)));
        clock.clear();
        assertEquals(MilitaryRiflePosture.Stance.STANDING,clock.choose(0,()->true,()->false,samples(.99,.999999)));
        assertEquals(80,clock.until());
    }
    @Test void eligibleConditionRunsAgainOnTheBanksSelectionPass(){
        var clock=new MilitaryRiflePosture();int[] calls={0};
        assertNull(clock.choose(0,()->++calls[0]==1,()->{fail("invalid second pass cannot descend");return true;},samples(.99)));
        assertEquals(2,calls[0]);assertFalse(clock.active(0));
    }
    @Test void ordinaryBurstRecoveryDoesNotRerollPostureButCancelDoes(){
        var clock=new MilitaryRiflePosture();clock.choose(10,()->true,()->true,samples(.9,0));
        assertEquals(MilitaryRiflePosture.Stance.CROUCHING,clock.choose(89,()->{fail("active wait does not rerun bank");return false;},()->false,samples()));
        assertEquals(MilitaryRiflePosture.Stance.STANDING,clock.choose(90,()->true,()->true,samples(.1,0)));
        clock.clear();assertFalse(clock.active(90));
        assertEquals(MilitaryRiflePosture.Stance.CROUCHING,clock.choose(91,()->true,()->true,samples(.9,0)));
        assertEquals(91,clock.started());
    }
    @Test void changingCoverCancelsTheKneelForAWholeStandingWait(){
        var clock=new MilitaryRiflePosture();clock.choose(0,()->true,()->true,samples(.9,0));
        clock.stand(12,samples(0));assertEquals(MilitaryRiflePosture.Stance.STANDING,clock.stance());
        assertTrue(clock.active(71));assertFalse(clock.active(72));
        assertThrows(IllegalArgumentException.class,()->clock.choose(72,()->true,()->true,samples(Double.NaN)));
    }
}
