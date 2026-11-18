package com.codex.blacklight.military;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** P1 rifle/saw/sniper firing posture bank, startup_fig315748..316336.
 * This clock does not own ammunition, fire permits or Minecraft collision. */
public final class MilitaryRiflePosture {
    public enum Stance { STANDING, CROUCHING }
    // Source crouch/stand transition uses11 frames at30fps; rounded up to20Hz.
    public static final int TRANSITION_TICKS=8;
    private Stance stance;
    private long started,until;
    public Stance stance(){return stance;}
    public long started(){return started;}
    public long until(){return until;}
    public boolean active(long now){return stance!=null&&now>=started&&now<until;}
    public void clear(){stance=null;started=until=0;}

    /** Random weights3:1 belong to the outer bank. Low LoF is checked inside
     * the selected crouch branch; its explicit fallback executes standing. */
    public Stance choose(long now,BooleanSupplier awayFromEdge,BooleanSupplier lowLine,DoubleSupplier random){
        if(active(now))return stance;
        float total=3+(awayFromEdge.getAsBoolean()?1:0);
        float draw=(float)(sample(random)*total);
        Stance selected=Stance.STANDING;
        if(draw>3){
            // Preserve the bank's second condition pass, not a cached boolean.
            if(!awayFromEdge.getAsBoolean()){clear();return null;}
            if(lowLine.getAsBoolean())selected=Stance.CROUCHING;
        }
        enter(selected,now,random);return stance;
    }
    public void stand(long now,DoubleSupplier random){enter(Stance.STANDING,now,random);}
    private void enter(Stance next,long now,DoubleSupplier random){
        // Source continuous3..4/4..6-second waits are quantized to whole ticks.
        int minimum=next==Stance.CROUCHING?80:60,span=next==Stance.CROUCHING?40:20;
        stance=next;started=now;until=now+minimum+(int)Math.floor(sample(random)*(span+1));
    }
    private static double sample(DoubleSupplier random){
        double value=random.getAsDouble();
        if(!Double.isFinite(value)||value<0||value>=1)throw new IllegalArgumentException("Posture sample outside [0,1)");
        return value;
    }
}
