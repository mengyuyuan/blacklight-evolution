package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterRapidTest {
    @Test void ordinaryFlightHasNoColdDropAndZeroAccelerationSnapsToSourceCruise(){
        var f=new HelicopterRapidFlight(new Vec3(0,0,1),Vec3.ZERO);
        assertEquals(150,f.save().getDouble("VelocityZ"));
        assertEquals(new Vec3(0,0,6.25),f.step());assertEquals(new Vec3(0,0,6.25),f.step());assertEquals(new Vec3(0,0,1),f.heading());
        assertEquals(1.4527442455291748,HelicopterRapidFlight.LENGTH);
    }
    @Test void inheritedDriftDecaysWhileHeadingAndForwardCruiseRemainFixedAcrossReload(){
        var a=new HelicopterRapidFlight(new Vec3(0,0,1),new Vec3(1,.5,.4));
        Vec3 step=a.step();assertTrue(step.x>0&&step.x<1);assertTrue(step.y>0);assertEquals(6.25,step.z,1e-10);
        var b=HelicopterRapidFlight.load(a.save());assertNotNull(b);
        for(int i=0;i<25;i++){assertEquals(a.step(),b.step());assertEquals(new Vec3(0,0,1),b.heading());}
        assertEquals(0,a.step().x,1e-10);
    }
    @Test void lifetimeAndMalformedStateCannotCreateImmortalOrNonfiniteFlight(){
        var f=new HelicopterRapidFlight(new Vec3(0,0,1),Vec3.ZERO);for(int i=0;i<300;i++)f.step();assertTrue(f.expired());assertEquals(Vec3.ZERO,f.step());
        var bad=f.save();bad.putDouble("VelocityX",Double.NaN);assertNull(HelicopterRapidFlight.load(bad));
        assertThrows(IllegalArgumentException.class,()->new HelicopterRapidFlight(Vec3.ZERO,Vec3.ZERO));
        bad=f.save();bad.putInt("Age",-1);assertNull(HelicopterRapidFlight.load(bad));
    }
    @Test void finitePodsAlternateAtSourcePlayerCadenceAndPersistEmptyAndCooldown(){
        var m=new HelicopterRapidMagazine();
        for(int n=0;n<150;n++){
            assertEquals(n%2==0?-1:1,m.readySide());m.fired(m.readySide());
            var b=new HelicopterRapidMagazine();b.load(m.save());m=b;
            for(int i=0;i<4;i++){assertEquals(0,m.readySide());m.tick();}assertEquals(0,m.readySide());m.tick();
        }
        assertEquals(0,m.left()+m.right());assertEquals(150,m.shots());assertEquals(0,m.readySide());
        var restored=new HelicopterRapidMagazine();restored.load(m.save());assertEquals(0,restored.readySide());
    }
    @Test void emptySideFallsBackWithoutRefillingAndMalformedStateFailsClosed(){
        var m=new HelicopterRapidMagazine();var tag=m.save();tag.putInt("Left",0);tag.putInt("Right",2);m.load(tag);
        assertEquals(1,m.readySide());m.fired(1);for(int i=0;i<5;i++)m.tick();assertEquals(1,m.readySide());
        tag.putInt("Version",9);m.load(tag);assertEquals(0,m.readySide());
        m.load(new CompoundTag());assertEquals(150,m.left()+m.right());
    }
}
