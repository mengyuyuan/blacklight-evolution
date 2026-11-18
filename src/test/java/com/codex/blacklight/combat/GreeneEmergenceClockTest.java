package com.codex.blacklight.combat;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.combat.GreeneEmergenceClock.Track.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authored but deliberately not executed while the user's test hold is active. */
class GreeneEmergenceClockTest {
    @Test void resetArmsRootSweepWithoutEmittingAndR20StartsOnTheFollowingPoll(){
        var clock=new GreeneEmergenceClock();
        assertTrue(clock.advance().tracks().isEmpty());clock.reset();
        assertEquals(1,clock.activeCount());assertEquals(0F,clock.seconds());
        var first=clock.advance();assertEquals(List.of(R3),first.tracks());
        assertEquals(0F,first.elapsedSeconds());assertEquals(.05F,clock.seconds());
        for(int age=2;age<=6;age++)assertEquals(List.of(R3),clock.advance().tracks());
        var activation=clock.advance();
        assertEquals(List.of(R3),activation.tracks());assertEquals(2,activation.activeAfter());
        assertEquals(List.of(R20,R3),clock.advance().tracks(),"source order wins over activation order");
    }

    @Test void finalR3PollSurvivesFinalizationAndReceiptsCanClearOnlyAfterDispatch(){
        var clock=new GreeneEmergenceClock();clock.reset();
        GreeneEmergenceClock.Frame before=null;
        for(int age=1;age<=59;age++)before=clock.advance();
        assertNotNull(before);assertEquals(List.of(R3),before.tracks());assertTrue(clock.hasActive());
        var last=clock.advance();
        assertEquals(List.of(R3),last.tracks());assertEquals(0,last.activeAfter());
        assertFalse(clock.hasActive());assertTrue(clock.isCurrent(last));
        assertThrows(UnsupportedOperationException.class,()->last.tracks().clear());
        assertTrue(clock.advance().tracks().isEmpty());assertFalse(clock.isCurrent(last));
    }

    @Test void exactZeroRemainingIsNotExpiredBeforeOrAfterItsPoll(){
        var clock=new GreeneEmergenceClock();clock.reset();var snapshot=clock.save();
        // A representable equality boundary: no tolerance or >= comparison may erase it.
        snapshot.getCompound("R3").putFloat("Remaining",0F);
        clock.load(snapshot);
        var equality=clock.advance();assertEquals(List.of(R3),equality.tracks());assertEquals(1,equality.activeAfter());
        var negative=clock.advance();assertEquals(List.of(R3),negative.tracks());assertEquals(0,negative.activeAfter());
        assertTrue(clock.advance().tracks().isEmpty(),"R20 is still pending, not an immediate catch-up hit");
    }

    @Test void sourceValuesRemainRawAndDoNotSilentlyUseMinecraftDamageOrScale(){
        assertEquals(.33333298563957214F,R20.begin());assertEquals(1F,R20.end());
        assertEquals(20F,R20.radius());assertEquals(800F,R20.damage());
        assertEquals(.009999999776482582F,R20.friendlyFire());assertFalse(R20.useShapecast());
        assertEquals(0F,R3.begin());assertEquals(2.9333300590515137F,R3.end());
        assertEquals(3F,R3.radius());assertEquals(100F,R3.damage());
        assertEquals(.20000000298023224F,R3.friendlyFire());assertTrue(R3.useShapecast());
    }

    @Test void saveAtR20ActivationPreservesTheUnconsumedFirstPollAndDelta(){
        var original=new GreeneEmergenceClock();original.reset();
        for(int i=0;i<7;i++)original.advance();
        var restored=new GreeneEmergenceClock();restored.load(original.save());
        assertEquals(original.seconds(),restored.seconds());assertEquals(2,restored.activeCount());
        for(int i=0;i<60;i++){
            var a=original.advance();var b=restored.advance();
            assertEquals(a.tracks(),b.tracks());assertEquals(a.elapsedSeconds(),b.elapsedSeconds());
            assertEquals(a.activeAfter(),b.activeAfter());
        }
        assertFalse(restored.hasActive());
    }

