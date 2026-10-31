package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommonInfectedMotionTest{
    @Test void sharedRendererSamplingCannotLeakBetweenCitizensOrDependOnFrameOrder(){
        var expected=CommonInfectedMotion.sample(481.25,2,.7F,true,11,1);
        for(int i=0;i<400;i++)CommonInfectedMotion.sample(i,i*.17F,.2F,false,i%27,i%2);
        assertEquals(expected,CommonInfectedMotion.sample(481.25,2,.7F,true,11,1));
        assertNotEquals(expected,CommonInfectedMotion.sample(481.25,2,.7F,true,11,0));
    }
    @Test void windupAndRecoveryJoinLocomotionWithoutSnapping(){
        var idle=CommonInfectedMotion.sample(200,1,.3F,false,-1,0);
        assertEquals(idle,CommonInfectedMotion.sample(200,1,.3F,false,0,0));
        assertEquals(idle,CommonInfectedMotion.sample(200,1,.3F,false,26,0));
        var near=CommonInfectedMotion.sample(200,1,.3F,false,25.999F,0);
        assertEquals(idle.armR(),near.armR(),.0001);assertEquals(idle.torso(),near.torso(),.0001);
        var contact=CommonInfectedMotion.sample(200,1,0,false,CommonInfectedMotion.CONTACT_TICK,0);
        assertTrue(contact.armR()<-1.4&&contact.elbowR()>-.3,"damage frame is the extended arm, after visible anticipation");
    }
    @Test void idleDoesNotHoldBothArmsForwardAndClothesRemainStableAcrossUuidReload(){
        var idle=CommonInfectedMotion.sample(8,0,0,false,-1,0);
        assertTrue(Math.abs(idle.armR())<.3&&Math.abs(idle.armL())<.3);
        var all=new java.util.HashSet<Integer>();for(int i=0;i<100;i++)all.add(CommonInfectedMotion.appearance(0,i));
        assertEquals(java.util.Set.of(0,1,2,3),all);
        assertEquals(CommonInfectedMotion.appearance(-41,19),CommonInfectedMotion.appearance(-41,19));
    }
}
