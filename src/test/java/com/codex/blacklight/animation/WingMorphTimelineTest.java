package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WingMorphTimelineTest {
    @Test void rootGrowsBeforeTheDistalBladesAndEverythingFinishes(){
        assertTrue(WingMorphTimeline.growth("left_root",.3F)>0);
        assertEquals(0,WingMorphTimeline.growth("left_tip_1",.3F));
        for(String bone:new String[]{"left_root","left_rib_1","left_arch_1","left_finger_1","left_tip_1"}){
            assertEquals(0,WingMorphTimeline.growth(bone,0));
            assertEquals(1,WingMorphTimeline.growth(bone,1));
        }
    }
    @Test void transformationEndsWithoutHoldingTheBodyOrWingPose(){
        assertEquals(1,WingMorphTimeline.progress(true,60));assertEquals(0,WingMorphTimeline.progress(false,60));
        assertTrue(WingMorphTimeline.unfurl(true,18)>.4F);
        assertEquals(0,WingMorphTimeline.unfurl(true,60));
        assertEquals(0,WingMorphTimeline.bodyWeight(true,60));assertEquals(0,WingMorphTimeline.bodyWeight(false,60));
    }
}