    @Test void restoreAfterR20ExpiryDoesNotRearmItOrCatchUp(){
        var clock=new GreeneEmergenceClock();clock.reset();
        for(int i=0;i<30;i++)clock.advance();
        var oldFrame=clock.advance();var snapshot=clock.save();float savedSeconds=clock.seconds();
        clock.load(snapshot);assertFalse(clock.isCurrent(oldFrame));
        assertEquals(savedSeconds,clock.seconds());assertEquals(1,clock.activeCount());
        assertEquals(List.of(R3),clock.advance().tracks());
    }

    @Test void callbackCancellationInvalidatesTheRemainingStableSnapshot(){
        var clock=new GreeneEmergenceClock();clock.reset();
        for(int i=0;i<7;i++)clock.advance();var frame=clock.advance();
        assertEquals(List.of(R20,R3),frame.tracks());
        var consumed=new java.util.ArrayList<GreeneEmergenceClock.Track>();
        for(var track:frame.tracks()){
            if(!clock.isCurrent(frame))break;
            consumed.add(track);clock.clear(); // An external hurt callback removed the owner.
        }
        assertEquals(List.of(R20),consumed);assertFalse(clock.hasActive());
        assertTrue(clock.advance().tracks().isEmpty());
        var canceled=clock.save();clock.reset();clock.load(canceled);
        assertTrue(clock.advance().tracks().isEmpty(),"loading cancellation must not revive R3");
    }

    @Test void resetAndOtherClockFramesCannotAuthorizeOldCallbacks(){
        var first=new GreeneEmergenceClock();var second=new GreeneEmergenceClock();
        first.reset();second.reset();var a=first.advance();var b=second.advance();
        assertFalse(first.isCurrent(b));assertFalse(second.isCurrent(a));
        first.reset();assertFalse(first.isCurrent(a));
        assertEquals(List.of(R3),first.advance().tracks());
    }

    @Test void missingPartialAndNonfiniteSnapshotsFailClosed(){
        var clock=new GreeneEmergenceClock();
        for(String field:List.of("Seconds","Remaining","PriorDelta")){
            clock.reset();var tag=clock.save();
            if(field.equals("Seconds"))tag.putFloat(field,Float.NaN);
            else tag.getCompound("R3").putFloat(field,Float.POSITIVE_INFINITY);
            clock.load(tag);assertFalse(clock.hasActive());assertTrue(clock.advance().tracks().isEmpty());
        }
        clock.reset();var partial=clock.save();partial.remove("R20");clock.load(partial);
        assertTrue(clock.advance().tracks().isEmpty());
        clock.reset();clock.load(new CompoundTag());assertTrue(clock.advance().tracks().isEmpty());
        clock.reset();clock.load(null);assertTrue(clock.advance().tracks().isEmpty());
    }

    @Test void inconsistentAndOutOfRangeSnapshotsFailClosed(){
        var clock=new GreeneEmergenceClock();clock.reset();var future=clock.save();
        future.putFloat("Seconds",100F);clock.load(future);
        assertTrue(clock.advance().tracks().isEmpty());
        clock.reset();var contradictory=clock.save();
        contradictory.getCompound("R3").putBoolean("Finished",true);clock.load(contradictory);
        assertTrue(clock.advance().tracks().isEmpty());
        clock.reset();var disabled=clock.save();disabled.putBoolean("Enabled",false);clock.load(disabled);
        assertTrue(clock.advance().tracks().isEmpty());
        clock.reset();var impossibleDelta=clock.save();
        impossibleDelta.getCompound("R3").putFloat("PriorDelta",.1F);clock.load(impossibleDelta);
        assertTrue(clock.advance().tracks().isEmpty());
    }
}
