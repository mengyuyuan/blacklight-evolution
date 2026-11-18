package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The two P1 coming_out Hit tracks, with the native action-list phase order.
 * See art/encounters/greene-emergence-hit-source.json. A fixed .05-second update
 * is the Minecraft adaptation; source track times and arithmetic remain floats.
 * This class does not query entities, apply damage, or execute callbacks. */
public final class GreeneEmergenceClock {
    public static final float STEP_SECONDS=.05F;
    private static final int SAVE_VERSION=1;

    /** Declaration order is the source track-list order, not activation order.
     * Radius, damage and friendliness are raw P1 values. Both effective origins
     * are the actor root: the R3 authored Neck_1 is absent from MotherCore. */
    public enum Track {
        R20(.33333298563957214F,1F,20F,800F,.009999999776482582F,false),
        R3(0F,2.9333300590515137F,3F,100F,.20000000298023224F,true);

        private final float begin,end,radius,damage,friendlyFire;
        private final boolean useShapecast;

        Track(float begin,float end,float radius,float damage,float friendlyFire,boolean useShapecast){
            this.begin=begin;this.end=end;this.radius=radius;this.damage=damage;
            this.friendlyFire=friendlyFire;this.useShapecast=useShapecast;
        }
        public float begin(){return begin;}
        public float end(){return end;}
        public float radius(){return radius;}
        public float damage(){return damage;}
        public float friendlyFire(){return friendlyFire;}
        public boolean useShapecast(){return useShapecast;}
        private float duration(){return end-begin;}
    }

    /** tracks is the immutable poll1 snapshot. elapsedSeconds is the list clock
     * BEFORE this advance, while activeAfter includes tracks newly activated
     * for the NEXT poll. Even an expiring track stays in this frame's snapshot.
     * Check isCurrent before/after external callbacks. Clear shared hit receipts
     * only after consuming a still-current frame whose activeAfter is zero. */
    public record Frame(List<Track> tracks,float elapsedSeconds,int activeAfter,long revision) {
        public Frame {tracks=List.copyOf(tracks);}
    }

    private static final Track[] ORDER=Track.values();
    private final boolean[] active=new boolean[ORDER.length],finished=new boolean[ORDER.length];
    private final float[] remaining=new float[ORDER.length],priorDelta=new float[ORDER.length];
    private boolean enabled;
    private float seconds;
    private long revision;
    private Frame currentFrame;

    public GreeneEmergenceClock(){clear();}

    /** Enter coming_out at clock zero. R3 is activated, but no poll occurs here. */
    public void reset(){
        clear();enabled=true;
        for(Track track:ORDER){
            int i=track.ordinal();finished[i]=false;remaining[i]=track.duration();
            active[i]=track.begin()==0F;
        }
    }

    /** Cancel all present and future hits, invalidating an in-flight frame. */
    public void clear(){
        enabled=false;seconds=0F;currentFrame=null;revision++;
        Arrays.fill(active,false);Arrays.fill(finished,true);
        Arrays.fill(remaining,0F);Arrays.fill(priorDelta,0F);
    }

    /** Exactly one observed 20 Hz update. The caller supplies world-time
     * deduplication; loading or skipped ticks must not cause catch-up calls. */
    public Frame advance(){
        float pollSeconds=seconds;
        var polled=new ArrayList<Track>(ORDER.length);
        revision++;
        if(enabled){
            // Native poll(1) precedes finalization, including the final callback.
            for(Track track:ORDER)if(active[track.ordinal()])polled.add(track);
            for(Track track:polled){
                int i=track.ordinal();remaining[i]-=priorDelta[i];
                if(remaining[i]<0F){active[i]=false;finished[i]=true;}
            }
            // Native advance(2) activates after poll(1); poll(0) cannot run Hits.
            seconds+=STEP_SECONDS;
            boolean pending=false;
            for(Track track:ORDER){
                int i=track.ordinal();
                if(finished[i])continue;
                pending=true;
                if(seconds<track.begin())continue;
                if(active[i])priorDelta[i]=STEP_SECONDS;
                else {active[i]=true;priorDelta[i]=seconds-track.begin();}
            }
            enabled=pending;
        }
        currentFrame=new Frame(polled,pollSeconds,activeCount(),revision);
        return currentFrame;
    }

