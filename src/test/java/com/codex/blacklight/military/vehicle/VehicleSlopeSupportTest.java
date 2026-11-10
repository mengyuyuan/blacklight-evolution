package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VehicleSlopeSupportTest {
    private static final double N=Double.NEGATIVE_INFINITY;
    private static double[][] rows(double... perRow){
        var g=new double[perRow.length][3];
        for(int r=0;r<perRow.length;r++)java.util.Arrays.fill(g[r],perRow[r]);
        return g;
    }
    @Test void hullOnHillsideIsCarriedByItsHighestContact(){
        assertEquals(68,VehicleKinematics.carriedTop(rows(64,65,66,67,68)));
        assertEquals(64,VehicleKinematics.carriedTop(rows(64,64,64,64,64)));
    }
    @Test void stairRunAndOneBlockLedgeAreCarried(){
        assertEquals(66,VehicleKinematics.carriedTop(rows(63,64,65,66,66,66)));
        assertEquals(65,VehicleKinematics.carriedTop(rows(64,64,64,65,65,65)));
    }
    @Test void narrowKerbUnderTheMiddleIsCarried(){
        var g=rows(64,64,64,64,64);g[2][1]=65;
        assertEquals(65,VehicleKinematics.carriedTop(g));
    }
    @Test void threeDeepPitUnderTheFrontStopsTheHull(){
        assertEquals(N,VehicleKinematics.carriedTop(rows(82,82,82,82,79)));
    }
    @Test void cliffEdgeOrFallStopsTheHull(){
        assertEquals(N,VehicleKinematics.carriedTop(rows(64,64,64,64,N)));
        assertEquals(N,VehicleKinematics.carriedTop(rows(N,N,N)));
        assertEquals(N,VehicleKinematics.carriedTop(rows(64,N,N,N,N)));
    }
    @Test void turnedHullStraddlesDiagonalRises(){
        // Axis-aligned samples about one block apart cross one boundary; turned samples can cross two.
        assertEquals(1,VehicleKinematics.crossings(0,.97));
        assertEquals(2,VehicleKinematics.crossings(.45,.86));
        var g=rows(64,64,64,64,64);for(int r=0;r<5;r++)g[r][2]=66;
        assertEquals(N,VehicleKinematics.carriedTop(g,1,1));
        assertEquals(66,VehicleKinematics.carriedTop(g,1,2));
        assertEquals(N,VehicleKinematics.carriedTop(rows(82,82,82,82,79),2,2));
    }
}