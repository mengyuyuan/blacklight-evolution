package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterCircleStrafeTest {
    @Test void realFlightDynamicsConvergeFromInsideAndOutsideWithoutAChasedOrbitAngle(){
        for(Vec3 initial:new Vec3[]{new Vec3(12,16,0),new Vec3(55,45,0),new Vec3(0,0,0)}){
            var circle=new HelicopterCircleStrafe();circle.begin(.5);Vec3 position=initial,velocity=Vec3.ZERO,earlier=null;
            for(int tick=0;tick<800;tick++){
                var m=circle.motion(position,Vec3.ZERO,0,false,new Vec3(0,0,1));assertNotNull(m);
                double length=m.request().length();Vec3 goal=position.add(m.request().scale(HelicopterFlight.BRAKE_DISTANCE/length));
                Vec3 next=HelicopterFlight.velocity(velocity,position,goal,HelicopterFlight.MAX_SPEED*Math.min(1,length));
                assertTrue(next.subtract(velocity).length()<=HelicopterFlight.ACCELERATION+1e-7);velocity=next;position=position.add(next);
                if(tick==600)earlier=position;
            }
            assertTrue(position.horizontalDistance()>=19&&position.horizontalDistance()<=31,"distance band: "+position);
            assertTrue(position.y>=5.9&&position.y<=10.1,"height band: "+position);
            assertTrue(position.distanceTo(earlier)>15&&velocity.horizontalDistance()>.3,"continues orbiting, does not park on a waypoint");
        }
    }
    @Test void nativeFieldMappedTerrainRisePreservesHeightBandWidthAndHydraProfile(){
        var circle=new HelicopterCircleStrafe();circle.begin(0);
        var ordinary=circle.motion(new Vec3(25,87,0),new Vec3(0,81,0),81,false,Vec3.ZERO);
        assertEquals(6,ordinary.minimumHeight());assertEquals(10,ordinary.maximumHeight());assertEquals(0,ordinary.request().y);
        var hydra=circle.motion(new Vec3(25,87,0),new Vec3(0,81,0),81,true,Vec3.ZERO);
        assertEquals(15,hydra.minimumHeight());assertEquals(20,hydra.maximumHeight());assertTrue(hydra.request().y>0);
        var raised=circle.motion(new Vec3(25,90,0),new Vec3(0,81,0),105,false,Vec3.ZERO);
        assertEquals(30,raised.minimumHeight());assertEquals(34,raised.maximumHeight());
        assertTrue(raised.request().y>0);assertFalse(raised.inBand());
        assertNull(circle.motion(Vec3.ZERO,Vec3.ZERO,Double.NaN,false,Vec3.ZERO));
    }
    @Test void speedSamplePersistsAndMalformedSavedValuesCannotDriveFlight(){
        var circle=new HelicopterCircleStrafe();circle.begin(.75);var restored=new HelicopterCircleStrafe();restored.load(circle.save());
        assertEquals(.75,restored.speed(),1e-9);
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,.59,.81}){var tag=new CompoundTag();tag.putInt("Version",1);tag.putDouble("Speed",value);restored.load(tag);assertFalse(restored.initialized());}
        restored.load(new CompoundTag());assertFalse(restored.initialized());
        assertThrows(IllegalArgumentException.class,()->circle.begin(1));
    }
}
