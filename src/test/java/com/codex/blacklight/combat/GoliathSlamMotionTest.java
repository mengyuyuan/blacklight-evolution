package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathSlamMotionTest {
    // Independent original frame18..42 translation [-1.2666015625,6,5.21875]
    // from behemoth.p3d2256349; model scale from the recorded original bind.
    private static final Vec3 RETREAT=new Vec3(-.9578891847057928,0,-3.9467693161663506);
    @Test void originalRetreatAndReleaseUseTheExistingSlamClockOnce(){
        var state=new GoliathSlamMotion.State();state.begin();Vec3 sum=Vec3.ZERO;
        assertEquals(Vec3.ZERO,state.advance(0,0));
        for(int age=1;age<=40;age++){
            var delta=state.advance(age,0);assertEquals(0,delta.y,0,"absolute sourceY6 must never move the actor vertically");sum=sum.add(delta);
            if(age==20||age==30)assertTrue(sum.distanceTo(RETREAT)<.00001,"original root plateau before release");
            if(age==38)assertTrue(sum.length()<.00001,"unobstructed release returns by the unchanged Hit frame");
        }
        assertEquals(38,SourceBossAction.get(EncounterKind.GOLIATH,EncounterMove.HAMMER).contact());
        var root=GoliathMotion.table().point(GoliathMotion.attack(EncounterMove.HAMMER,20),"source_Motion_Root");
        assertEquals(0,root.x,.00001);assertEquals(0,root.z,.00001,"pose must not carry the same horizontal root travel a second time");
    }
    @Test void everyStepTurnsWithActorYawWithoutChangingSourceDistance(){
        var a=new GoliathSlamMotion.State();var b=new GoliathSlamMotion.State();a.begin();b.begin();
        for(int age=1;age<=40;age++)assertTrue(a.advance(age,0).yRot((float)Math.toRadians(-137)).distanceTo(b.advance(age,137))<1e-8);
    }
    @Test void clippingDoesNotProduceStoredDebtAndSkippedTicksNeverCatchUp(){
        var state=new GoliathSlamMotion.State();state.begin();
        for(int age=1;age<=20;age++)state.advance(age,0); // movement caller rejects the retreat
        assertEquals(Vec3.ZERO,state.advance(20,0),"same age cannot move twice");
        assertEquals(Vec3.ZERO,state.advance(21,0),"opening a wall during the plateau cannot repay blocked retreat");
        assertEquals(Vec3.ZERO,state.advance(34,0),"a clock gap cannot teleport through the unobserved release");
        assertTrue(state.advance(35,0).distanceTo(GoliathSlamMotion.sourceOffset(35).subtract(GoliathSlamMotion.sourceOffset(34)))<1e-8);
    }
    @Test void cancellationReloadPolicyAndNewBeginCannotReuseOldActionDelta(){
        var state=new GoliathSlamMotion.State();state.begin();
        for(int age=1;age<=20;age++)state.advance(age,0);
        state.cancel();assertEquals(Vec3.ZERO,state.advance(31,0));assertEquals(Vec3.ZERO,state.advance(38,0));
        state.begin();var fresh=new GoliathSlamMotion.State();fresh.begin();
        for(int age=1;age<=40;age++)assertEquals(fresh.advance(age,0),state.advance(age,0));
        assertEquals(Vec3.ZERO,state.advance(1000,0));assertEquals(Vec3.ZERO,state.advance(1001,0));
    }
}
