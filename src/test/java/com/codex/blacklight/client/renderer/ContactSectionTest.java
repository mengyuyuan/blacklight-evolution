package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactSectionTest {
    static List<Vec3> box(Vec3 origin,double scale){
        var result=new ArrayList<Vec3>();
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1})result.add(origin.add(x*scale,y*scale,z*scale));
        return result;
    }
    @Test void preservesInteriorContactAndDiagonalDirection(){
        var contact=new Vec3(.1,.2,0);var normal=new Vec3(1,2,3).normalize();
        var plane=ContactSection.fit(contact,normal,box(Vec3.ZERO,1));
        assertNotNull(plane);assertEquals(0,plane.point().distanceTo(contact),1e-9);assertEquals(0,plane.normal().distanceTo(normal),1e-9);
    }
    @Test void grazingContactProducesASectionInsteadOfAThinChip(){
        var plane=ContactSection.fit(new Vec3(0,-.999,0),new Vec3(0,1,0),box(Vec3.ZERO,1));
        assertNotNull(plane);assertEquals(-.4,plane.point().y,1e-9);assertEquals(0,plane.point().x);assertEquals(0,plane.point().z);
    }
    @Test void adaptsToModelScaleAndDistantWorldCoordinates(){
        var offset=new Vec3(30000000,250,-30000000);
        for(double scale:new double[]{.1,1,10}){
            var plane=ContactSection.fit(offset.add(0,-scale,0),new Vec3(0,1,0),box(offset,scale));
            assertNotNull(plane);assertEquals(-scale*.4,plane.point().y-offset.y,1e-8);
        }
    }
    @Test void retainsSeparateBladeOffsetsAndDirections(){
        Set<Vec3> points=new HashSet<>();
        for(int i=0;i<4;i++){
            var normal=new Vec3(0,1,i*.05).normalize();
            var plane=ContactSection.fit(new Vec3(0,-.3+i*.2,0),normal,box(Vec3.ZERO,1));
            assertNotNull(plane);points.add(plane.point());assertEquals(0,plane.normal().distanceTo(normal),1e-9);
        }
        assertEquals(4,points.size());
    }
    static List<Vec3> face(){return List.of(new Vec3(-1,-1,0),new Vec3(1,-1,0),new Vec3(1,1,0),new Vec3(-1,1,0));}
    @Test void finiteVisibleBladeMustIntersectSkin(){
        Vec3[] crossing={new Vec3(0,-.5,-1),new Vec3(0,.5,1),new Vec3(0,.5,-1)};
        var contact=ContactSection.contact(Collections.singletonList(crossing),face());
        assertNotNull(contact);assertEquals(0,contact.point().z,1e-9);assertEquals(1,Math.abs(contact.normal().x),1e-9);
        Vec3[] distant=Arrays.stream(crossing).map(p->p.add(3,0,0)).toArray(Vec3[]::new);
        assertNull(ContactSection.contact(Collections.singletonList(distant),face()));
    }
    @Test void overlappingBoundingBoxesAloneDoNotAuthorizeContact(){
        Vec3[] nearCorner={new Vec3(.9,2,-1),new Vec3(2,.9,1),new Vec3(2,2,-1)};
        assertNull(ContactSection.contact(Collections.singletonList(nearCorner),face()));
    }
    @Test void flatDecorationDoesNotBecomeSolidTissue(){
        assertNull(ContactSection.fit(Vec3.ZERO,new Vec3(0,0,1),face()));
        assertNull(ContactSection.fit(Vec3.ZERO,Vec3.ZERO,box(Vec3.ZERO,1)));
    }
}
