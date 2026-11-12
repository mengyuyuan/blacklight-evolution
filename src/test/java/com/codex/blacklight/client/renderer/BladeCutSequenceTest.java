package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BladeCutSequenceTest {
    private static BladeSpan sample(double time,double x){return new BladeSpan(List.of(new Vec3(x,-1,0),new Vec3(x,1,0)),time,Vec3.ZERO,180);}
    @Test void aCompletedCutKeepsNewMotionButDoesNotReplayTheOldSweep(){
        var sequence=new BladeCutSequence();var a=sample(1,-1);var b=sample(2,0);var c=sample(3,1);
        sequence.record(a);sequence.record(b);var job=sequence.snapshot();String first=sequence.key("claw");
        sequence.record(c);var fragment=sequence.copy();fragment.advance(job.getLast());
        assertEquals(List.of(b,c),fragment.snapshot());assertNotEquals(first,fragment.key("claw"));
        assertEquals(List.of(a,b,c),sequence.snapshot());assertEquals(List.of(a,b),job);
        assertEquals(0,sequence.generation());assertEquals(1,fragment.generation());
    }
    @Test void aFragmentNeedsFreshMotionToCutAgain(){
        var sequence=new BladeCutSequence();sequence.record(sample(1,-1));var end=sample(2,0);sequence.record(end);
        sequence.advance(end);assertEquals(1,sequence.snapshot().size());assertTrue(new SweptBladeSurface(sequence.snapshot()).empty());
        sequence.record(sample(3,1));assertFalse(new SweptBladeSurface(sequence.snapshot()).empty());
    }
    @Test void theSameEdgeCutsAClosedDescendantUsingOnlyItsNewMotion(){
        var sequence=new BladeCutSequence();
        var enter=section(1,.17,-1.2);var exit=section(2,.17,1.2);
        sequence.record(enter);sequence.record(exit);var firstJob=sequence.snapshot();
        // This movement arrives while the first geometry job is still running.
        sequence.record(section(3,-.37,1.2));sequence.record(section(4,-.37,-1.2));
        var original=FiniteMeshCutTest.box(new Vec3(-1,-1,-1),new Vec3(1,1,1),4);
        var first=FiniteMeshCut.trace(original,FiniteMeshCutTest.VERTICES,new SweptBladeSurface(firstJob));
        assertEquals(2,first.pieces().size(),first.reason());
        var lower=first.pieces().stream().filter(p->p.faces().stream().flatMap(List::stream).anyMatch(v->v.y<-.9)).findFirst().orElseThrow();
        var closed=new ArrayList<>(lower.faces());
        // Use the production quad encoding of cap triangles, including the duplicate vertex.
        for(var cap:lower.caps())for(var t:cap.triangles())closed.add(List.of(t[0],t[1],t[2],t[2]));
        var descendant=sequence.copy();descendant.advance(firstJob.getLast());
        assertEquals(3,descendant.snapshot().size());assertEquals(exit,descendant.snapshot().getFirst());
        var surface=new SweptBladeSurface(descendant.snapshot());
        var refined=FiniteMeshCut.refine(closed,FiniteMeshCutTest.VERTICES,surface,.075,6144);
        assertFalse(refined.isEmpty());var second=FiniteMeshCut.trace(refined,FiniteMeshCutTest.VERTICES,surface);
        assertEquals(2,second.pieces().size(),second.reason());
        assertEquals(FiniteMeshCutTest.area(closed),second.pieces().stream().mapToDouble(p->FiniteMeshCutTest.area(p.faces())).sum(),1e-7);
        for(var piece:second.pieces())for(var cap:piece.caps())for(var t:cap.triangles())for(var point:t)assertTrue(surface.covers(point));
        descendant.advance(descendant.snapshot().getLast());assertEquals(2,descendant.generation());
        assertTrue(new SweptBladeSurface(descendant.snapshot()).empty());
        assertEquals(0,sequence.generation());
    }
    private static BladeSpan section(double time,double y,double z){
        return new BladeSpan(List.of(new Vec3(-1.2,y,z),new Vec3(1.2,y,z)),time,Vec3.ZERO,180);
    }
    @Test void completedBoundaryIsRetainedEvenAfterSamplingWasCompacted(){
        var sequence=new BladeCutSequence();var through=sample(50,.5);
        for(int i=0;i<150;i++)sequence.record(sample(i,i*.01));
        assertTrue(sequence.snapshot().size()<=64);sequence.advance(through);
        assertEquals(through,sequence.snapshot().getFirst());assertEquals(149,sequence.snapshot().getLast().time());
        assertTrue(sequence.snapshot().subList(1,sequence.snapshot().size()).stream().allMatch(s->s.time()>50));
        sequence.record(sample(20,-3));assertEquals(149,sequence.snapshot().getLast().time());
    }
}
