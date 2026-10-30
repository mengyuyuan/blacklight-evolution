package com.codex.blacklight.infection;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueHypertrophyTest {
    private static final V UP=new V(0,-1,0),FRONT=new V(0,0,-1),RIGHT=new V(1,0,0);
    private static Bone body() {return new Bone("body",0,new Box(new V(-4,0,-2),new V(4,12,2)),UP,FRONT,RIGHT,new V(0,6,0));}
    @Test void approvedKeysAndTopologyAreRetainedWithoutModifyingSharedArrays() {
        float[][] original={{1,1,1,2,1,1,2,1,2,1,1,2,1,2,1,2,2,1,2,2,2,1,2,2},
                {1,1,1,2,1,1,2,1,2,1,1,2,1,2,1,2,2,1,2,2,2,1,2,2},
                {1,1,1,2,1,1,2,1,2,1,1,2,1,2,1,2,2,1,2,2,2,1,2,2}};
        var mesh=new Mesh("tissue_body",true,0,"",RIGHT.array(),null,original,new float[][]{{0,1,2,3,2,0,1}});
        var result=TissueHypertrophy.extend(body(),mesh);
        assertEquals(5,result.stages().length);assertSame(mesh.faces(),result.faces());
        for(int i=0;i<3;i++)assertArrayEquals(original[i],result.stages()[i]);
        assertNotEquals(Arrays.toString(result.stages()[2]),Arrays.toString(result.stages()[4]));
    }
    @Test void enlargedShoulderPreservesBothSkinSocketsAndItsGlandOutlet() {
        var region=generate(11,List.of(body()),true).regions().get(0);
        var skin=region.meshes().stream().filter(Mesh::skin).findFirst().orElseThrow();
        for(int stage=3;stage<5;stage++) {
            assertArrayEquals(Arrays.copyOfRange(skin.stages()[2],0,24),Arrays.copyOfRange(skin.stages()[stage],0,24),.0001F);
            int end=skin.stages()[2].length;
            assertArrayEquals(Arrays.copyOfRange(skin.stages()[2],end-24,end),Arrays.copyOfRange(skin.stages()[stage],end-24,end),.0001F);
        }
        var gland=region.meshes().stream().filter(m->m.name().equals("tendril_gland")).findFirst().orElseThrow();
        for(int stage=0;stage<5;stage++)for(int a=0;a<3;a++) {
            float centre=0;for(int i=gland.stages()[stage].length-24+a;i<gland.stages()[stage].length;i+=3)centre+=gland.stages()[stage][i];
            assertEquals(region.outlet()[stage][a],centre/8,.0001F,"visible tendon must begin inside the enlarged gland");
        }
    }
    @Test void supportClawsGrowForwardAndRetainTheirActualHinge() {
        var leg=new Bone("left_front_leg",3,new Box(new V(-2,0,-2),new V(2,12,2)),UP,FRONT,RIGHT,new V(0,0,0));
        var region=generate(1,List.of(body(),leg),true).regions().stream().filter(r->r.name().equals(leg.path())).findFirst().orElseThrow();
        var claw=region.meshes().stream().filter(m->m.motion().equals("toe")).findFirst().orElseThrow();
        float mature=0,ancient=0;
        for(int i=0;i<claw.stages()[2].length;i+=3) {
            mature=Math.min(mature,claw.stages()[2][i+2]);ancient=Math.min(ancient,claw.stages()[4][i+2]);
            assertTrue(claw.stages()[4][i+1]<=12.01F);
        }
        assertTrue(ancient<mature-1,"a later claw must gain reach, not only change texture");
        for(int stage=2;stage<5;stage++)for(int a=0;a<3;a++) {
            float centre=0;for(int i=a;i<24;i+=3)centre+=claw.stages()[stage][i];
            assertEquals(centre/8,claw.pivots()[stage][a],.0001F,"the widening paw and its digit hinge grow together");
        }
        assertTrue(extent(claw.stages()[4],0)>extent(claw.stages()[2],0)*1.1F,"digits gain width as well as reach");
    }
    @Test void torsoDevelopsInThreeDimensionsBetweenItsOriginalSockets() {
        var region=generate(11,List.of(body()),true).regions().get(0);
        var skin=region.meshes().stream().filter(Mesh::skin).findFirst().orElseThrow();
        float width=extent(skin.stages()[4],0)/extent(skin.stages()[2],0);
        float depth=extent(skin.stages()[4],2)/extent(skin.stages()[2],2);
        assertTrue(width>1.1F&&depth>1.1F&&Math.max(width,depth)/Math.min(width,depth)<1.2F,"the growing shoulder gains a coherent cross-section");
        assertEquals(extent(skin.stages()[2],1),extent(skin.stages()[4],1),.0001F);
        for(int a=0;a<3;a++) {
            float change=0;for(int i=a;i<skin.stages()[2].length;i+=3)change=Math.max(change,Math.abs(skin.stages()[4][i]-skin.stages()[2][i]));
            assertTrue(change>.01F,"internal sections develop along every axis while the neck/hip sockets stay put");
        }
    }
    @Test void shoulderBladeDevelopsABroadRootInsteadOfOnlyLengthening() {
        var region=generate(11,List.of(body()),true).regions().get(0);
        var crest=region.meshes().stream().filter(m->m.name().startsWith("shoulder_crest")).findFirst().orElseThrow();
        float rootWidth=extent(Arrays.copyOf(crest.stages()[4],24),0)/extent(Arrays.copyOf(crest.stages()[2],24),0);
        float length=extent(crest.stages()[4],1)/extent(crest.stages()[2],1);
        float rootDepth=extent(Arrays.copyOf(crest.stages()[4],24),2)/extent(Arrays.copyOf(crest.stages()[2],24),2);
        assertTrue(rootWidth>1.15F&&rootDepth>1.15F&&length<1.35F,"root width and thickness support the organ in both section axes");
    }
    @Test void attachmentDevelopsBeforeMostOfTheTipReach() {
        var region=generate(11,List.of(body()),true).regions().get(0);
        var crest=region.meshes().stream().filter(m->m.name().startsWith("shoulder_crest")).findFirst().orElseThrow();
        float root0=extent(Arrays.copyOf(crest.stages()[2],24),0);
        float root1=extent(Arrays.copyOf(crest.stages()[3],24),0);
        float root2=extent(Arrays.copyOf(crest.stages()[4],24),0);
        float stem0=centreDistance(crest.stages()[2]),stem1=centreDistance(crest.stages()[3]),stem2=centreDistance(crest.stages()[4]);
        assertTrue((root1-root0)/(root2-root0)>.65F,"root mass is established in the first late stage");
        assertTrue((stem1-stem0)/(stem2-stem0)<.5F,"most extension follows the supporting tissue");
    }
    @Test void developmentGenotypeIsStableAndDoesNotRewriteMatureAnatomy() {
        var base=generate(11,List.of(body()),true).regions().get(0).meshes().get(0);
        var original=new Mesh(base.name(),base.skin(),base.phase(),base.motion(),base.axis(),base.pivots(),Arrays.copyOf(base.stages(),3),base.faces());
        Mesh a=TissueHypertrophy.extend(body(),original,171),b=TissueHypertrophy.extend(body(),original,171),other=TissueHypertrophy.extend(body(),original,172);
        for(int k=0;k<5;k++)assertArrayEquals(a.stages()[k],b.stages()[k]);
        for(int k=0;k<3;k++)assertArrayEquals(original.stages()[k],other.stages()[k]);
        assertFalse(Arrays.equals(a.stages()[4],other.stages()[4]));
    }
    private static float centreDistance(float[] points) {
        V first=new V(0,0,0),last=new V(0,0,0);int end=points.length-24;
        for(int i=0;i<24;i+=3){first=first.add(new V(points[i],points[i+1],points[i+2]));last=last.add(new V(points[end+i],points[end+i+1],points[end+i+2]));}
        return last.sub(first).length()/8;
    }
    private static float extent(float[] points,int axis) {
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int i=axis;i<points.length;i+=3){min=Math.min(min,points[i]);max=Math.max(max,points[i]);}
        return max-min;
    }
}
