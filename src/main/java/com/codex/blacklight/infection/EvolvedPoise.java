package com.codex.blacklight.infection;

/** Damage remains effective; a finite flinch is followed by time to act. */
public final class EvolvedPoise {
    public static final int LIGHT_TICKS=8,HEAVY_TICKS=16,MAX_CONTROL_TICKS=24,FREE_ACTION_TICKS=40;
    private long nextControl=Long.MIN_VALUE;
    public boolean accept(long now){
        if(protectedAt(now))return false;
        nextControl=now+MAX_CONTROL_TICKS+FREE_ACTION_TICKS;return true;
    }
    public boolean protectedAt(long now){return now<nextControl;}
}
