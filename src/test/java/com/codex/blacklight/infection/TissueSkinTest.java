package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueSkinTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static final Bone BODY=new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));
    @Test void sectionsPreserveSurfaceAreaWindingAndSocketPlanes() {
        V a=new V(-4,0,-2),b=new V(4,0,-2),c=new V(4,12,-2);
        var pieces=TissueSkin.sections(BODY,a,b,c);float area=0;boolean middle=false;
        assertTrue(pieces.size()<=7&&pieces.size()>1);
        for(var triangle:pieces) {
            V cross=triangle.b().sub(triangle.a()).cross(triangle.c().sub(triangle.a()));area+=cross.length()*.5F;
            assertTrue(cross.dot(b.sub(a).cross(c.sub(a)))>0);
            for(var point:new V[]{triangle.a(),triangle.b(),triangle.c()}) {
                assertEquals(-2,point.z());assertTrue(point.y()>=0&&point.y()<=12);
                middle|=Math.abs(point.y()-4)<.0001F||Math.abs(point.y()-8)<.0001F;
            }
        }
        assertEquals(48,area,.0001F);assertTrue(middle);
        // An old four-corner cuboid sampled only sin(0) and sin(pi), so no
        // amount of stronger coefficients could create a shoulder silhouette.
        var state=new TissueEvolution.State(8,1,.2F,.2F,.2F,.2F);
        assertEquals(a,TissueEvolution.develop(BODY,a,state));
        var shoulder=new V(4,4,-2);assertTrue(TissueEvolution.develop(BODY,shoulder,state).x()>5);
    }
    @Test void coplanarCutsDoNotDuplicateFacesOrDegenerateTriangles() {
        var pieces=TissueSkin.sections(BODY,new V(-4,4,-2),new V(4,4,-2),new V(4,4,2));
        assertEquals(1,pieces.size());
    }
    @Test void buryingTheSocketDoesNotHideTheExposedTip() {
        float[] vertices=new float[72];
        for(int ring=0;ring<3;ring++)for(int i=ring*24;i<ring*24+24;i+=3){vertices[i]=6+ring;vertices[i+1]=3;vertices[i+2]=1;}
        TissueSilhouette.graft(vertices,new V(-2,0,0));
        assertEquals(4,vertices[0]);assertEquals(6,vertices[24]);assertEquals(8,vertices[48]);
        assertEquals(3,vertices[49]);assertEquals(1,vertices[50]);
    }
    @Test void tissueColourRetainsEarlySkinAndAlphaWithoutRandomFlicker() {
        for(long seed=0;seed<40;seed++)for(int role=0;role<7;role++) {
            int early=0x91ffffff;assertEquals(early,TissueSkin.tint(early,seed,role,0));
            int grown=TissueSkin.tint(early,seed,role,1);
            assertEquals(early>>>24,grown>>>24);assertTrue((grown&0xffffff)<0xffffff);
            assertEquals(grown,TissueSkin.tint(early,seed,role,1));
        }
    }
    @Test void tissueCoverageIsContinuousAndEventuallyCoversTheActualSurface() {
        var sections=TissueSkin.sections(BODY,new V(-4,0,-2),new V(4,0,-2),new V(4,12,-2));
        for(long seed=0;seed<15;seed++) {
            float previous=0;
            for(long age=12000;age<=48000;age+=120) {
                float area=0;
                for(var triangle:sections) {
                    var polygon=TissueSkin.cover(BODY,triangle,triangle,seed,age,0);
                    assertTrue(polygon.size()<=4);
                    for(int i=1;i<polygon.size()-1;i++)area+=polygon.get(i).position().sub(polygon.get(0).position()).cross(polygon.get(i+1).position().sub(polygon.get(0).position())).length()*.5F;
                }
                assertTrue(area>=previous-.0001F&&area-previous<2.4F,"joined cover advances continuously without a per-triangle wire pattern");previous=area;
            }
            assertEquals(48,previous,.0001F);
        }
    }
    @Test void primaryOrgansDevelopBeyondTheirBuriedSocketWithSupportingGirth() {
        for(var core:TissueCore.values()) {
            if(!core.legacy())continue;
            String organ=switch(core){case MUSCLE,GRASP->"claw_0";case CARAPACE->"scapular_plate_1";case JAW->"mandible";case CREST->"shoulder_crest_1";default->throw new AssertionError();};
            float gain=TissueSkin.organGain(core,organ);assertTrue(gain>1);
            assertEquals(1,TissueSkin.organGain(core,"tissue_body"));
            float[] points=new float[48];
            for(int ring=0;ring<2;ring++)for(int i=0;i<8;i++){int at=ring*24+i*3;points[at]=(i%2==0?-1:1);points[at+1]=ring*4;points[at+2]=0;}
            TissueSkin.focus(points,gain);
            float base=0,tip=0;for(int i=0;i<24;i+=3){base+=points[i+1];tip+=points[24+i+1];}
            assertEquals(0,base,.00001F);assertEquals(32*gain,tip,.00001F);
            assertTrue(Math.abs(points[0])>1,"length gains include supporting cross-section");
        }
    }
}
