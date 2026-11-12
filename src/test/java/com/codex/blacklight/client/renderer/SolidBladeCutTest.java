package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SolidBladeCutTest {
    static final SolidBladeCut.Vertices<Vec3> V=new SolidBladeCut.Vertices<>(){
        public Vec3 position(Vec3 p){return p;}public Vec3 mix(Vec3 a,Vec3 b,double t){return a.lerp(b,t);}
        public Vec3 cap(Vec3 p,Vec3 n){return p;}public Vec3 flip(Vec3 p){return p;}
    };
    static List<List<Vec3>> box(){return FiniteMeshCutTest.box(new Vec3(-1,-1,-1),new Vec3(1,1,1),1);}
    static SolidBladeCut.Result<Vec3> cut(List<List<Vec3>> mesh,SweptBladeSurface surface){return SolidBladeCut.subtract(mesh,V,surface.triangles(),6144);}
    @Test void throughCutCreatesClosedHalvesAtTheActualBlade(){
        var result=cut(box(),FiniteMeshCutTest.plane(-1.2,1.2,-1.2,1.2));
        assertTrue(result.changed(),result.reason());assertEquals(2,result.pieces().size(),result.reason());
        assertEquals(8-4*.006,result.pieces().stream().mapToDouble(p->SolidBladeCut.volume(p,V)).sum(),1e-7);
        for(var piece:result.pieces())assertTrue(FiniteMeshCut.closed(piece,V));
    }
    @Test void twoPartialStrokesFinishASectionWithoutPrematurelyDetachingIt(){
        var first=cut(box(),FiniteMeshCutTest.plane(-1.2,1.2,-1.2,0));
        assertTrue(first.changed(),first.reason());assertEquals(1,first.pieces().size());
        var second=cut(first.pieces().getFirst(),FiniteMeshCutTest.plane(-1.2,1.2,-.01,1.2));
        assertTrue(second.changed(),second.reason());assertEquals(2,second.pieces().size(),second.reason());
        for(var piece:second.pieces())assertTrue(FiniteMeshCut.closed(piece,V));
        assertEquals(8-4*.006,second.pieces().stream().mapToDouble(p->SolidBladeCut.volume(p,V)).sum(),1e-7);
    }
    @Test void intersectingPartialCutsDetachACornerWithTwoDifferentCutFaces(){
        var first=cut(box(),new SweptBladeSurface(List.of(new BladeSpan(new Vec3(-1.2,0,-1.2),new Vec3(.01,0,-1.2)),new BladeSpan(new Vec3(-1.2,0,1.2),new Vec3(.01,0,1.2)))));
        assertEquals(1,first.pieces().size(),first.reason());
        var next=new SweptBladeSurface(List.of(new BladeSpan(new Vec3(0,-.01,-1.2),new Vec3(0,1.2,-1.2)),new BladeSpan(new Vec3(0,-.01,1.2),new Vec3(0,1.2,1.2))));
        var second=cut(first.pieces().getFirst(),next);assertEquals(2,second.pieces().size(),second.reason());
        double small=second.pieces().stream().mapToDouble(p->SolidBladeCut.volume(p,V)).min().orElseThrow();assertTrue(small>1.95&&small<2.01);
        for(var piece:second.pieces())assertTrue(FiniteMeshCut.closed(piece,V));
    }
    @Test void missesAndOpenDecorationsDoNotBecomeSolidFragments(){
        assertFalse(cut(box(),FiniteMeshCutTest.plane(2,3,2,3)).changed());
        var open=new ArrayList<>(box());open.removeLast();assertEquals("open-skin",cut(open,FiniteMeshCutTest.plane(-1.2,1.2,-1.2,1.2)).reason());
    }
    @Test void anInternalKerfIsNotAnIndependentFragmentAndIsNotFilledOnTheNextCut(){
        var inner=FiniteMeshCutTest.plane(-.3,.3,-.3,.3);var first=cut(box(),inner);
        assertTrue(first.changed(),first.reason());assertEquals(1,first.pieces().size());
        assertEquals(8-.6*.6*.006,SolidBladeCut.volume(first.pieces().getFirst(),V),1e-7);
        var repeat=cut(first.pieces().getFirst(),inner);assertFalse(repeat.changed(),repeat.reason());
    }
    @Test void aCurvedSweepCanFinishAcrossManySmallJobsWithoutOpeningTheSkin(){
        List<List<List<Vec3>>> pieces=List.of(box());double before=8;
        for(int i=0;i<12;i++){
            double z=-1.2+i*.2,next=z+.2;
            var surface=new SweptBladeSurface(List.of(
                    new BladeSpan(new Vec3(-1.2,.18*Math.sin(z),z),new Vec3(1.2,.18*Math.sin(z),z)),
                    new BladeSpan(new Vec3(-1.2,.18*Math.sin(next),next),new Vec3(1.2,.18*Math.sin(next),next))));
            var nextPieces=new ArrayList<List<List<Vec3>>>();
            for(var piece:pieces){var result=cut(piece,surface);assertFalse(result.reason().startsWith("budget")||result.reason().startsWith("open-"),result.reason());
                if(result.changed())nextPieces.addAll(result.pieces());else nextPieces.add(piece);}
            pieces=nextPieces;for(var piece:pieces)assertTrue(FiniteMeshCut.closed(piece,V));
            double volume=pieces.stream().mapToDouble(p->SolidBladeCut.volume(p,V)).sum();assertTrue(volume<=before+1e-8);before=volume;
        }
        assertEquals(2,pieces.size());assertTrue(before>7.96&&before<7.98);
    }
    @Test void anObliqueCutCreatesAnOffCenterSection(){
        var surface=new SweptBladeSurface(List.of(
                new BladeSpan(new Vec3(-1.2,-.3,-1.2),new Vec3(1.2,.66,-1.2)),
                new BladeSpan(new Vec3(-1.2,-.3,1.2),new Vec3(1.2,.66,1.2))));
        var result=cut(box(),surface);assertEquals(2,result.pieces().size(),result.reason());
        var volumes=result.pieces().stream().mapToDouble(p->SolidBladeCut.volume(p,V)).sorted().toArray();
        assertTrue(volumes[1]-volumes[0]>1);for(var piece:result.pieces())assertTrue(FiniteMeshCut.closed(piece,V));
    }
    @Test void overlappingModelCubesAreOneInteriorBeforeCutting(){
        var mesh=new ArrayList<>(box());mesh.addAll(FiniteMeshCutTest.box(new Vec3(0,-1,-1),new Vec3(2,1,1),1));
        var result=cut(mesh,FiniteMeshCutTest.plane(-1.2,2.2,-1.2,1.2));
        assertTrue(result.changed(),result.reason());assertEquals(2,result.pieces().size(),result.reason());
        assertEquals(12-6*.006,result.pieces().stream().mapToDouble(p->SolidBladeCut.volume(p,V)).sum(),1e-6);
        for(var piece:result.pieces())assertTrue(FiniteMeshCut.closed(piece,V));
    }
}