    /** The current post-advance clock. Receipts created during a callback should
     * use Frame.elapsedSeconds instead, to stay on the source poll clock. */
    public float seconds(){return seconds;}
    public int activeCount(){int count=0;for(boolean value:active)if(value)count++;return count;}
    public boolean hasActive(){return activeCount()!=0;}
    public boolean isCurrent(Frame frame){return frame!=null&&frame==currentFrame&&frame.revision()==revision;}

    /** The pending delta is saved explicitly: recomputing it from age on reload
     * changes the first/last poll and can repeat a previously consumed window. */
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",SAVE_VERSION);
        tag.putBoolean("Enabled",enabled);tag.putFloat("Seconds",seconds);
        for(Track track:ORDER){
            int i=track.ordinal();var state=new CompoundTag();
            state.putBoolean("Active",active[i]);state.putBoolean("Finished",finished[i]);
            state.putFloat("Remaining",remaining[i]);state.putFloat("PriorDelta",priorDelta[i]);
            tag.put(track.name(),state);
        }
        return tag;
    }

    /** Restore a complete snapshot without activating, polling or catching up.
     * Missing, partial or nonfinite data leaves the clock canceled. */
    public void load(CompoundTag tag){
        clear();
        if(tag==null||!tag.contains("Version",Tag.TAG_INT)||tag.getInt("Version")!=SAVE_VERSION
                ||!tag.contains("Enabled",Tag.TAG_BYTE)||!tag.contains("Seconds",Tag.TAG_FLOAT))return;
        float savedSeconds=tag.getFloat("Seconds");boolean savedEnabled=tag.getBoolean("Enabled");
        // At most two fixed steps separate the final source end from the
        // post-final-poll clock. Reject corrupt ages instead of replaying Hits
        // at an unrelated receipt time; this is snapshot validation, not a
        // substitute for the native remaining-duration comparisons below.
        if(!Float.isFinite(savedSeconds)||savedSeconds<0F
                ||savedSeconds>Track.R3.end()+2F*STEP_SECONDS)return;
        boolean[] loadedActive=new boolean[ORDER.length],loadedFinished=new boolean[ORDER.length];
        float[] loadedRemaining=new float[ORDER.length],loadedDelta=new float[ORDER.length];
        boolean pending=false;
        for(Track track:ORDER){
            if(!tag.contains(track.name(),Tag.TAG_COMPOUND))return;
            var state=tag.getCompound(track.name());int i=track.ordinal();
            if(!state.contains("Active",Tag.TAG_BYTE)||!state.contains("Finished",Tag.TAG_BYTE)
                    ||!state.contains("Remaining",Tag.TAG_FLOAT)||!state.contains("PriorDelta",Tag.TAG_FLOAT))return;
            boolean a=state.getBoolean("Active"),f=state.getBoolean("Finished");
            float r=state.getFloat("Remaining"),d=state.getFloat("PriorDelta");
            if(!Float.isFinite(r)||!Float.isFinite(d)||r< -STEP_SECONDS||r>track.duration()
                    ||d<0F||d>STEP_SECONDS||a&&(f||r<0F||savedSeconds<track.begin())
                    ||!a&&!f&&(savedSeconds>=track.begin()||r!=track.duration()||d!=0F)
                    ||!savedEnabled&&a)return;
            loadedActive[i]=a;loadedFinished[i]=f;loadedRemaining[i]=r;loadedDelta[i]=d;
            pending|=!f;
        }
        if(savedEnabled!=pending)return;
        seconds=savedSeconds;enabled=savedEnabled;
        System.arraycopy(loadedActive,0,active,0,ORDER.length);
        System.arraycopy(loadedFinished,0,finished,0,ORDER.length);
        System.arraycopy(loadedRemaining,0,remaining,0,ORDER.length);
        System.arraycopy(loadedDelta,0,priorDelta,0,ORDER.length);
    }
}
