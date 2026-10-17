package com.codex.blacklight.movement;

import java.util.function.DoublePredicate;

/** Pure stopping-distance limiter. The predicate examines only already loaded terrain. */
public final class WingFlightCorridor {
    /** Number of flight ticks kept ahead as a loading reservoir, beyond the hard stop check. */
    private static final double LOAD_RESERVE_TICKS=8;
    public static final class LoadBudget {
        private long lastTick=Long.MIN_VALUE;
        private double budget=Double.NaN;
        public double update(long tick,double readyDistance,boolean horizonReady,WingFlightTuning.Tier tier){
            double target=horizonReady?tier.boostSpeed():Math.min(tier.boostSpeed(),Math.max(0,readyDistance-2)/LOAD_RESERVE_TICKS);
            if(!Double.isFinite(budget)||lastTick==Long.MIN_VALUE||tick<lastTick||tick-lastTick>20)budget=target;
            else if(tick>lastTick){
                double elapsed=Math.min(4,tick-lastTick);
                // Chunk batches must not turn into a speed jump. Shed speed before the
                // hard stopping envelope, then recover more slowly as the runway refills.
                double rate=target<budget?tier.braking()*.35:tier.acceleration()*.5;
                budget+=Math.clamp(target-budget,-rate*elapsed,rate*elapsed);
            }
            lastTick=tick;
            return Math.clamp(budget,0,tier.boostSpeed());
        }
    }
    public static double adaptiveLimit(double speed,int level,long tick,LoadBudget budget,DoublePredicate loadedAtDistance){
        var tier=WingFlightTuning.tier(level);
        if(level<=0)return limit(speed,tier.braking(),loadedAtDistance);
        double cap=tier.boostSpeed();
        double horizon=Math.max(cap+cap*cap/(2*tier.braking())+2,cap*LOAD_RESERVE_TICKS+2);
        double clear=0;boolean ready=true;
        for(double at=0;at<horizon;at+=2){
            if(!loadedAtDistance.test(at)){ready=false;break;}
            clear=at;
        }
        if(ready){ready=loadedAtDistance.test(horizon);if(ready)clear=horizon;}
        double allowed=Math.min(speed,budget.update(tick,clear,ready,tier));
        // Always retain the hard collision/loading boundary, even if chunks unload
        // suddenly or the player turns toward a shorter corridor.
        return ready?allowed:safe(allowed,tier.braking(),clear);
    }
    public static double limit(double speed,double braking,DoublePredicate loadedAtDistance) {
        if(!Double.isFinite(speed)||speed<=0||!Double.isFinite(braking)||braking<=0)return 0;
        // Include one tick of movement before braking begins and two blocks of reserve.
        double horizon=speed+speed*speed/(2*braking)+2;
        double clear=0;
        for(double at=0;at<=horizon;at+=2){
            if(!loadedAtDistance.test(at))return safe(speed,braking,clear);
            clear=at;
        }
        if(!loadedAtDistance.test(horizon))return safe(speed,braking,clear);
        return speed;
    }
    private static double safe(double speed,double brake,double clear){
        double distance=Math.max(0,clear-2);
        return Math.min(speed,Math.sqrt(brake*brake+2*brake*distance)-brake);
    }
    private WingFlightCorridor(){}
}
