package com.codex.blacklight.military;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UavReconTest {
    private static final UUID TARGET=UUID.fromString("00000000-0000-0000-0000-000000000001"),OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static UavRecon scan(){var r=new UavRecon();for(int i=0;i<60;i++)r.tick(TARGET,true,true);return r;}
    @Test void detectionPrecedesASeparateInterruptibleTransmission(){
        var r=new UavRecon();for(int i=0;i<59;i++)assertEquals(UavRecon.Event.NONE,r.tick(TARGET,true,true));
        assertEquals(UavRecon.Event.DETECTED,r.tick(TARGET,true,true));assertEquals(1,r.signal());assertFalse(r.ready(TARGET));
        for(int i=0;i<38;i++)assertEquals(UavRecon.Event.NONE,r.tick(TARGET,true,true));
        assertEquals(UavRecon.Event.REQUEST_SUPPORT,r.tick(TARGET,true,true));assertTrue(r.ready(TARGET));assertFalse(r.ready(OTHER));
    }
    @Test void occlusionImmediatelyCancelsThePendingTransmission(){
        var r=scan();for(int i=0;i<20;i++)r.tick(TARGET,true,true);r.tick(TARGET,false,true);
        assertEquals(56,r.scan());assertEquals(0,r.signal());assertFalse(r.ready(TARGET));
        for(int i=0;i<38;i++)assertNotEquals(UavRecon.Event.REQUEST_SUPPORT,r.tick(TARGET,true,true));
    }
    @Test void anotherTargetCannotInheritAcquisitionOrMissItsOwnDetection(){
        var r=scan();for(int i=0;i<59;i++)assertEquals(UavRecon.Event.NONE,r.tick(OTHER,true,true));
        assertEquals(59,r.scan());assertEquals(UavRecon.Event.DETECTED,r.tick(OTHER,true,true));assertFalse(r.ready(OTHER));
    }
    @Test void DisabledAndDamageInterruptionsNeverCreateDelayedRequests(){
        var r=scan();r.tick(TARGET,true,false);assertEquals(0,r.scan());assertEquals(0,r.signal());
        r=scan();r.interrupt();assertFalse(r.ready(TARGET));assertEquals(0,r.scan());
        assertEquals(UavRecon.Event.NONE,r.tick(null,false,true));
    }
    @Test void successfulSortieBlocksNewCallsButKeepsDetectionAlive(){
        var r=scan();r.dispatched(true);int detections=0;
        for(int i=0;i<3599;i++){var event=r.tick(TARGET,true,true);assertNotEquals(UavRecon.Event.REQUEST_SUPPORT,event);if(event==UavRecon.Event.DETECTED)detections++;}
        assertTrue(detections>20);assertEquals(1,r.supportWait());assertEquals(0,r.signal());
    }
    @Test void failedDispatchRetriesAfterABoundedBackoffAndFreshTransmission(){
        var r=scan();r.dispatched(false);
        for(int i=0;i<238;i++)assertNotEquals(UavRecon.Event.REQUEST_SUPPORT,r.tick(TARGET,true,true));
        assertEquals(UavRecon.Event.REQUEST_SUPPORT,r.tick(TARGET,true,true));
    }
    @Test void reloadPreservesCooldownWithoutResumingAStaleTarget(){
        var r=scan();r.restoreCooldowns(83,1250);assertEquals(0,r.scan());assertEquals(0,r.signal());assertEquals(1250,r.supportWait());assertEquals(83,r.reportWait());assertFalse(r.ready(TARGET));
        r.restoreCooldowns(-1,Integer.MAX_VALUE);assertEquals(0,r.reportWait());assertEquals(3600,r.supportWait());
    }
    @Test void enclosedTargetsCannotStartOrInheritAnIncompleteViralScan(){
        var r=new UavRecon();for(int i=0;i<30;i++)r.tick(TARGET,true,true);
        assertFalse(r.trackRecognized(TARGET,true,true));assertEquals(0,r.scan());assertEquals(0,r.signal());
        for(int i=0;i<120;i++)assertFalse(r.trackRecognized(TARGET,true,true));
        r=scan();assertFalse(r.trackRecognized(OTHER,true,true));assertEquals(0,r.scan());
    }
    @Test void recognizedVehicleContactKeepsAlertButCancelsGroundTransmissionAndExpiresOnOcclusion(){
        var r=scan();for(int i=0;i<20;i++)r.tick(TARGET,true,true);assertTrue(r.signal()>0);
        for(int i=0;i<120;i++){assertTrue(r.trackRecognized(TARGET,true,true));assertEquals(60,r.scan());assertEquals(0,r.signal());assertFalse(r.ready(TARGET));}
        assertFalse(r.trackRecognized(TARGET,false,true));assertEquals(0,r.scan());
        assertFalse(r.trackRecognized(TARGET,true,true));
    }
    @Test void followingKnownVehicleStillConsumesCooldownAndCannotSurviveDisable(){
        var r=scan();r.dispatched(true);for(int i=0;i<100;i++)assertTrue(r.trackRecognized(TARGET,true,true));assertEquals(3500,r.supportWait());
        assertFalse(r.trackRecognized(TARGET,true,false));assertEquals(0,r.scan());assertEquals(3499,r.supportWait());
    }
}
