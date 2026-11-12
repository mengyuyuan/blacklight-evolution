package com.codex.blacklight.military.vehicle;

import java.util.List;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleDeformationTest {
    @Test void contactsActualSkinAndDisplacesOnlyNearTheHit(){
        for(String chassis:List.of("tank","apc","truck")){
            double x=chassis.equals("tank")?2:chassis.equals("apc")?1.65:1.45;
            var dent=VehicleDeformation.contact(chassis,new Vec3(x,1.65,1),new Vec3(-1,0,0),0,0,40,420);
            assertNotNull(dent);assertTrue(dent.inward().x<-.9);assertTrue(dent.depth()>.2);
            assertEquals(dent.depth(),VehicleDeformation.offset(List.of(dent),dent.point()).length(),1e-6);
            assertEquals(Vec3.ZERO,VehicleDeformation.offset(List.of(dent),dent.point().add(0,0,3)));
            assertEquals(Vec3.ZERO,VehicleDeformation.offset(List.of(dent),dent.point().add(-4,0,0)));
        }
    }
    @Test void boneCoordinatesFollowTurretAndPitchWithoutSliding(){
        Vec3 point=new Vec3(.3,.2,1.5);
        for(String chassis:List.of("tank","apc"))for(String bone:List.of("body","turret_yaw","gun_pitch"))for(double yaw:new double[]{0,90,-135}){
            Vec3 p=VehicleDeformation.toBody(chassis,bone,point,yaw,-20);
            assertTrue(VehicleDeformation.toBone(chassis,bone,p,yaw,-20).distanceTo(point)<1e-5);
        }
    }
    @Test void broadHullContactResolvesToActualTurretOrDeckAlongTheAttackRay(){
        var turret=VehicleDeformation.contact("tank",new Vec3(2,2.6,.2),new Vec3(-1,0,0),0,0,40,420);
        assertNotNull(turret);assertEquals("turret_yaw",turret.bone());
        var deck=VehicleDeformation.contact("tank",new Vec3(1.1,3.4,2.8),new Vec3(0,-1,0),0,0,40,420);
        assertNotNull(deck);assertEquals("body",deck.bone());
        assertTrue(deck.inward().y<-.9);
    }
    @Test void repeatedImpactsAccumulateWithinBoundsAndRepairRestoresThem(){
        var dent=new VehicleDeformation.Dent("body",new Vec3(2,1.6,1),new Vec3(-1,0,0),1,.3);
        var dents=List.of(dent);for(int i=0;i<50;i++)dents=VehicleDeformation.add(dents,dent);
        assertEquals(1,dents.size());assertEquals(.55,dents.getFirst().depth());
        assertEquals(.275,VehicleDeformation.repair(dents,.5).getFirst().depth(),1e-6);
        assertTrue(VehicleDeformation.repair(dents,1).isEmpty());
        for(int i=0;i<40;i++)dents=VehicleDeformation.add(dents,new VehicleDeformation.Dent("body",new Vec3(0,0,i*.7),new Vec3(1,0,0),.3,.1));
        assertEquals(VehicleDeformation.LIMIT,dents.size());
    }
    @Test void oldAndMalformedSavesAreSafeAndRoundtripPreservesDents(){
        var dent=new VehicleDeformation.Dent("body",new Vec3(2,1.6,1),new Vec3(-1,0,0),1,.3);
        assertEquals(List.of(dent),VehicleDeformation.read("tank",VehicleDeformation.write(List.of(dent))));
        var saved=VehicleDeformation.write(List.of(dent));saved.getCompound(0).putDouble("Depth",Double.NaN);
        assertTrue(VehicleDeformation.read("tank",saved).isEmpty());
        assertTrue(VehicleDeformation.read("tank",new ListTag()).isEmpty());
    }
}
