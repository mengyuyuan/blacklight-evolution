package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterMovementClearanceTest {
    @Test void departureCanSeparateFromABystanderAlreadyInsideTheHull() {
        var body=new AABB(-3.1,0,-6.4,3.1,3.75,4.9);
        var bystander=new AABB(2.45,0,-.45,3.35,.9,.45);
        for(int i=0;i<40;i++) {
            var next=body.move(0,.035,0);
            assertTrue(HelicopterMovementClearance.permits(body,next,bystander));body=next;
        }
        assertFalse(body.intersects(bystander));
    }
    @Test void containedObstacleRequiresAnOutwardMotionEvenBeforeVolumeChanges() {
        var body=new AABB(-3,0,-6,3,4,5);
        var lowBody=new AABB(-.4,.5,-.4,.4,1.5,.4);
        assertTrue(HelicopterMovementClearance.permits(body,body.move(0,.03,0),lowBody));
        assertFalse(HelicopterMovementClearance.permits(body,body.move(0,-.03,0),lowBody));
        assertFalse(HelicopterMovementClearance.permits(body,body,lowBody));
    }
    @Test void freshWallsCeilingsAndBodiesStillBlockTheEntireSweptPath() {
        var body=new AABB(-3,0,-6,3,4,5);
        assertFalse(HelicopterMovementClearance.permits(body,body.move(0,0,20),new AABB(-4,0,8,4,5,8.05)));
        assertFalse(HelicopterMovementClearance.permits(body,body.move(0,2,0),new AABB(-4,4.5,-7,4,5,6)));
        assertFalse(HelicopterMovementClearance.permits(body,body.move(2,0,0),new AABB(3.5,0,-.5,4,2,.5)));
    }
    @Test void ExistingOverlapCannotGrowOrRotateThroughAnObstacle() {
        var body=new AABB(-3,0,-6,3,4,5);
        var wall=new AABB(2.9,-1,-8,5,7,8);
        assertTrue(HelicopterMovementClearance.permits(body,body.move(-.03,0,0),wall));
        assertFalse(HelicopterMovementClearance.permits(body,body.move(.03,0,0),wall));
        assertFalse(HelicopterMovementClearance.permits(body,body.inflate(.03),wall));
    }
}
