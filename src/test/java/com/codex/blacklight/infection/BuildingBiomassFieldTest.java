package com.codex.blacklight.infection;

import static com.codex.blacklight.infection.BuildingBiomassField.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class BuildingBiomassFieldTest {
    private static List<Stem> example() {
        return List.of(new Stem(List.of(new Point(.5,.2,.5),new Point(-2,7,4),new Point(3,15,2),new Point(5,24,.5)),1.9,1.15,1000,9000,-1));
    }
    @Test void allBoundaryMeshesLieOnTheirPhysicalFieldAndFaceOutward() {
        for(int mask=0;mask<256;mask++) {
            var triangles=mesh(mask);assertTrue(triangles.size()<=12);
            assertEquals(triangles.size(),mesh(255-mask).size());
            for(var triangle:triangles) {
                assertTrue(triangle.b().sub(triangle.a()).cross(triangle.c().sub(triangle.a())).length()>1e-8);
                var centre=triangle.a().add(triangle.b()).add(triangle.c()).mul(1/3D);
                assertEquals(0,scalar(mask,centre.x(),centre.y(),centre.z()),1e-7);
                assertEquals(1,triangle.normal().length(),1e-7);
                var outward=centre.add(triangle.normal().mul(1e-5));
                var inward=centre.sub(triangle.normal().mul(1e-5));
                if(inCell(outward)&&inCell(inward))assertTrue(scalar(mask,outward.x(),outward.y(),outward.z())<scalar(mask,inward.x(),inward.y(),inward.z()));
                for(var p:List.of(triangle.a(),triangle.b(),triangle.c()))assertTrue(inCell(p));
            }
        }
    }
    private static boolean inCell(Point p) { return Math.min(p.x(),Math.min(p.y(),p.z()))>=0&&Math.max(p.x(),Math.max(p.y(),p.z()))<=1; }
    private static Set<String> boundary(int mask,int axis,double plane) {
        var result=new TreeSet<String>();
        for(var t:mesh(mask))for(var p:List.of(t.a(),t.b(),t.c())) {
            double v=axis==0?p.x():axis==1?p.y():p.z();
            if(v==plane)result.add(axis==0?p.y()+":"+p.z():axis==1?p.x()+":"+p.z():p.x()+":"+p.y());
        }
        return result;
    }
    @Test void neighbouringCellsHaveIdenticalSeamsAlongEveryAxis() {
        var stems=example();
        for(var cell:cells(stems,16384))for(int axis=0;axis<3;axis++) {
            int a=mask(stems,cell.x(),cell.y(),cell.z(),12000,0);
            int b=mask(stems,cell.x()+(axis==0?1:0),cell.y()+(axis==1?1:0),cell.z()+(axis==2?1:0),12000,0);
            assertEquals(boundary(a,axis,1),boundary(b,axis,0),"seam "+cell+" axis "+axis);
        }
    }
    @Test void lateColumnIsThickAndReachesTheRealSupportProgressively() {
        var stems=example();var nearEnd=new Point(5,24,.5);
        assertTrue(density(stems,nearEnd,4000,0)>0);
        assertTrue(density(stems,nearEnd,12000,0)<0);
        assertTrue(density(stems,new Point(.5,.2,.5),12000,0)<-1.5);
        assertTrue(cells(stems,16384).size()>300);
        assertEquals(cells(stems,16384),cells(stems,16384));
        assertEquals(Double.POSITIVE_INFINITY,density(stems,nearEnd,12000,1));
    }
    @Test void clearedStemDoesNotLeaveASecondIndependentSurface() {
        var stems=example();
        for(var c:cells(stems,16384))assertEquals(0,mask(stems,c.x(),c.y(),c.z(),12000,1));
    }
    @Test void plansAreBoundedBeforeBuildingAWorldQueue() {
        assertThrows(IllegalArgumentException.class,()->cells(example(),10));
        assertThrows(IllegalArgumentException.class,()->cells(List.of(new Stem(List.of(new Point(0,0,0),new Point(500,500,500)),4,4,0,1000,-1)),16384));
    }
}
