package com.codex.blacklight.infection;

import com.codex.blacklight.combat.CreatureAttack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HunterSoundTimelineTest {
    @Test void counterEntryHasOneGruntAndDoesNotInventAWristWindowWhoosh(){
        var played=new ArrayList<HunterSoundTimeline.Cue>();
        HunterSoundTimeline.between(CreatureAttack.COUNTER,-1,0,played::add);
        for(int tick=1;tick<=32;tick++)HunterSoundTimeline.between(CreatureAttack.COUNTER,tick-1,tick,played::add);
        assertEquals(List.of(54520),played.stream().map(HunterSoundTimeline.Cue::figOffset).toList());
        assertEquals(InfectedSoundCue.HUNTER_GRUNT,played.get(0).sound());
    }
    @Test void releaseJoinConsumesItsEntryOnceThenFollowsTwoSourceWhooshesAndLateRoar(){
        var played=new ArrayList<HunterSoundTimeline.Cue>();int entry=CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK;
        HunterSoundTimeline.between(CreatureAttack.BARRAGE,entry-1,entry,played::add);
        // The entity changes stages and returns; no second tick of damage/audio at the join.
        for(int tick=entry+1;tick<=57;tick++)HunterSoundTimeline.between(CreatureAttack.BARRAGE,tick-1,tick,played::add);
        assertEquals(List.of(56924,57016,57108),played.stream().map(HunterSoundTimeline.Cue::figOffset).toList());
        assertEquals(List.of(8,28,42),played.stream().map(HunterSoundTimeline.Cue::tick).toList());
        var roar=played.get(2);assertEquals(3,roar.gain());assertEquals(.5F,roar.minPitch());assertEquals(1,roar.maxPitch());
    }
    @Test void unchangedOrBackwardClockCannotRepeatAConsumedEntry(){
        var played=new ArrayList<HunterSoundTimeline.Cue>();
        HunterSoundTimeline.between(CreatureAttack.BARRAGE,8,8,played::add);
        HunterSoundTimeline.between(CreatureAttack.BARRAGE,28,8,played::add);
        HunterSoundTimeline.between(CreatureAttack.BARRAGE,Float.NaN,30,played::add);
        assertTrue(played.isEmpty());
    }
    @Test void unrelatedAuthoredMovesDoNotReceiveCounterAudioAndLongStepPreservesTrackOrder(){
        var played=new ArrayList<HunterSoundTimeline.Cue>();
        for(var move:List.of(CreatureAttack.NONE,CreatureAttack.SLAM,CreatureAttack.SWIPE,CreatureAttack.LEAP,CreatureAttack.DODGE)){
            assertFalse(HunterSoundTimeline.owns(move));HunterSoundTimeline.between(move,-1,100,played::add);
        }
        assertTrue(played.isEmpty());HunterSoundTimeline.between(CreatureAttack.BARRAGE,7,43,played::add);
        assertEquals(List.of(56924,57016,57108),played.stream().map(HunterSoundTimeline.Cue::figOffset).toList());
    }
}
