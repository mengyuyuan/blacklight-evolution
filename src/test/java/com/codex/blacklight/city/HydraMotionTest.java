package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HydraMotionTest {
    @Test void allTransitFramesKeepFiniteGripAndTransforms(){
        var motion=HydraMotion.INSTANCE;
        for(int action:new int[]{0,2,3,4,5,6,7,8,9,10})for(float t=0;t<=150;t+=.25F){
            var pose=motion.sample(action,t);
            assertTrue(motion.grip(pose).isFinite(),"invalid grip at "+action+":"+t);
            for(var matrix:motion.transforms(pose))assertTrue(matrix.isFinite());
        }
    }
    @Test void eachTransitionStartsAtThePreviousRenderedPose(){
        var motion=HydraMotion.INSTANCE;
        int[][] transitions={{2,80,6},{6,36,5},{7,80,8},{8,140,4},{4,36,5},{5,80,0}};
        for(var transition:transitions){
            var previous=motion.grip(motion.sample(transition[0],transition[1]));
            var beginning=motion.grip(motion.pose(transition[2],0,transition[0],transition[1]));
            assertEquals(0,previous.distance(beginning),.0001);
            var next=motion.grip(motion.sample(transition[2],6));
            var blended=motion.grip(motion.pose(transition[2],6,transition[0],transition[1]));
            assertEquals(0,next.distance(blended),.0001);
        }
    }
    @Test void emergenceAndDiveCrossTheGroundPlane(){
        var motion=HydraMotion.INSTANCE;
        float buried=motion.grip(motion.sample(7,0)).y;
        float emerged=motion.grip(motion.sample(7,80)).y;
        assertTrue(buried<0&&emerged>2,"arrival must actually emerge above ground");
        assertTrue(motion.grip(motion.sample(6,36)).y<0,"exit must actually go below ground");
    }
}
