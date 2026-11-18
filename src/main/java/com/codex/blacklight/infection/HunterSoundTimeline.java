package com.codex.blacklight.infection;

import com.codex.blacklight.combat.CreatureAttack;
import java.util.List;
import java.util.function.Consumer;

/** P1 Brawler counter Sound tracks, not a sound per damage window.
 * Counter release uses the existing composed barrage clock at tick 8. */
public final class HunterSoundTimeline {
    public record Cue(int tick,InfectedSoundCue sound,float gain,float minPitch,float maxPitch,int figOffset) {}
    private static final List<Cue> OPENING=List.of(new Cue(0,InfectedSoundCue.HUNTER_GRUNT,1,1,1,54520));
    private static final List<Cue> RELEASE=List.of(
            new Cue(CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK,InfectedSoundCue.HUNTER_WHOOSH,1,1,1,56924),
            new Cue(CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK+20,InfectedSoundCue.HUNTER_WHOOSH,1,1,1,57016),
            // Source 1.66667 s, sampled at the first server tick after the track begins.
            new Cue(CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK+34,InfectedSoundCue.HUNTER_ROAR,3,.5F,1,57108));
    private HunterSoundTimeline(){}
    public static boolean owns(CreatureAttack move){return move==CreatureAttack.COUNTER||move==CreatureAttack.BARRAGE;}
    public static void between(CreatureAttack move,float previous,float age,Consumer<Cue> emit){
        if(!Float.isFinite(previous)||!Float.isFinite(age)||age<previous)return;
        var cues=move==CreatureAttack.COUNTER?OPENING:move==CreatureAttack.BARRAGE?RELEASE:List.<Cue>of();
        for(var cue:cues)if(previous<cue.tick&&age>=cue.tick)emit.accept(cue);
    }
}
