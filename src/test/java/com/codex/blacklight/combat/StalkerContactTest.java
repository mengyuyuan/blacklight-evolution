package com.codex.blacklight.combat;

import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StalkerContactTest {
    private static AABB cow(double z){return new AABB(-.45,0,z-.45,.45,1.4,z+.45);}
    @Test void onlyTheFourVisibleSharpDigitsOwnContact(){
        var parts=StalkerContact.claws(MutantMove.STALKER_CLAW,0,Vec3.ZERO,180);
        assertEquals(4,parts.size());assertEquals(2,parts.stream().filter(c->c.bone().equals("right_arm")).count());
        for(var c:parts){assertEquals(1/16.0,c.box().bounds.getXsize(),1e-6);assertEquals(1.5/16,c.box().bounds.getYsize(),1e-6);assertEquals(3/16.0,c.box().bounds.getZsize(),1e-6);assertEquals(-9.5/16,c.box().center.z,1e-6);}
    }
    @Test void closeTargetCanBeReachedButOldDamageConeCannot(){
        assertTrue(StalkerContact.canReach(MutantMove.STALKER_CLAW,Vec3.ZERO,0,cow(1.25)));
        assertFalse(StalkerContact.canReach(MutantMove.STALKER_CLAW,Vec3.ZERO,0,cow(2.1)));
        assertFalse(StalkerContact.canReach(MutantMove.STALKER_CLAW,Vec3.ZERO,0,cow(-1.25)));
    }
    @Test void eachDoubleClawWindowOwnsOnlyItsAnimatedHand(){
        var windows=StalkerContact.windows(MutantMove.STALKER_DOUBLE);assertEquals(2,windows.size());
        assertTrue(StalkerContact.owns(windows.get(0),"right_arm"));assertFalse(StalkerContact.owns(windows.get(0),"left_arm"));
        assertTrue(StalkerContact.owns(windows.get(1),"left_arm"));assertFalse(StalkerContact.owns(windows.get(1),"right_arm"));
        assertNull(StalkerContact.active(MutantMove.STALKER_DOUBLE,15,16));
    }
    @Test void movingClawCannotSkipAThinTargetBetweenTicks(){
        var center=StalkerContact.claws(MutantMove.STALKER_CLAW,12.375F,Vec3.ZERO,0).get(0).box().center;
        var tiny=new AABB(center,center).inflate(.015);
        var contact=StalkerContact.contact(MutantMove.STALKER_CLAW,StalkerContact.windows(MutantMove.STALKER_CLAW).get(0),12,13,Vec3.ZERO,Vec3.ZERO,0,tiny);
        assertNotNull(contact);assertEquals("right_arm",contact.bone());assertTrue(tiny.inflate(1e-5).contains(contact.point()));
    }
    @Test void bodyTravelIsIncludedInTheSweptHandContact(){
        var origin=new Vec3(0,.4,3);var before=origin.add(0,0,-.6);
        var claw=StalkerContact.claws(MutantMove.STALKER_POUNCE,18,origin,0).get(0);
        var contact=StalkerContact.contact(MutantMove.STALKER_POUNCE,StalkerContact.windows(MutantMove.STALKER_POUNCE).get(0),17,18,before,origin,0,claw.box().bounds);
        assertNotNull(contact);assertTrue(contact.point().z>3);
    }
    @Test void noDamageDuringWindupOrRecovery(){
        for(var move:new MutantMove[]{MutantMove.STALKER_CLAW,MutantMove.STALKER_DOUBLE,MutantMove.STALKER_POUNCE}){
            assertNull(StalkerContact.active(move,0,5));assertNull(StalkerContact.active(move,move.ticks-3,move.ticks));
            for(float t=0;t<=move.ticks;t+=.25F)for(var c:StalkerContact.claws(move,t,Vec3.ZERO,0))assertTrue(BlockThrowBallistics.finite(c.box().center));
        }
    }
    @Test void largeWorldCoordinatesDoNotQuantizeTheClaws(){
        var origin=new Vec3(4000000.125,90.5,-8000000.375);
        var local=StalkerContact.claws(MutantMove.STALKER_DOUBLE,24.25F,Vec3.ZERO,73);var world=StalkerContact.claws(MutantMove.STALKER_DOUBLE,24.25F,origin,73);
        for(int i=0;i<local.size();i++)assertEquals(0,world.get(i).box().center.subtract(origin).distanceTo(local.get(i).box().center),1e-8);
    }
    @Test void cachedReachMatchesDirectRotatedSharpBoxes(){
        for(float yaw:new float[]{0,45,90,173,271})for(double distance:new double[]{1.1,1.6,2.1}){
            double a=Math.toRadians(yaw);var center=new Vec3(-Math.sin(a)*distance,.7,Math.cos(a)*distance);var target=new AABB(center,center).inflate(.45,.7,.45);boolean direct=false;
            for(var w:StalkerContact.windows(MutantMove.STALKER_DOUBLE))for(float t=w.start();t<=w.end();t+=.25F)for(var c:StalkerContact.claws(MutantMove.STALKER_DOUBLE,t,Vec3.ZERO,yaw))if(StalkerContact.owns(w,c.bone())&&c.box().touches(target))direct=true;
            assertEquals(direct,StalkerContact.canReach(MutantMove.STALKER_DOUBLE,Vec3.ZERO,yaw,target),"yaw="+yaw+" distance="+distance);
        }
    }
}
