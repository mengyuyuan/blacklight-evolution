package com.codex.blacklight.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarrySteeringTest {
    private static double[] world(float yaw,float forward,float left) {
        double angle=Math.toRadians(yaw);
        return new double[]{-Math.sin(angle)*forward+Math.cos(angle)*left,
                Math.cos(angle)*forward+Math.sin(angle)*left};
    }
    @Test void turningNeverChangesRequestedWorldTravel() {
        for(float camera:new float[]{-179,-90,0,45,179,720})
            for(float actor:new float[]{-180,-44,0,91,179})
                for(float forward:new float[]{-1,0,1})for(float left:new float[]{-1,0,1}){
                    var step=CarrySteering.steer(camera,actor,forward,left);
                    var requested=world(camera,forward,left);var actual=world(step.yaw(),step.forward(),step.left());
                    assertArrayEquals(requested,actual,1E-6);
                    double turn=Math.atan2(Math.sin(Math.toRadians(step.yaw()-actor)),Math.cos(Math.toRadians(step.yaw()-actor)));
                    assertTrue(Math.abs(Math.toDegrees(turn))<=45.00001);
                }
    }
    @Test void backwardsAndSidewaysInputBecomeForwardTravelAfterTurning() {
        for(float[] direction:new float[][]{{-1,0},{0,-1},{0,1},{1,1},{-1,-1}}){
            float yaw=0;
            for(int tick=0;tick<4;tick++)yaw=CarrySteering.steer(0,yaw,direction[0],direction[1]).yaw();
            var step=CarrySteering.steer(0,yaw,direction[0],direction[1]);
            assertEquals(Math.hypot(direction[0],direction[1]),step.forward(),1E-6);
            assertEquals(0,step.left(),1E-6);
        }
    }
    @Test void releaseKeepsTheCarrierPoseAndDoesNotInventMotion() {
        var step=CarrySteering.steer(-135,73,0,0);
        assertEquals(73,step.yaw());assertEquals(0,step.forward());assertEquals(0,step.left());
    }
}
