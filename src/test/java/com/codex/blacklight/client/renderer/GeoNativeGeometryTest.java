package com.codex.blacklight.client.renderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;

class GeoNativeGeometryTest {
    private static GeoBone bone(double rotation){
        var b=new GeoBone(null,"body",false,0D,false,false);b.setPivotX(3);b.setPivotY(10);b.setPivotZ(-2);b.setRotX(.2F);b.setRotZ(.4F);b.saveInitialSnapshot();
        var points=new GeoVertex[]{new GeoVertex(2/16D,6/16D,-3/16D),new GeoVertex(6/16D,6/16D,-3/16D),new GeoVertex(6/16D,14/16D,1/16D),new GeoVertex(2/16D,14/16D,1/16D)};
        var quad=new GeoQuad(points,new Vector3f(0,0,1),Direction.SOUTH);
        b.getCubes().add(new GeoCube(new GeoQuad[]{quad},Vec3.ZERO,new Vec3(0,0,rotation),new Vec3(4,8,4),0,false));return b;
    }
    @Test void asymmetricJointAndAxisConversionMatchRealCubeCoordinates(){
        var source=bone(0);var view=new GeoNativeGeometry(new BakedGeoModel(List.of(source),null));
        var cube=view.joint(source).getRandomCube(RandomSource.create(1));
        assertEquals(-1,cube.minX,.0001);assertEquals(3,cube.maxX,.0001);
        assertEquals(-4,cube.minY,.0001);assertEquals(4,cube.maxY,.0001);
        assertEquals(-1,cube.minZ,.0001);assertEquals(3,cube.maxZ,.0001);
        assertEquals(1,view.cubeCount());assertEquals(1,view.jointCount());
        assertEquals(-.2,view.joint(source).getInitialPose().xRot,.0001);assertEquals(-.4,view.joint(source).getInitialPose().zRot,.0001);
        assertEquals(.2,source.getRotX(),.0001);assertEquals(.4,source.getRotZ(),.0001);assertFalse(source.isTrackingMatrices());
    }
    @Test void cubeRotationIsBakedBeforeRebasingAndDoesNotMoveSourceBones(){
        var source=bone(Math.PI/2);var view=new GeoNativeGeometry(new BakedGeoModel(List.of(source),null));
        var cube=view.joint(source).getRandomCube(RandomSource.create(1));
        assertEquals(-17,cube.minX,.0001);assertEquals(-9,cube.maxX,.0001);
        assertEquals(4,cube.minY,.0001);assertEquals(8,cube.maxY,.0001);
        assertEquals(3,source.getPivotX());assertEquals(10,source.getPivotY());assertEquals(-2,source.getPivotZ());
        assertEquals(Math.PI/2,source.getCubes().get(0).rotation().z,.000001);
    }
}
