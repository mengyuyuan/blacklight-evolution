package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterHellfireRackTest {
    @Test void originalMeshLengthIsIndependentOfThePointFiveTwoProfileField(){
        assertEquals(.7220941781997681-(-.7322606444358826),HelicopterHellfireRack.MISSILE_LENGTH,1e-12);
        assertTrue(HelicopterHellfireRack.MISSILE_LENGTH>1.4);
    }
    @Test void fixedRacksStayInboardOfTheSeparatePodsAndCycleFourDistinctRailCenters(){
        var points=new java.util.HashSet<Vec3>();
        for(int side:new int[]{-1,1}){
            assertTrue(Math.abs(HelicopterHellfireRack.rapidPod(side).x)>Math.abs(HelicopterHellfireRack.mount(side).x)+.7);
            for(int slot=0;slot<4;slot++){
                Vec3 p=HelicopterHellfireRack.mount(side).add(HelicopterHellfireRack.slot(slot));
                assertTrue(points.add(p));assertTrue(Math.abs(p.x)>1.7);
            }
        }
        assertEquals(8,points.size());
        assertTrue(HelicopterHellfireRack.slot(0).x>0&&HelicopterHellfireRack.slot(0).y>0);
        assertTrue(HelicopterHellfireRack.slot(1).x>0&&HelicopterHellfireRack.slot(1).y<0);
        assertTrue(HelicopterHellfireRack.slot(2).x<0&&HelicopterHellfireRack.slot(2).y<0);
        assertTrue(HelicopterHellfireRack.slot(3).x<0&&HelicopterHellfireRack.slot(3).y>0);
    }
    @Test void sourcePlayerLaunchKeepsBodyHeadingWhileAiCanDepressWithoutYawTraverse(){
        for(float heading:new float[]{0,45,179,-90})for(float bank:new float[]{-12,0,12}){
            Vec3 localTarget=new Vec3(8,-12,30),world=HelicopterArmament.rotate(localTarget,heading,bank);
            var ai=HelicopterArmament.aim(HelicopterHellfireRack.launchDirection(world,heading,bank,false),heading,bank);
            assertEquals(0,ai.yaw(),1e-4);assertEquals(Math.toDegrees(Math.atan2(12,Math.hypot(8,30))),ai.pitch(),1e-4);
            var player=HelicopterHellfireRack.launchDirection(world,heading,bank,true);
            assertEquals(1,player.dot(HelicopterArmament.rotate(new Vec3(0,0,1),heading,bank)),1e-8);
        }
    }
    @Test void finitePlayerMissilesDisappearInActualLeftRightLaunchOrder(){
        for(int remaining=8;remaining>=0;remaining--){int visible=0;
            for(int side:new int[]{-1,1})for(int slot=0;slot<4;slot++)if(HelicopterHellfireRack.loaded(side,slot,remaining))visible++;
            assertEquals(remaining,visible);
            if(remaining>0){int side=remaining%2==0?-1:1,slot=(8-remaining)/2;
                assertTrue(HelicopterHellfireRack.loaded(side,slot,remaining));assertFalse(HelicopterHellfireRack.loaded(side,slot,remaining-1));}
        }
    }
}
