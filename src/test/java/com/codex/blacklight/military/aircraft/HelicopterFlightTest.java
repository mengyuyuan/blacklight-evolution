package com.codex.blacklight.military.aircraft;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterFlightTest {
    @Test void flightAcceleratesAndBrakesIntoAStationaryLanding(){
        Vec3 position=Vec3.ZERO,velocity=Vec3.ZERO,goal=new Vec3(45,16,0);double maximum=0;
        for(int i=0;i<600;i++){
            Vec3 next=HelicopterFlight.velocity(velocity,position,goal,HelicopterFlight.MAX_SPEED);
            assertTrue(next.subtract(velocity).length()<=HelicopterFlight.ACCELERATION+1e-7);
            maximum=Math.max(maximum,next.length());velocity=next;position=position.add(next);
            assertTrue(position.x<=goal.x+.01,"approach must not oscillate through landing destination");
        }
        assertTrue(maximum>.5);assertTrue(velocity.length()<.001);assertTrue(position.distanceTo(goal)<.01);
    }
    @Test void closeApproachSlowsBelowCruise(){
        assertTrue(HelicopterFlight.velocity(Vec3.ZERO,Vec3.ZERO,new Vec3(.2,0,0),.65).length()<.01);
    }
    @Test void headingChangesRemainBoundedAcrossWrap(){
        float before=179,next=HelicopterFlight.heading(new Vec3(-.1,0,-1),before);
        assertTrue(Math.abs(next-before)<=3);assertEquals(81,HelicopterFlight.heading(Vec3.ZERO,81));
    }
    @Test void invalidSavedVectorsCannotPropagateNaN(){
        assertEquals(Vec3.ZERO,HelicopterFlight.velocity(Vec3.ZERO,Vec3.ZERO,new Vec3(Double.NaN,0,0),.65));
        assertEquals(Vec3.ZERO,HelicopterFlight.velocity(new Vec3(Double.POSITIVE_INFINITY,0,0),Vec3.ZERO,Vec3.ZERO,.65));
    }
}
