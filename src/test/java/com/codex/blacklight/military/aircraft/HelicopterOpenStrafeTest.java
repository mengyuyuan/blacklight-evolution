package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source scenarios. These cases have not been executed. */
class HelicopterOpenStrafeTest {
    private static final Vec3 FORWARD=new Vec3(0,0,1);
    private static final HelicopterOpenStrafe.Area FLAT=(point,width)->{assertEquals(20F,width);return 0;};
    private static HelicopterOpenStrafe.Route normal(){return HelicopterOpenStrafe.find(new Vec3(0,8,-20),Vec3.ZERO,false,1,FLAT);}
    @Test void ordinaryCorridorRaisesStartAndUsesStrictCellIndices(){
        var route=normal();assertNotNull(route);
        assertEquals(new Vec3(0,15,-20),route.start());assertEquals(new Vec3(0,10,0),route.aim());
        assertEquals(new Vec3(0,25,6),route.end(),"i>ceil20+4 means index25, after26 cell advances");
    }
    @Test void hydraHasItsOwnStartBandAndExtraAfterTargetCells(){
        var route=HelicopterOpenStrafe.find(new Vec3(0,30,-20),Vec3.ZERO,true,1,FLAT);
        assertNotNull(route);assertEquals(new Vec3(0,30,-20),route.start());assertEquals(new Vec3(0,25,8),route.end());
    }
    @Test void lengthFieldsAreCellCountsAndBoundTheWholeCorridor(){
        var route=HelicopterOpenStrafe.find(new Vec3(0,20,-40),Vec3.ZERO,false,2,FLAT);
        assertNotNull(route);assertEquals(new Vec3(0,25,12),route.end());
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,20,-40),Vec3.ZERO,false,1,FLAT));
    }
    @Test void startMaximumIsInclusiveAndTheDistantHeightCorrectionIsSeparate(){
        assertNotNull(HelicopterOpenStrafe.find(new Vec3(0,27,-20),Vec3.ZERO,false,1,FLAT));
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,27.01,-20),Vec3.ZERO,false,1,FLAT));
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,227,-20),Vec3.ZERO,false,1,FLAT));
        var corrected=HelicopterOpenStrafe.find(new Vec3(0,228,-20),Vec3.ZERO,false,1,FLAT);
        assertNotNull(corrected);assertEquals(27D,corrected.start().y);
    }
    @Test void highTerrainOrAnUnavailableSampleRejectsTheCandidate(){
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,20,-20),Vec3.ZERO,false,1,(point,width)->point.z==-19?31:0));
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,20,-20),Vec3.ZERO,false,1,(point,width)->point.z==-19?Float.NaN:0));
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,20,-20),Vec3.ZERO,false,1,(point,width)->30),"path equality30 is allowed, but every end needs strictly less30");
    }
    @Test void nearStartAndCorrectFacingBeginTheRunWithoutAnExtraApproach(){
        var route=normal();var craft=route.start().add(0,-3,0);
        var run=new HelicopterOpenStrafe.Run(route,craft,FORWARD);
        assertEquals(HelicopterOpenStrafe.Run.Stage.RUN,run.stage());assertEquals(craft,run.reservationStart());
    }
    @Test void approachRequiresArrivalAndAlignmentThenRunsOnceAndFacesCurrentTarget(){
        var route=normal();var run=new HelicopterOpenStrafe.Run(route,new Vec3(0,8,-20),FORWARD);
        assertEquals(HelicopterOpenStrafe.Run.Stage.PREPARE,run.stage());assertEquals(route.start(),run.destination());
        run.tick(route.start(),FORWARD.scale(-1),Vec3.ZERO,true);assertEquals(HelicopterOpenStrafe.Run.Stage.PREPARE,run.stage());
        run.tick(route.start(),FORWARD,Vec3.ZERO,false);assertEquals(HelicopterOpenStrafe.Run.Stage.PREPARE,run.stage());
        run.tick(route.start(),FORWARD,Vec3.ZERO,true);assertEquals(HelicopterOpenStrafe.Run.Stage.RUN,run.stage());
        run.tick(route.end(),FORWARD,Vec3.ZERO,true);assertEquals(HelicopterOpenStrafe.Run.Stage.ORIENT,run.stage());
        run.tick(route.end(),FORWARD,Vec3.ZERO,true);assertEquals(HelicopterOpenStrafe.Run.Stage.ORIENT,run.stage());
        run.tick(route.end(),FORWARD,new Vec3(0,0,20),false);assertEquals(HelicopterOpenStrafe.Run.Stage.DONE,run.stage());
    }
    @Test void targetLossAbortsAndDegenerateCorridorsAreNotReserved(){
        var route=normal();var run=new HelicopterOpenStrafe.Run(route,route.start(),FORWARD);
        run.tick(route.start(),FORWARD,null,false);assertEquals(HelicopterOpenStrafe.Run.Stage.ABORTED,run.stage());
        assertNull(HelicopterOpenStrafe.find(new Vec3(0,20,0),Vec3.ZERO,false,1,FLAT));
    }
}
