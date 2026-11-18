package com.codex.blacklight.combat;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HydraHurtContactTest {
    private static final Vec3 SLUG_SAMPLE=new Vec3(.7389640237677464,5.112649713712703,.22943394222028923);
    private static List<HydraContact.Region> frame(int sourceFrame,int broken){
        return GreeneContact.regions(GreeneMotion.sample("idle",sourceFrame*20/24F,false),Vec3.ZERO,0,broken);
    }
    private static String nearest(List<HydraContact.Region> regions,Vec3 point){
        return regions.stream().filter(r->!r.boxes().isEmpty()).min(Comparator.comparingDouble(r->r.boxes().stream()
                .mapToDouble(b->b.nearest(point).distanceToSqr(point)).min().orElseThrow())).orElseThrow().bone();
    }
    private static WeaponCutContact.Report swept(){
        // Shipped native idle frame 0 -> 1 (41.67 ms): this Slug surface moves
        // clear of a point shared by weak_2 and neck_upper in the next frame.
        // A sub-tick hit must retain its original receiver through stroke/path.
        var old=frame(0,0);
        var actual=HydraHurtContact.ray(old,SLUG_SAMPLE,old.get(0).boxes().get(0).center,com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
        assertNotNull(actual);assertEquals("weak_0",actual.bone(),"old native skin really receives this contact before animation advances");
        var touch=new WeaponCutContact.Contact(32,"claw-edge",actual.point(),new Vec3(.02,0,0),new Vec3(-1,0,0)).onBody(actual.bone());
        return new WeaponCutContact.Report(WeaponCutContact.Kind.SLICE,List.of(touch)).stroke(57);
    }
    @Test void movingNativeSlugRetainsTheActualSweptReceiverAcrossFrames(){
        assertTrue(frame(0,0).get(0).boxes().stream().mapToDouble(b->b.nearest(SLUG_SAMPLE).distanceToSqr(SLUG_SAMPLE)).min().orElseThrow()<1e-9);
        var current=frame(1,0);
        assertTrue(current.get(0).boxes().stream().noneMatch(b->b.contains(SLUG_SAMPLE)),"original Jaw3 moves off the old contact in the next native frame");
        assertTrue(current.get(2).boxes().stream().anyMatch(b->b.contains(SLUG_SAMPLE)),"Slug3 Spine4/5 now contain the old Slug1 contact");
        assertTrue(current.stream().filter(r->r.bone().equals("neck_upper")).flatMap(r->r.boxes().stream()).anyMatch(b->b.contains(SLUG_SAMPLE)),"core overlap also exists but is ordered after the health children");
        assertEquals("weak_2",nearest(current,SLUG_SAMPLE),"old reclassification would select a different live health child");
        var report=swept();assertEquals(57,report.contacts().get(0).stroke());assertEquals("weak_0",report.contacts().get(0).receivingBone());
        assertEquals("weak_2",nearest(current,report.contacts().get(0).point()),"actual old-frame ray point also exposes the same reclassification");
        var hit=HydraHurtContact.reportedContact(frame(1,0),report);
        assertNotNull(hit);assertEquals("weak_0",hit.bone());assertEquals(report.contacts().get(0).point(),hit.point(),"never teleport the actual contact to the new frame");
    }
    @Test void brokenOrHiddenSweptReceiverCannotRedirectItsHitToTheCore(){
        assertNull(HydraHurtContact.reportedContact(frame(1,1),swept()));
        var hidden=frame(1,0).stream().map(r->new HydraContact.Region(r.bone(),List.of(),r.bounds())).toList();
        assertNull(HydraHurtContact.reportedContact(hidden,swept()));
    }
    @Test void unknownReceiverAndEmptyReportCannotInventContact(){
        var wrong=new WeaponCutContact.Contact(32,"claw-edge",SLUG_SAMPLE,new Vec3(.02,0,0),Vec3.ZERO).onBody("weak_99");
        assertNull(HydraHurtContact.reportedContact(frame(1,0),new WeaponCutContact.Report(WeaponCutContact.Kind.SLICE,List.of(wrong))));
        assertNull(HydraHurtContact.reportedContact(frame(1,0),WeaponCutContact.Report.NONE));
    }
}
