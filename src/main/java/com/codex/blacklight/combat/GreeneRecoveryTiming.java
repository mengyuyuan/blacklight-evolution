package com.codex.blacklight.combat;

/** Original collapse TimerSet, measured from collapse entry (not DOWN entry).
 * Native TimerSet stores time at +0x1c and randomRange at +0x18. Its RNG forms
 * a float in [2,4), subtracts 3, multiplies by range, then adds time.
 * MC supplies the 23 random bits and rounds the deadline up to a server tick. */
public final class GreeneRecoveryTiming {
    public static final float TIMER_SECONDS=26.66670036315918F,RANDOM_RANGE=1;
    public static final int RANDOM_BITS=1<<23;
    public static int recoveryStartTicks(int mantissa){
        if(mantissa<0||mantissa>=RANDOM_BITS)throw new IllegalArgumentException("23-bit timer sample required");
        float noise=Float.intBitsToFloat(0x40000000|mantissa)-3F;
        float seconds=TIMER_SECONDS+noise*RANDOM_RANGE;
        return (int)Math.ceil((double)seconds*20);
    }
    public static int downTicks(int mantissa){return recoveryStartTicks(mantissa)-GreeneMotion.COLLAPSE_TICKS;}
    private GreeneRecoveryTiming(){}
}
