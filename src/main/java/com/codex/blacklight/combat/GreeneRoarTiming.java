package com.codex.blacklight.combat;

/** startup_fig 267084/267516 and the concurrent distance bank 272948.
 * Distances here are original units; the caller owns the MC world conversion.
 * Native variable-dt timers become observed 20 Hz ticks, without unload catch-up.
 * Cinematic ForceThunderousRoar is deliberately not consumed as a one-shot flag. */
public final class GreeneRoarTiming {
    public static final double CLOSE_DISTANCE=36, TRIGGER_DISTANCE=35;
    public static final int DWELL_TICKS=120, RECHARGE_TICKS=300;
    public record Snapshot(int closeTicks,int rechargeTicks,boolean close,boolean forbidden) {}
    private int closeTicks,rechargeTicks;
    private boolean close,forbidden;
    private long lastTick=Long.MIN_VALUE;

    public void observe(long tick,boolean engaged,double distance){
        if(tick==lastTick)return;
        lastTick=tick;
        if(rechargeTicks>0)rechargeTicks--;
        boolean next=engaged&&Double.isFinite(distance)&&distance>=0&&distance<=CLOSE_DISTANCE;
        // Opportunity 10a8ea4b..51 does not re-enter the current selected node.
        // TimerSet therefore runs on near/far entry, not every nearby update.
        closeTicks=next?(close?Math.min(DWELL_TICKS,closeTicks+1):0):0;
        close=next;
    }
    public boolean ready(double distance,boolean mechanicalTarget,boolean devastator){
        return !mechanicalTarget&&!devastator&&!forbidden&&close&&closeTicks>=DWELL_TICKS
            &&rechargeTicks==0&&Double.isFinite(distance)&&distance>=0&&distance<TRIGGER_DISTANCE;
    }
    public boolean recharged(){return rechargeTicks==0;}
    public void started(){closeTicks=0;rechargeTicks=RECHARGE_TICKS;}
    public void forbidden(boolean value){forbidden=value;}
    public Snapshot snapshot(){return new Snapshot(closeTicks,rechargeTicks,close,forbidden);}
    public void restore(Snapshot saved){
        close=saved.close();forbidden=saved.forbidden();
        closeTicks=close?Math.max(0,Math.min(DWELL_TICKS,saved.closeTicks())):0;
        rechargeTicks=Math.max(0,Math.min(RECHARGE_TICKS,saved.rechargeTicks()));
        // Retain this instance's last observation across same-object load. A
        // newly constructed instance already starts at MIN_VALUE; reloading in
        // a tick it has observed must not count that tick for a second time.
    }
}
