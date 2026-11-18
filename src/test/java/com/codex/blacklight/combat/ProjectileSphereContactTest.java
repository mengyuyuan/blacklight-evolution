package com.codex.blacklight.combat;

import java.util.Arrays;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSphereContactTest {
    private static final AABB BOX=new AABB(-1,-1,-1,1,1,1);
    private static void point(Vec3 expected,Vec3 actual,double tolerance){assertEquals(0,expected.distanceTo(actual),tolerance);}
    private static Vec3[] corners(Vec3 origin,Vec3 x,Vec3 y,Vec3 z){
        var result=new Vec3[8];for(int i=0;i<8;i++)result[i]=origin.add((i&1)==0?Vec3.ZERO:x).add((i&2)==0?Vec3.ZERO:y).add((i&4)==0?Vec3.ZERO:z);return result;
    }
    @Test void faceHitDistinguishesProjectileCenterFromReceivingSurface(){
        var hit=ProjectileSphereContact.sweep(new Vec3(-3,0,0),new Vec3(3,0,0),.5,BOX);
        assertNotNull(hit);assertEquals(.25,hit.fraction(),1e-12);
        point(new Vec3(-1.5,0,0),hit.center(),1e-12);point(new Vec3(-1,0,0),hit.point(),1e-12);point(new Vec3(-1,0,0),hit.normal(),1e-12);
    }
    @Test void inflatedAabbEmptyCornerIsNotASphereHit(){
        var from=new Vec3(-3,1.4,1.4);var to=new Vec3(3,1.4,1.4);
        assertTrue(BOX.inflate(.5).clip(from,to).isPresent());
        assertNull(ProjectileSphereContact.sweep(from,to,.5,BOX));
    }
    @Test void edgeAndVertexUseRoundedFirstContactRatherThanExpandedPlanes(){
        var edge=ProjectileSphereContact.sweep(new Vec3(-3,1.3,0),new Vec3(3,1.3,0),.5,BOX);
        assertNotNull(edge);assertEquals((2-.4)/6,edge.fraction(),1e-12);
        point(new Vec3(-1,1,0),edge.point(),1e-12);point(new Vec3(-.8,.6,0),edge.normal(),1e-12);
        var vertex=ProjectileSphereContact.sweep(new Vec3(-3,1.3,1.3),new Vec3(3,1.3,1.3),.5,BOX);
        assertNotNull(vertex);assertEquals((2-Math.sqrt(.25-.18))/6,vertex.fraction(),1e-12);
        point(new Vec3(-1,1,1),vertex.point(),1e-12);assertEquals(.5,vertex.center().distanceTo(vertex.point()),1e-12);
    }
    @Test void shearedFacesUseTheirTrueWorldNormalAndCornerOverloadsAgree(){
        var x=new Vec3(1,0,0);var y=new Vec3(1,1,0);var z=new Vec3(0,0,1);
        var box=HydraContact.affine(Vec3.ZERO,new Vec3(1,1,1),x,y,z);
        var normal=new Vec3(-Math.sqrt(.5),Math.sqrt(.5),0);var surface=new Vec3(-1,0,0);
        var from=surface.add(normal.scale(3));var to=surface.subtract(normal.scale(3));
        var hit=ProjectileSphereContact.sweep(from,to,.25,box);assertNotNull(hit);
        assertEquals((3-.25)/6,hit.fraction(),1e-12);point(surface,hit.point(),1e-12);point(normal,hit.normal(),1e-12);
        var vertices=corners(new Vec3(-2,-1,-1),x.scale(2),y.scale(2),z.scale(2));
        assertEquals(hit,ProjectileSphereContact.sweep(from,to,.25,vertices));
        assertEquals(hit,ProjectileSphereContact.sweep(from,to,.25,Arrays.asList(vertices)));
    }
    @Test void translatedFastSweepKeepsThinGeometryAndEndpointContacts(){
        var origin=new Vec3(29_000_000,64,-29_000_000);var box=new AABB(origin.add(-.0001,-1,-1),origin.add(.0001,1,1));
        var hit=ProjectileSphereContact.sweep(origin.add(-1000,0,0),origin.add(1000,0,0),.01,box);
        assertNotNull(hit);assertEquals((1000-.0101)/2000,hit.fraction(),2e-11);point(origin.add(-.0001,0,0),hit.point(),1e-8);
        var end=ProjectileSphereContact.sweep(new Vec3(-3,0,0),new Vec3(-1.5,0,0),.5,BOX);
        assertNotNull(end);assertEquals(1,end.fraction(),1e-12);
        assertNull(ProjectileSphereContact.sweep(new Vec3(-3,0,0),new Vec3(-1.5001,0,0),.5,BOX));
    }
    @Test void tangentInitialOverlapAndZeroRadiusHaveDefinedResults(){
        var tangent=ProjectileSphereContact.sweep(new Vec3(-3,1.5,0),new Vec3(3,1.5,0),.5,BOX);
        assertNotNull(tangent);assertEquals(1/3D,tangent.fraction(),1e-12);
        var overlap=ProjectileSphereContact.sweep(new Vec3(1.2,0,0),new Vec3(2,0,0),.25,BOX);
        assertNotNull(overlap);assertEquals(0,overlap.fraction());point(new Vec3(1,0,0),overlap.point(),1e-12);
        var contained=ProjectileSphereContact.sweep(Vec3.ZERO,Vec3.ZERO,0,BOX);
        assertNotNull(contained);assertEquals(0,contained.fraction());assertEquals(1,contained.normal().length(),1e-12);
        var ray=ProjectileSphereContact.sweep(new Vec3(-3,0,0),new Vec3(3,0,0),0,BOX);
        assertNotNull(ray);assertEquals(1/3D,ray.fraction(),1e-12);point(new Vec3(-1,0,0),ray.normal(),1e-12);
        assertNull(ProjectileSphereContact.sweep(new Vec3(3,0,0),new Vec3(3,0,0),.25,BOX));
    }
    @Test void planarAndPointBoxesRemainRealSurfaces(){
        for(var box:new AABB[]{new AABB(0,-1,-1,0,1,1),new AABB(0,0,0,0,0,0)}){
            var hit=ProjectileSphereContact.sweep(new Vec3(-2,0,0),new Vec3(2,0,0),.5,box);
            assertNotNull(hit);assertEquals(.375,hit.fraction(),1e-12);point(Vec3.ZERO,hit.point(),1e-12);
        }
    }
    @Test void rejectsInvalidInputAndNonAffineCornerOrdering(){
        assertThrows(IllegalArgumentException.class,()->ProjectileSphereContact.sweep(Vec3.ZERO,new Vec3(1,0,0),-.1,BOX));
        assertThrows(IllegalArgumentException.class,()->ProjectileSphereContact.sweep(new Vec3(Double.NaN,0,0),Vec3.ZERO,1,BOX));
        assertThrows(IllegalArgumentException.class,()->ProjectileSphereContact.sweep(Vec3.ZERO,new Vec3(1,0,0),1e300,BOX));
        var bad=corners(Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));bad[7]=new Vec3(2,2,2);
        assertThrows(IllegalArgumentException.class,()->ProjectileSphereContact.sweep(Vec3.ZERO,new Vec3(1,0,0),.1,bad));
    }
}
