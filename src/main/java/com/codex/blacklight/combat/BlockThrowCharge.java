package com.codex.blacklight.combat;

/** Server tick clock. Repeat START packets renew a lease without restarting charge. */
public final class BlockThrowCharge {
    private long started=-1,lastInput=-1;
    public void start(long now){if(started<0)started=now;lastInput=now;}
    public boolean active(){return started>=0;}
    public void cancel(){started=-1;lastInput=-1;}
    public void tick(long now){if(started>=0&&(now<started||now-lastInput>25))cancel();}
    public int ticks(long now){return started<0?0:(int)Math.min(BlockThrowTuning.MAX_CHARGE_TICKS,Math.max(0,now-started));}
    public int release(long now){tick(now);if(started<0)return -1;int result=ticks(now);cancel();return result;}
}
