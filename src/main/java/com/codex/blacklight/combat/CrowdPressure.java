package com.codex.blacklight.combat;

/** Repeated knockback against one player inside a short window shrinks to nothing, so a
 * crowd can still hurt the player but cannot juggle them in place. */
public final class CrowdPressure {
    public static final int WINDOW_TICKS=20;
    private long first=Long.MIN_VALUE;private int count;
    /** Multiplier for the knockback received at {@code now}. */
    public float accept(long now,boolean attacking){
        if(first==Long.MIN_VALUE||now-first>=WINDOW_TICKS){first=now;count=0;}
        count++;
        float scale=count==1?1F:count==2?.5F:0F;
        // A committed attack keeps its footing like super armour, but still feels the first hit.
        return attacking?scale*.35F:scale;
    }
}