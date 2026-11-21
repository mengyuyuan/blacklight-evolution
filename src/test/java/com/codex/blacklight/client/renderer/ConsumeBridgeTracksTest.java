package com.codex.blacklight.client.renderer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeBridgeTracksTest {
    private static void cue(String clip,int branch,int calledAt,int... begins){
        var cue=ConsumeBridgeTracks.forClip(clip);
        assertEquals(branch,cue.branch(),clip);assertEquals(calledAt,cue.frame(),clip);
        assertArrayEquals(begins,cue.tracks().stream().mapToInt(t->calledAt+t.delayFrames()).toArray(),clip);
    }
    @Test void dispatchesEachDeployedReceiverOnItsSourceClock(){
        cue("normal",92,35,35,37,38,39);cue("claws_head",92,50,50,52,53,54);cue("muscle_heart",92,40,40,42,43,44);
        cue("normal_head_stomp",93,34,34,36,37,38);cue("whip_lash",93,51,51,53,54,55);
        cue("normal_beatdown",94,100,100,101,102);
        cue("normal_knee_head",98,30,35);cue("hammer",98,58,63);cue("hammer_smush",98,61,66);
        for(String clip:List.of("claws","muscle","whip","blade"))cue(clip,0,0);
        for(String clip:List.of("stealth_neck","large","large_hammer","backspines"))assertNull(ConsumeBridgeTracks.forClip(clip),clip);
    }
    @Test void nearIdenticalGroupsKeepDifferentEndpointsAndSectionCounts(){
        var chest=ConsumeBridgeTracks.forClip("normal").tracks();var stomp=ConsumeBridgeTracks.forClip("normal_head_stomp").tracks();
        assertEquals(ConsumeBridgeTracks.Target.NECK,chest.get(1).target());
        assertEquals(ConsumeBridgeTracks.Target.HIP_R,stomp.get(1).target());
        var beat=ConsumeBridgeTracks.forClip("normal_beatdown").tracks();
        assertEquals(List.of(ConsumeBridgeTracks.Target.PELVIS,ConsumeBridgeTracks.Target.SHOULDER_L,ConsumeBridgeTracks.Target.KNEE_R),
                beat.stream().map(ConsumeBridgeTracks.Track::target).toList());
        assertTrue(beat.stream().allMatch(t->t.segments()==10));
        var single=ConsumeBridgeTracks.forClip("hammer").tracks().get(0);
        assertEquals(ConsumeBridgeTracks.Source.SPINE_2,single.source());assertEquals(ConsumeBridgeTracks.Target.SHOULDER_R,single.target());
        assertEquals(5,single.segments());
    }
}
