package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathChargeTest {
    private static GoliathCharge.Inputs input(boolean available,double playerY,boolean supported,double bearing,int force,int disabled){
        return new GoliathCharge.Inputs(available,0,playerY,supported,bearing,0,force,disabled);
    }
    private static final GoliathCharge.Inputs AIRBORNE=input(true,1,false,0,0,0);
    private static final GoliathCharge.Inputs GROUND=input(true,0,true,0,0,0);
    private static void advance(GoliathCharge.State state,int first,int last,GoliathCharge.Inputs input){
        for(int age=first;age<=last;age++){state.advance(age,input);state.afterContact(input);}
    }
    private static Vec3 vector(JsonArray value){return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble());}
    @Test void livePhaseWindowsDoNotInheritTheOldFixedActionDuration(){
        var state=new GoliathCharge.State();state.begin();
        for(int age=1;age<=56;age++){
            state.advance(age,GROUND);
            assertTrue(GoliathCharge.spheres(state.snapshot(),age,Vec3.ZERO,0).isEmpty(),"no source Hit in windup/release/enter "+age);
            state.afterContact(GROUND);
        }
        assertEquals(3,state.snapshot().phase());
        for(int age=57;age<=76;age++){
            state.advance(age,GROUND);assertFalse(GoliathCharge.spheres(state.snapshot(),age,Vec3.ZERO,0).isEmpty());state.afterContact(GROUND);
        }
        assertEquals(6,state.snapshot().phase(),"ordinary play selects original toAggro branch, not unconditional long return");
        assertTrue(GoliathCharge.spheres(state.snapshot(),76,Vec3.ZERO,0).isEmpty());
        advance(state,77,82,GROUND);assertEquals(7,state.snapshot().phase(),"normal return sequences to native taunt58");
        advance(state,83,312,GROUND);assertTrue(state.done());assertEquals(-1,state.snapshot().phase());
    }
    @Test void nativeArcTimerSupportAndPlayerCounterAreConjoinedBeforeScriptOverride(){
        assertTrue(GoliathCharge.continueCycle(AIRBORNE,0,.5F));
        assertFalse(GoliathCharge.continueCycle(GROUND,0,.5F),"same-height supported player does not extend the cycle");
        assertTrue(GoliathCharge.continueCycle(input(true,1,true,60,0,0),0,.5F));
        assertFalse(GoliathCharge.continueCycle(input(true,1,true,60.01,0,0),0,.5F),"120 source arc means +/-60");
        assertTrue(GoliathCharge.continueCycle(input(true,1,true,360,0,0),0,.5F));
        assertFalse(GoliathCharge.continueCycle(input(false,1,false,0,0,0),0,.5F),"NPC target cannot borrow the global Player");
        assertFalse(GoliathCharge.continueCycle(AIRBORNE,1,.5F));
        assertFalse(GoliathCharge.continueCycle(AIRBORNE,0,2.5F));
        assertTrue(GoliathCharge.continueCycle(input(false,0,true,180,1,0),9,9),"separate source sequence434 overrides normal conditions");
        var angleOnly=new GoliathCharge.Inputs(true,0,1,true,180,1,0,0);
        assertTrue(GoliathCharge.continueCycle(angleOnly,0,.5F));assertFalse(GoliathCharge.continueCycle(angleOnly,1,.5F));
    }
    @Test void finalCycleContactCanStopReentryAndCancellationClearsEveryOutput(){
        var state=new GoliathCharge.State();state.begin();advance(state,1,65,AIRBORNE);
        state.advance(66,AIRBORNE);
        assertEquals(3,state.snapshot().phase());assertEquals(102,GoliathCharge.sourceFrame(state.snapshot(),66));
        assertFalse(GoliathCharge.spheres(state.snapshot(),66,Vec3.ZERO,0).isEmpty(),"closing sample must run before onEnd");
        state.playerHit();state.afterContact(AIRBORNE);assertEquals(4,state.snapshot().phase(),"last-sample player hit prevents queued restart");
        state.cancel();assertEquals(Vec3.ZERO,state.advance(67,AIRBORNE));assertTrue(state.done());
        assertTrue(GoliathCharge.spheres(state.snapshot(),67,Vec3.ZERO,0).isEmpty());
    }
    @Test void eachRepeatedCycleRetainsItsRootTravelWithoutRewindAndTimerIsNotReset(){
        var state=new GoliathCharge.State();state.begin();advance(state,1,66,AIRBORNE);
        assertEquals(3,state.snapshot().phase());assertEquals(66,state.snapshot().phaseStartAge());
        var step=state.advance(67,AIRBORNE);assertTrue(step.z>.4,"root starts from85 rather than subtracting prior102");assertEquals(0,step.y,1e-8);
        state.afterContact(AIRBORNE);advance(state,68,106,AIRBORNE);
        assertEquals(4,state.snapshot().phase(),"2.5-second parent timer survives five quantized cycles");
    }
    @Test void anUnobservedClockGapDoesNotReplaySeveralSegmentsAtOnce(){
        var state=new GoliathCharge.State();state.begin();state.advance(1000,AIRBORNE);
        assertEquals(0,state.snapshot().phase());assertEquals(55,GoliathCharge.sourceFrame(state.snapshot(),1000));
        assertTrue(GoliathCharge.spheres(state.snapshot(),1000,Vec3.ZERO,0).isEmpty());
        state.afterContact(AIRBORNE);assertEquals(1,state.snapshot().phase());assertEquals(1000,state.snapshot().phaseStartAge());
        state.advance(1001,AIRBORNE);assertEquals(56.5F,GoliathCharge.sourceFrame(state.snapshot(),1001));
    }
    @Test void positiveTimeEndFitsTheSourceFrameIntervalInsteadOfHoldingEarly(){
        var enter=new GoliathCharge.Snapshot(2,42);
        assertEquals(83.1F,GoliathCharge.sourceFrame(enter,54),.0001F,"66..85 fitted to native0.666667s, not raw30fps");
        assertTrue(GoliathCharge.sourceFrame(enter,55)<85);assertEquals(85,GoliathCharge.sourceFrame(enter,56));
        var slam=SourceBossAction.get(com.codex.blacklight.entity.EncounterKind.GOLIATH,EncounterMove.HAMMER);
        assertEquals(45,slam.sourceTick(34)/20*30,.0001,"front-slam42..48 release fits0.4s; it must not arrive at48 halfway through");
        assertEquals(54,slam.sourceTick(46)/20*30,.0001,"front-slam52..60 interval fits0.8s");
    }
    @Test void normalAndScriptedReturnsUseTheirOwnSourceClipsAndRealLastFrames(){
        for(boolean special:new boolean[]{false,true}){
            var state=new GoliathCharge.State();var input=input(true,0,true,0,0,special?1:0);state.begin();advance(state,1,76,input);
            assertEquals(special?5:6,state.snapshot().phase());
            int end=special?98:82;advance(state,77,end-1,input);state.advance(end,input);
            float frame=special?150:125;assertEquals(frame,GoliathCharge.sourceFrame(state.snapshot(),end));
            var expected=GoliathMotion.sample(special?"CHARGE":"CHARGE_RETURN",frame/30*20,false);
            var actual=GoliathCharge.pose(state.snapshot(),end);
            for(int i=0;i<actual.length;i++)assertArrayEquals(expected[i],actual[i],.00001F,"source tail is not blended into invented idle frames");
            state.afterContact(input);assertEquals(special?-1:7,state.snapshot().phase());
        }
    }
    @Test void hitSpheresMatchIndependentNativeJointIndicesAndInsertionPositions() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/goliath-charge-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var values=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("samples");
            for(var value:values){var row=value.getAsJsonObject();float age=row.get("age").getAsFloat();
                for(float yaw:new float[]{0,90,-137}){
                    var origin=new Vec3(11,7,-9);var spheres=GoliathCharge.spheres(age,origin,yaw);
                    assertEquals(row.getAsJsonArray("points").size(),spheres.size(),"inclusive native joint index chain plus inserted spheres");
                    for(var item:row.getAsJsonArray("points")){
                        var point=item.getAsJsonObject();String joint=point.get("joint").getAsString();
                        var expected=vector(point.getAsJsonArray("worldYaw0")).yRot((float)Math.toRadians(-yaw)).add(origin);
                        var sphere=spheres.stream().filter(s->s.joint().equals(joint)).findFirst().orElseThrow();
                        assertTrue(expected.distanceTo(sphere.center())<.0003,joint+" source frame="+row.get("frame")+" yaw="+yaw);
                        assertEquals(row.get("radius").getAsDouble(),sphere.radius(),.00001);
                    }
                }
            }
        }
    }
    @Test void rootTravelUsesSourceCurveOnceAndRotatesWithActor(){
        Vec3 sum=Vec3.ZERO;
        for(int age=1;age<96;age++){
            var delta=GoliathCharge.step(age,0);sum=sum.add(delta);
            assertEquals(0,delta.y,1e-8,"actor must not receive absolute Motion_Root Y=6 each tick");
            assertTrue(delta.yRot((float)Math.toRadians(-90)).distanceTo(GoliathCharge.step(age,90))<1e-7);
        }
        assertEquals(15.5463,sum.z,.01);assertEquals(0,sum.x,.0001);
        assertTrue(GoliathCharge.step(5,0).z<0,"native windup moves backward before release");
        assertTrue(GoliathCharge.step(60,0).z>.4,"native cycle displacement replaces generic fixed .40 speed");
        assertEquals(Vec3.ZERO,GoliathCharge.step(0,0));assertEquals(Vec3.ZERO,GoliathCharge.step(96,0));
    }
    @Test void sphereNarrowPhaseDoesNotTurnItsBoundingCubeIntoAHit(){
        var sphere=new GoliathCharge.Sphere("Wrist_L",new Vec3(8,6,-4),3);
        var corner=new AABB(10.7,8.7,-1.3,10.8,8.8,-1.2);
        assertTrue(new AABB(sphere.center(),sphere.center()).inflate(3).intersects(corner));
        assertNull(GoliathCharge.contact(sphere,corner));
        var front=new AABB(10.9,5.9,-4.1,11.1,6.1,-3.9);
        assertNotNull(GoliathCharge.contact(sphere,front));
        assertEquals(10.9,GoliathCharge.contact(sphere,front).x,.00001,"contact is a real target surface for cover ray");
    }
    @Test void retreatAndRecoveryClippingAreNotChargingImpactsOrStoredRootDebt(){
        for(int phase=0;phase<=12;phase++){
            var snapshot=new GoliathCharge.Snapshot(phase,0);
            assertEquals(phase==3||phase==4?GoliathCharge.Obstruction.EXPOSE:GoliathCharge.Obstruction.CLIP,
                GoliathCharge.obstruction(snapshot,true,true),"phase "+phase);
            assertEquals(GoliathCharge.Obstruction.NONE,GoliathCharge.obstruction(snapshot,true,false));
            assertEquals(GoliathCharge.Obstruction.CANCEL,GoliathCharge.obstruction(snapshot,false,true),"unloaded/border/restriction is not a physical wall impact");
        }
        assertEquals(GoliathCharge.Obstruction.CANCEL,GoliathCharge.obstruction(GoliathCharge.Snapshot.INACTIVE,true,true));
        var clipped=new GoliathCharge.State();var free=new GoliathCharge.State();clipped.begin();free.begin();
        for(int age=1;age<=10;age++){
            // The movement caller may accept none of the retreat. Both source
            // clocks still observe it; opening the wall cannot repay that debt.
            clipped.advance(age,GROUND);free.advance(age,GROUND);
            clipped.afterContact(GROUND);free.afterContact(GROUND);
        }
        assertEquals(free.advance(11,GROUND),clipped.advance(11,GROUND));
        clipped.cancel();assertEquals(Vec3.ZERO,clipped.advance(12,GROUND));
    }
    @Test void sourceSphereTangencySurvivesEntityEnumerationWithoutIncreasingItsRadius(){
        for(double x:new double[]{0,29_000_000}){
            var spheres=GoliathCharge.spheres(new GoliathCharge.Snapshot(3,0),1,new Vec3(x,10,0),0);
            var sphere=spheres.stream().max(java.util.Comparator.comparingDouble(s->s.center().x+s.radius())).orElseThrow();
            double edge=sphere.center().x+sphere.radius();var c=sphere.center();
            var touching=new AABB(edge,c.y-.05,c.z-.05,edge+.1,c.y+.05,c.z+.05);
            hTouch(spheres,sphere,touching);
            assertNull(GoliathCharge.contact(sphere,touching.move(.001,0,0)),"query padding cannot turn a real gap into damage");
        }
    }
    private static void hTouch(java.util.List<GoliathCharge.Sphere> spheres,GoliathCharge.Sphere sphere,AABB touching){
        AABB old=null;for(var p:spheres){var b=new AABB(p.center(),p.center()).inflate(p.radius());old=old==null?b:old.minmax(b);}
        assertFalse(old.intersects(touching),"strict vanilla candidate query used to discard this actual sphere contact");
        assertNotNull(GoliathCharge.contact(sphere,touching));
        assertTrue(GoliathCharge.bounds(spheres).intersects(touching));
    }
}
