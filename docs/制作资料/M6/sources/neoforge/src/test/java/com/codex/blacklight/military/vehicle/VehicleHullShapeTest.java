package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;
import com.codex.blacklight.military.vehicle.VehicleHullShape.Bounds;

class VehicleHullShapeTest {
    final Vec3 origin = new Vec3(0,0,0);
    @Test void longHullDoesNotShrinkAtAxisOrDiagonalHeadings() {
        var hull=new VehicleHullShape(2,6,3);
        var zero=hull.bounds(origin,0); assertEquals(-1,zero.minX(),1e-9); assertEquals(3,zero.maxZ(),1e-9);
        var ninety=hull.bounds(origin,90); assertEquals(-3,ninety.minX(),1e-9); assertEquals(1,ninety.maxZ(),1e-9);
        var diagonal=hull.bounds(origin,45); assertEquals(-2.8284271247461903,diagonal.minX(),1e-9); assertEquals(2.8284271247461903,diagonal.maxZ(),1e-9);
        assertEquals(0,diagonal.minY()); assertEquals(3,diagonal.maxY());
    }
    @Test void localRaycastRejectsEmptyConservativeAabbCorners() {
        var hull=new VehicleHullShape(2,6,3);
        assertEquals(7,hull.raycast(origin,0,new Vec3(0,1,10),new Vec3(0,0,-1),20).orElseThrow(),1e-9);
        assertTrue(hull.raycast(origin,45,new Vec3(2.7,5,2.7),new Vec3(0,-1,0),10).isEmpty());
        assertEquals(7,hull.raycast(origin,90,new Vec3(10,1,0),new Vec3(-4,0,0),20).orElseThrow(),1e-9);
        assertTrue(hull.raycast(origin,0,new Vec3(0,1,10),new Vec3(0,0,-1),6.9).isEmpty());
    }
    @Test void rotationCoversAMidArcPostEvenWhenBothEndBoxesAreClear() {
        var hull=new VehicleHullShape(2,6,3); var post=new Bounds(3.04,0,.58,3.10,2,.62);
        assertFalse(hull.bounds(origin,0).intersects(post)); assertFalse(hull.bounds(origin,90).intersects(post));
        var swept=hull.sweepRotation(origin,0,90);
        assertTrue(swept.size()>=40); assertTrue(swept.stream().anyMatch(b->b.intersects(post)));
        // Hand-derived corner at 70 degrees: x=1*cos70+3*sin70, z=-1*sin70+3*cos70.
        assertTrue(swept.stream().anyMatch(b->b.contains(new Vec3(3.161098005683394,1,.086367809191098)))) ;
    }
    @Test void sweepErrorInflationContainsEveryIntermediateHullCorner() {
        var hull=new VehicleHullShape(2.7,6.2,3.2); var swept=hull.sweepRotation(origin,-15,110);
        for(int i=0;i<=2000;i++) {
            double a=Math.toRadians(-15+125*i/2000.0);
            for(int sx:new int[]{-1,1}) for(int sz:new int[]{-1,1}) {
                var corner=new Vec3(sx*1.35*Math.cos(a)+sz*3.1*Math.sin(a),1,-sx*1.35*Math.sin(a)+sz*3.1*Math.cos(a));
                assertTrue(swept.stream().anyMatch(b->b.contains(corner)),"uncovered corner at sample "+i);
            }
        }
    }
    @Test void translationStepsDoNotSkipAThinWallOrSpanMoreThanAQuarterBlock() {
        var hull=new VehicleHullShape(2,6,3); var steps=hull.sweepTranslation(origin,new Vec3(2,0,0),0);
        assertEquals(8,steps.size());
        assertTrue(steps.stream().anyMatch(b->b.intersects(new Bounds(1.49,0,-.1,1.51,2,.1))));
        assertTrue(steps.stream().allMatch(b->b.maxX()-b.minX()<=2.250000001));
    }
    @Test void cannonIsIncludedWhenItsGunPitchAndTurretYawChange() {
        var d=VehicleCatalog.loadDefault().require("tank");
        // New authoring: muzzle z=6.03076923 plus radius .15785714; full body is 4x8.
        var forwards=VehicleHullShape.from(d,0,0).bounds(origin,0); assertEquals(6.18862637,forwards.maxZ(),1e-9);
        var side=VehicleHullShape.from(d,90,0).bounds(origin,0); assertEquals(6.18862637,side.maxX(),1e-9);
        assertEquals(4,side.maxZ(),1e-9); assertEquals(2,forwards.maxX(),1e-9);
    }
    @Test void invalidGeometryIsRejectedBeforeItCanCreateAnUnboundedSweep() {
        assertThrows(IllegalArgumentException.class,()->new VehicleHullShape(Double.NaN,6,3));
        assertThrows(IllegalArgumentException.class,()->new VehicleHullShape(-1,6,3));
        assertThrows(IllegalArgumentException.class,()->new VehicleHullShape(2,1e12,3));
        var hull=new VehicleHullShape(2,6,3);
        assertThrows(IllegalArgumentException.class,()->hull.sweepRotation(origin,0,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->hull.sweepTranslation(origin,new Vec3(10000,0,0),0));
        assertThrows(IllegalArgumentException.class,()->hull.raycast(origin,0,origin,new Vec3(0,0,0),10));
    }
    @Test void turretRotationAlsoCoversTheBarrelBetweenBothSafeEndpoints() {
        var d=VehicleCatalog.loadDefault().require("tank"); var post=new Bounds(-.05,1.9,4.7,.05,2.3,4.8);
        assertFalse(VehicleHullShape.from(d,-90,0).bounds(origin,0).intersects(post));
        assertFalse(VehicleHullShape.from(d,90,0).bounds(origin,0).intersects(post));
        assertTrue(VehicleHullShape.sweepArticulation(d,origin,0,-90,90,0,0).stream().anyMatch(b->b.intersects(post)));
    }
    @Test void diagonalBarrelRaycastRejectsItsEnvelopeCornerAndStillHitsTheRealBarrel() {
        var hull=VehicleHullShape.from(VehicleCatalog.loadDefault().require("tank"),45,0);
        assertTrue(hull.raycast(origin,0,new Vec3(3.3,10,1),new Vec3(0,-1,0),20).isEmpty());
        // Main barrel upper face is y=2.55+.15785714, so the y=10 ray travels 7.29214286.
        assertEquals(7.29214286,hull.raycast(origin,0,new Vec3(3,10,3),new Vec3(0,-1,0),20).orElseThrow(),1e-9);
        assertEquals(7.29214286,hull.raycast(new Vec3(5,3,-7),90,new Vec3(8,13,-10),new Vec3(0,-1,0),20).orElseThrow(),1e-9);
    }
    @Test void pitchedBarrelRaycastUsesTheBarrelsOwnAxes() {
        var hull=VehicleHullShape.from(VehicleCatalog.loadDefault().require("tank"),45,30);
        assertTrue(hull.raycast(origin,0,new Vec3(2.6,10,1),new Vec3(0,-1,0),20).isEmpty());
        // Pivot (0,2.55,1.16923077), rest z=4: pitch30 gives y=1.134615385, z=3.6207488354313145.
        // Yaw45 gives x=z=2.5602560545067773; upper face y=centerY+.15785714/cos30.
        double center=2.5602560545067773;
        assertEquals(8.683106890454991,hull.raycast(origin,0,new Vec3(center,10,center),new Vec3(0,-1,0),20).orElseThrow(),1e-9);
    }
}
