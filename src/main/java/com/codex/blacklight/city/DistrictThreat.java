package com.codex.blacklight.city;

import com.codex.blacklight.config.GameplayBalanceConfig;
import static com.codex.blacklight.config.GameplayBalanceConfig.value;

/** Local infection pressure, separate from global eligibility and daylight time. */
public final class DistrictThreat {
    public static final int MAXIMUM=10000, YELLOW=2000, RED=6000;
    public enum Zone { GREEN, YELLOW, RED }
    public record Signal(int infected,int mature,int evolved,int colonies,int redNeighbours,int yellowNeighbours) {
        public static final Signal EMPTY=new Signal(0,0,0,0,0,0);
        public int rate(){
            int local=infected>0?Math.min(12,2+Math.max(0,infected)/2):0;
            local+=Math.min(3,Math.max(0,mature))*2+Math.min(2,Math.max(0,evolved))*5+Math.min(20,Math.min(4,Math.max(0,colonies))*6);
            int incoming=Math.min(10,Math.min(4,Math.max(0,redNeighbours))*3+Math.min(4,Math.max(0,yellowNeighbours)));
            return local+incoming-(local==0?(incoming>0?1:6):0);
        }
    }
    private DistrictThreat(){}
    public static int seed(int stage){return stage>=2?6500:stage==1?2400:0;}
    public static long advance(long work,int ticks,Signal signal){
        long delta=(long)Math.clamp(ticks,0,24000)*signal.rate();
        return Math.clamp(Math.clamp(work,0,MAXIMUM*20L)+delta,0,MAXIMUM*20L);
    }
    /** Hysteresis prevents the HUD and population rules oscillating at a boundary. */
    public static Zone zone(Zone previous,int pressure){
        pressure=Math.clamp(pressure,0,MAXIMUM);
        if(previous==Zone.RED&&pressure>=RED-600)return Zone.RED;
        if(pressure>=RED)return Zone.RED;
        if(previous!=Zone.GREEN&&pressure>=YELLOW-400)return Zone.YELLOW;
        return pressure>=YELLOW?Zone.YELLOW:Zone.GREEN;
    }
    public static int populationCap(Zone zone,int configured){return Math.min(Math.max(0,configured),zone==Zone.GREEN?value(GameplayBalanceConfig.GREEN_CAP):zone==Zone.YELLOW?value(GameplayBalanceConfig.YELLOW_CAP):Math.max(0,configured));}
    public static int ambientChance(Zone zone){return value(zone==Zone.GREEN?GameplayBalanceConfig.GREEN_CHANCE:zone==Zone.YELLOW?GameplayBalanceConfig.YELLOW_CHANCE:GameplayBalanceConfig.RED_CHANCE);}
    public static int stage(Zone zone,int global,int local){return Math.min(Math.clamp(global,0,4),zone==Zone.GREEN?0:zone==Zone.YELLOW?1:Math.max(2,Math.clamp(local,0,4)));}
}
