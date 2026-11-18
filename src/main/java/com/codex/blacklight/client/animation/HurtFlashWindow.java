package com.codex.blacklight.client.animation;

/** A finite presentation budget that survives a world-clock correction.
 * Removed corpses have no entity ticks; absolute world deadlines can become
 * seconds long when an integrated server corrects the client clock backwards. */
public final class HurtFlashWindow {
    private long last;
    private int remaining;
    public HurtFlashWindow(long now,int ticks){last=now;remaining=Math.max(0,ticks);}
    public boolean active(long now){
        if(now>last)remaining-= (int)Math.min(remaining,now-last);
        last=now;
        return remaining>0;
    }
}
