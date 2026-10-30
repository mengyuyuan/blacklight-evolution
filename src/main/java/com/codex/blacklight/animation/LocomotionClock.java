package com.codex.blacklight.animation;

/** One committed gait interval per game tick; render queries only interpolate it. */
public final class LocomotionClock {
    private String key;
    private float duration;
    private boolean looping;
    private long tick=Long.MIN_VALUE;
    private double previous,current;
    public String key(){return key;}
    public long tick(){return tick;}
    public boolean looping(){return looping;}
    public float duration(){return duration;}
    public double current(){return current;}
    public boolean commit(long nextTick,String nextKey,float nextDuration,boolean nextLoop,
                          boolean preserveCycle,double distance,double nominalBlocksPerTick) {
        return commit(nextTick,nextKey,nextDuration,nextLoop,preserveCycle,distance,nominalBlocksPerTick,8);
    }
    public boolean commit(long nextTick,String nextKey,float nextDuration,boolean nextLoop,
                          boolean preserveCycle,double distance,double nominalBlocksPerTick,double maxRate) {
        if(!Double.isFinite(maxRate)||maxRate<=0)throw new IllegalArgumentException("Invalid gait rate limit");
        if(nextTick<=tick)return false;
        if(nextKey==null||nextDuration<=0||!Float.isFinite(nextDuration)
                ||!Double.isFinite(distance)||distance<0||!Double.isFinite(nominalBlocksPerTick)||nominalBlocksPerTick<0)
            throw new IllegalArgumentException("Invalid committed gait interval");
        boolean fresh=key==null,changed=!nextKey.equals(key);
        double ticks=fresh?0:nextTick-tick;
        if(changed){
            current=!fresh&&preserveCycle&&looping&&nextLoop
                    ?(current%duration)/duration*nextDuration:0;
            key=nextKey;duration=nextDuration;looping=nextLoop;
        }
        previous=current;
        double advance=looping&&nominalBlocksPerTick>0?distance/(nominalBlocksPerTick*20):ticks/20;
        // The three extracted sprint clips already shorten their cycles (16/14/12
        // source frame intervals at 30 Hz). Multiplying them by up to eight again
        // turns the limbs into a strobe. Keep their authored fast cadence while
        // still allowing distance-driven slowing/stopping and phase-continuous swaps.
        boolean sourceSprint=nextKey.equals("basic_sprint") || nextKey.equals("basic_sprint_fast")
                || nextKey.equals("basic_sprint_fastest");
        if(!fresh)current+=Math.min(advance,ticks/20*(sourceSprint?Math.min(1,maxRate):maxRate));
        tick=nextTick;
        return changed;
    }
    public double seconds(float partial) {
        double alpha=com.codex.blacklight.util.MathCompat.clamp(partial,0,1);
        return previous+(current-previous)*alpha;
    }
    public float sampleTime(float partial) {
        double seconds=seconds(partial);
        return looping?(float)(seconds%duration):(float)Math.min(seconds,duration);
    }
}
