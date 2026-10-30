package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;
import static com.codex.blacklight.config.GameplayBalanceConfig.value;

/** In-game elapsed time since release, independent of daylight commands. */
public final class OutbreakRules {
    public enum Tier { FRESH, GROWING, MATURE, HUNTER, ANCIENT }
    private OutbreakRules(){}
    /** Floors give developing lineages room before ordinary weighted births
     * fill the remaining slots. Existing living mobs are never culled. */
    public static Tier populationTier(int stage,int common,int developed,int mature,int ancient,int reserve,int cap,int roll){
        return populationTier(stage,common,developed,mature,ancient,reserve,cap,Math.floorMod(roll,100)/100D);
    }
    public static Tier populationTier(int stage,int common,int developed,int mature,int ancient,int reserve,int cap,double roll){
        if(!value(GameplayBalanceConfig.LINEAGE_GUARANTEES))return choose(stage,roll);
        int available=Math.max(0,cap-Math.min(Math.max(0,reserve),cap));
        if(common<Math.min(Math.max(0,reserve),cap))return Tier.FRESH;
        if(stage>=3&&ancient<Math.min(2,available))return Tier.ANCIENT;
        if(stage>=2&&mature<Math.min(2,available))return Tier.MATURE;
        if(developed<Math.min(Math.max(2,stage+1),available))return Tier.GROWING;
        // The early pool also needs actual non-zombie lineages, without skipping to mature elites.
        return stage<=0?roll<1-value(GameplayBalanceConfig.EARLY_GROWING)?Tier.FRESH:Tier.GROWING:choose(stage,roll);
    }
    public static int lineageFloor(int stage,int reserve,int cap){return Math.min(Math.max(0,cap-Math.min(Math.max(0,reserve),cap)),Math.max(2,Math.clamp(stage,0,4)+1));}
    public static Tier ambientTier(int stage,int common,int developed,int mature,int ancient,int reserve,int cap,int roll){
        return ambientTier(stage,common,developed,mature,ancient,reserve,cap,Math.floorMod(roll,100)/100D);
    }
    public static Tier ambientTier(int stage,int common,int developed,int mature,int ancient,int reserve,int cap,double roll){
        // Continuous player clearing must not turn the early replenishment pool into 100% zombies.
        if(value(GameplayBalanceConfig.LINEAGE_GUARANTEES)&&stage<=0&&developed<lineageFloor(stage,reserve,cap)&&roll>=1-value(GameplayBalanceConfig.EARLY_PIONEER))return Tier.GROWING;
        return populationTier(stage,common,developed,mature,ancient,reserve,cap,roll);
    }
    public static int stage(long elapsed,int dayTicks){
        long days=Math.max(0,elapsed)/Math.max(1,dayTicks);
        return days>=GameplayBalanceConfig.stageDay(3)?4:days>=GameplayBalanceConfig.stageDay(2)?3:days>=GameplayBalanceConfig.stageDay(1)?2:days>=GameplayBalanceConfig.stageDay(0)?1:0;
    }
    public static Tier choose(int stage,int roll){
        return choose(stage,Math.floorMod(roll,100)/100D);
    }
    public static Tier choose(int stage,double roll){return weighted(GameplayBalanceConfig.weights(stage),roll);}
    public static Tier weighted(int[] weights,double roll){
        long total=0;for(int w:weights)total+=Math.max(0,w);if(total==0)return Tier.FRESH;
        double target=(Double.isFinite(roll)?Math.clamp(roll,0,Math.nextDown(1D)):0)*total;
        for(int i=0;i<Math.min(5,weights.length);i++){target-=Math.max(0,weights[i]);if(target<0)return Tier.values()[i];}
        return Tier.FRESH;
    }
    public static long tissueAge(Tier tier){return switch(tier){case FRESH,HUNTER->0;case GROWING->6000;case MATURE->TissueGrowth.MATURITY_TICKS;case ANCIENT->TissueGrowth.ANCIENT_TICKS;};}
}
