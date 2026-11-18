package com.codex.blacklight.military.aircraft;

/** P1 engage_apache chin-gun controller: 4 s burst (97344), 4 s wait (96912).
 * Minecraft arms this window on its first accepted shot; the existing weapon profile
 * still determines round cadence. This is not the independent Hellfire controller. */
public final class HelicopterGunCycle {
    private static final int WINDOW=80,REST=80;
    private int burstTicks,restTicks;
    public void tick(){
        if(burstTicks>0){if(--burstTicks==0)restTicks=REST;}
        else if(restTicks>0)restTicks--;
    }
    public boolean ready(){return restTicks==0;}
    public void shot(){if(ready()&&burstTicks==0)burstTicks=WINDOW;}
    public int burstTicks(){return burstTicks;}
    public int restTicks(){return restTicks;}
    public void reset(){burstTicks=restTicks=0;}
    public void restore(int burst,int rest){
        restTicks=Math.max(0,Math.min(REST,rest));
        burstTicks=restTicks>0?0:Math.max(0,Math.min(WINDOW,burst));
    }
}
