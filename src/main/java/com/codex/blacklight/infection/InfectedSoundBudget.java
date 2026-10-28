package com.codex.blacklight.infection;

/** Per audible neighbourhood: six voices per tick, 32 per second. No queues or delayed hits. */
public final class InfectedSoundBudget {
    private long tick=Long.MIN_VALUE,window=Long.MIN_VALUE;
    private int immediate,total;
    public boolean accept(long now){
        if(tick!=now){tick=now;immediate=0;}
        if(window==Long.MIN_VALUE||now<window||now-window>=20){window=now;total=0;}
        if(immediate>=6||total>=32)return false;
        immediate++;total++;return true;
    }
}
