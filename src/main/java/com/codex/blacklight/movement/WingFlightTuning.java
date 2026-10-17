package com.codex.blacklight.movement;

/** Nominal game acoustics: one block = one metre, twenty movement ticks/second.
 * This is a fixed gameplay reference, not a temperature/atmosphere simulation. */
public final class WingFlightTuning {
    public static final double SONIC_SPEED = 343.0 / 20.0;
    public record Tier(double boostSpeed, double acceleration, double braking) {}
    private static final Tier[] TIERS = {
            new Tier(1.25, .095, .13), new Tier(4, .14, .35),
            new Tier(10, .22, .8), new Tier(22, .32, 2)
    };
    public static Tier tier(int level) { return TIERS[Math.clamp(level, 0, 3)]; }
    /** Vanilla compares displacement since tick start against one tick of velocity.
     * Account for at most five coalesced packets without exempting flight from validation. */
    public static float packetAllowance(float vanilla,double predictedSpeed,int level,int packets) {
        if(level<=0||!Double.isFinite(predictedSpeed))return vanilla;
        int count=packets<1||packets>5?1:packets;
        var tuning=tier(level);double speed=Math.clamp(predictedSpeed,0,tuning.boostSpeed());
        double envelope=Math.min(tuning.boostSpeed()+.5,speed+tuning.acceleration()*count+.5)*count;
        return (float)Math.max(vanilla,(envelope*envelope-speed*speed)/count);
    }
    public static float mach(float speed) { return Float.isFinite(speed) ? (float)Math.max(0, speed / SONIC_SPEED) : 0; }
    /** Continuous streamline blend before the shock, so crossing Mach 1 never snaps the rig. */
    public static float streamline(float speed) {
        float t=Math.clamp((mach(speed)-.35F)/.65F,0,1);return t*t*(3-2*t);
    }
    /** One boom per crossing; hysteresis and cooldown prevent boundary chatter. */
    public static final class SonicGate {
        private boolean armed=true;private long last=Long.MIN_VALUE;
        public boolean update(long tick, double speed, boolean flying) {
            if(!flying){armed=true;return false;}
            if(speed<SONIC_SPEED*.85)armed=true;
            if(armed&&Double.isFinite(speed)&&speed>=SONIC_SPEED&&(last==Long.MIN_VALUE||tick-last>=40)){
                armed=false;last=tick;return true;
            }
            return false;
        }
    }
    private WingFlightTuning() {}
}
