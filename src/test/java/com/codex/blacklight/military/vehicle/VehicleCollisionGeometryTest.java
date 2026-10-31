package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.military.vehicle.VehicleHullShape.Bounds;
import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VehicleCollisionGeometryTest {
    @Test void sidewaysBarrelDoesNotFillTheWalkingSpaceBelowIt(){
        var geometry=new VehicleCollisionGeometry(VehicleCatalog.loadDefault().require("tank"),0,90,0);
        assertTrue(geometry.rasterize(new Bounds(3.7,0,-.3,7.3,1.8,.3)).isEmpty(),
                "native contact raster must leave the full player route below the sideways barrel empty");
    }
    @Test void diagonalHullDoesNotFillItsEmptyBroadPhaseCorner(){
        var geometry=new VehicleCollisionGeometry(VehicleCatalog.loadDefault().require("tank"),45,0,0);
        assertTrue(geometry.rasterize(new Bounds(2.7,0,-3.8,6.3,1.8,-3.2)).isEmpty());
    }
    @Test void fullWidthBodyAndTrueBarrelHeightRemainSolid(){
        var geometry=new VehicleCollisionGeometry(VehicleCatalog.loadDefault().require("tank"),0,90,0);
        var body=geometry.rasterize(new Bounds(1.9,.2,1.9,2.5,1.8,2.5));assertFalse(body.isEmpty());
        assertEquals(-2,body.xs()[0],1e-9);assertEquals(2,body.xs()[body.xSize()],1e-9);assertEquals(0,body.maximumError());
        var barrel=geometry.rasterize(new Bounds(3.7,2.4,-.3,6.3,2.7,.3));assertFalse(barrel.isEmpty());
        assertEquals(6.03076923,barrel.xs()[barrel.xSize()],1e-8);assertTrue(occupied(barrel,new Vec3(4,2.55,0)));
    }
    @Test void arbitraryYawAndPitchCoverActualGeometryWithoutUnboundedOverfill(){
        var definition=VehicleCatalog.loadDefault().require("tank");var origin=new Vec3(0,0,0);var random=new Random(78290);
        for(double[] pose:new double[][]{{17.35,63.4,-19.2},{-51.5,-73.25,12.5},{45,0,0}}){
            var reference=VehicleHullShape.from(definition,pose[1],pose[2]);
            var grid=new VehicleCollisionGeometry(definition,pose[0],pose[1],pose[2]).rasterize(reference.bounds(origin,pose[0]));
            assertBounded(grid);
            for(int i=0;i<500;i++){
                double x=-1.99+random.nextDouble()*3.98,y=.01+random.nextDouble()*3.38,z=-3.99+random.nextDouble()*7.98;
                double angle=Math.toRadians(pose[0]);
                assertTrue(occupied(grid,new Vec3(x*Math.cos(angle)+z*Math.sin(angle),y,z*Math.cos(angle)-x*Math.sin(angle))),"raster may not remove real full-size body contact");
            }
            for(String suffix:new String[]{"primary","secondary"}){
                var a=definition.mountWorld("breech_"+suffix,origin,pose[0],pose[1],pose[2]);
                var b=definition.mountWorld("muzzle_"+suffix,origin,pose[0],pose[1],pose[2]);
                for(int i=0;i<=20;i++){double t=i/20.0;assertTrue(occupied(grid,new Vec3(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t)),"raster may not remove articulated barrel contact");}
            }
            int size=grid.xSize()*grid.ySize()*grid.zSize();
            for(int index=0;index<size;index+=97)if(grid.filled().get(index)){
                int x=index/(grid.ySize()*grid.zSize()),y=(index/grid.zSize())%grid.ySize(),z=index%grid.zSize();
                for(int dx=0;dx<=1;dx++)for(int dy=0;dy<=1;dy++)for(int dz=0;dz<=1;dz++){
                    var point=new Vec3(grid.xs()[x+dx],grid.ys()[y+dy],grid.zs()[z+dz]);var closest=reference.closestPoint(origin,pose[0],point);
                    double distance=Math.sqrt(Math.pow(point.x()-closest.x(),2)+Math.pow(point.y()-closest.y(),2)+Math.pow(point.z()-closest.z(),2));
                    assertTrue(distance<=grid.maximumError()+1e-7,"raster overfill must stay inside its declared geometric error");
                }
            }
            System.out.println("pose="+Arrays.toString(pose)+" cells="+size+" maxError="+grid.maximumError());
        }
    }
    @Test void walkingQueryUsesFineCellsAndHugeQueriesStayInsideTheNativeMemoryCap(){
        var geometry=new VehicleCollisionGeometry(VehicleCatalog.loadDefault().require("tank"),17.35,63.4,-19.2);
        var walking=geometry.rasterize(new Bounds(1.5,0,0,2.5,1.8,.6));assertFalse(walking.isEmpty());assertBounded(walking);
        assertTrue(walking.maximumError()<=Math.sqrt(3)/16+1e-9);
        var huge=geometry.rasterize(new Bounds(-1000000,-1000000,-1000000,1000000,1000000,1000000));assertBounded(huge);
        assertTrue(huge.maximumError()<=Math.sqrt(3)/4+1e-9);
        System.out.println("walking cells="+(walking.xSize()*walking.ySize()*walking.zSize())+" maxError="+walking.maximumError());
    }
    private static void assertBounded(VehicleCollisionGeometry.Grid grid){
        assertFalse(grid.isEmpty());assertTrue((long)grid.xSize()*grid.ySize()*grid.zSize()<=VehicleCollisionGeometry.MAX_CELLS);
        assertTrue(grid.xSize()<=VehicleCollisionGeometry.MAX_AXIS_CELLS&&grid.ySize()<=VehicleCollisionGeometry.MAX_AXIS_CELLS&&grid.zSize()<=VehicleCollisionGeometry.MAX_AXIS_CELLS);
    }
    private static boolean occupied(VehicleCollisionGeometry.Grid grid,Vec3 point){
        int x=index(grid.xs(),point.x()),y=index(grid.ys(),point.y()),z=index(grid.zs(),point.z());
        return x>=0&&y>=0&&z>=0&&x<grid.xSize()&&y<grid.ySize()&&z<grid.zSize()&&grid.filled().get((x*grid.ySize()+y)*grid.zSize()+z);
    }
    private static int index(double[] values,double point){int found=Arrays.binarySearch(values,point);return found>=0?Math.min(found,values.length-2):-found-2;}
    public static void main(String[] args){
        var test=new VehicleCollisionGeometryTest();test.sidewaysBarrelDoesNotFillTheWalkingSpaceBelowIt();test.diagonalHullDoesNotFillItsEmptyBroadPhaseCorner();
        test.fullWidthBodyAndTrueBarrelHeightRemainSolid();test.arbitraryYawAndPitchCoverActualGeometryWithoutUnboundedOverfill();
        test.walkingQueryUsesFineCellsAndHugeQueriesStayInsideTheNativeMemoryCap();
        System.out.println("VehicleCollisionGeometryTest: 5 passed");
    }
}
