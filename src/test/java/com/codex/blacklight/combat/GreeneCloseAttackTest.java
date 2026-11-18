package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneCloseAttackTest {
    @Test void nativeEntranceIsStrictRangeAndDistinctFromBothMotionSlotTests(){
        assertTrue(GreeneCloseAttack.aiEligible(24.999,false,true));
        assertFalse(GreeneCloseAttack.motionEligible(false),"Both serialized left/right motion conditions require Segment1");
        assertFalse(GreeneCloseAttack.aiEligible(25,true,true));
        assertFalse(GreeneCloseAttack.aiEligible(1,false,false));
        assertEquals(GreeneCloseAttack.Side.LEFT,GreeneCloseAttack.choose(0));
        assertEquals(GreeneCloseAttack.Side.RIGHT,GreeneCloseAttack.choose(.5F));
    }
    @Test void positiveTrackDurationsFitFramesButTailUsesNative24Fps(){
        assertEquals(15,GreeneCloseAttack.sourceFrame(10),1e-6);
        assertEquals(30,GreeneCloseAttack.sourceFrame(20),1e-5);
        assertEquals(34,GreeneCloseAttack.sourceFrame(GreeneCloseAttack.TAIL*20),1e-4);
        assertEquals(53.2,GreeneCloseAttack.sourceFrame((GreeneCloseAttack.TAIL+1)*20),1e-4);
        assertEquals(3.468747,GreeneCloseAttack.DURATION,2e-6);
        assertTrue(GreeneCloseAttack.DURATION>GreeneCloseAttack.COOLDOWN_SECONDS);
    }
    @Test void firstCollisionFollowsActivationAndAdjacentWindowsRetainAuthoredOrder(){
        var clock=new GreeneCloseAttack();clock.begin(GreeneCloseAttack.Side.LEFT);
        for(int update=1;update<=27;update++)assertTrue(clock.advance().tracks().isEmpty());
        assertEquals(java.util.List.of(GreeneCloseAttack.Track.TIP),clock.advance().tracks());
        var pairs=new ArrayList<java.util.List<GreeneCloseAttack.Track>>();
        while(clock.active()){var frame=clock.advance();if(frame.tracks().size()>1)pairs.add(frame.tracks());}
        assertTrue(pairs.contains(java.util.List.of(GreeneCloseAttack.Track.TIP,GreeneCloseAttack.Track.INNER)));
        assertTrue(pairs.contains(java.util.List.of(GreeneCloseAttack.Track.INNER,GreeneCloseAttack.Track.OUTER)));
    }
    @Test void reloadKeepsPendingDeltaWithoutRepeatingActivationAndInvalidatesOldCallbacks(){
        var a=new GreeneCloseAttack();a.begin(GreeneCloseAttack.Side.RIGHT);
        for(int i=0;i<30;i++)a.advance();var before=a.advance();var saved=a.save();
        var b=new GreeneCloseAttack();b.load(saved);
        while(a.active()){
            var fa=a.advance();var fb=b.advance();
            assertEquals(fa.tracks(),fb.tracks());assertEquals(fa.elapsedSeconds(),fb.elapsedSeconds());
            assertEquals(fa.activeAfter(),fb.activeAfter());assertEquals(fa.finished(),fb.finished());
        }
        assertFalse(a.isCurrent(before));a.begin(GreeneCloseAttack.Side.LEFT);var current=a.advance();
        a.load(saved);assertFalse(a.isCurrent(current));a.clear();assertFalse(a.active());
        b.load(new CompoundTag());assertFalse(b.active());
        saved.putFloat("Seconds",Float.NaN);b.load(saved);assertFalse(b.active());
    }
    @Test void concurrentShootingKeepsMouthPoseAndChangesOnlySelectedSlugWithoutMutatingCaller(){
        var base=GreeneMotion.sample("VOLLEY",30,false);var original=Arrays.stream(base).map(float[]::clone).toArray(float[][]::new);
        var combined=GreeneCloseAttack.pose(base,GreeneCloseAttack.Side.LEFT,30,true);
        var names=GreeneMotion.table().bones;int mouth=Arrays.asList(names).indexOf("native_Mouth");
        assertArrayEquals(base[mouth],combined[mouth]);
        assertFalse(Arrays.deepEquals(base,combined),"Actual selected Slug moves while shooting continues");
        assertTrue(Arrays.deepEquals(original,base),"Mixer must not corrupt the projectile/render base pose");
        for(int i=0;i<names.length;i++)if(names[i].startsWith("native_Slug_01_"))assertArrayEquals(base[i],combined[i]);
    }
}
