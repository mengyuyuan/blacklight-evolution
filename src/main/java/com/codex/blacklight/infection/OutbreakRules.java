package com.codex.blacklight.infection;

/** In-game elapsed time since release, independent of daylight commands. */
public final class OutbreakRules {
    public enum Tier { FRESH, GROWING, MATURE, HUNTER, ANCIENT }
    private OutbreakRules(){}
    /** Floors give developing lineages room before ordinary weighted births
     * fill the remaining slots. Existing living mobs are never culled. */
    public static Tier populationTier(int stage,int common,int developed,int mature,int ancient,int reserve,int cap,int roll){
        int available=Math.max(0,cap-Math.min(Math.max(0,reserve),cap));
        if(common<Math.min(Math.max(0,reserve),cap))return Tier.FRESH;
        if(stage>=3&&ancient<Math.min(2,available))return Tier.ANCIENT;
        if(stage>=2&&mature<Math.min(2,available))return Tier.MATURE;
        if(stage>=1&&developed<Math.min(stage+1,available))return Tier.GROWING;
        return choose(stage,roll);
    }
    public static int stage(long elapsed,int dayTicks){
        long days=Math.max(0,elapsed)/Math.max(1,dayTicks);
        return days>=14?4:days>=7?3:days>=3?2:days>=1?1:0;
    }
    public static Tier choose(int stage,int roll){
        int value=Math.floorMod(roll,100);
        return switch(Math.clamp(stage,0,4)){
            case 0 -> Tier.FRESH;
            case 1 -> value<80?Tier.FRESH:Tier.GROWING;
            case 2 -> value<70?Tier.FRESH:value<90?Tier.GROWING:value<98?Tier.MATURE:Tier.HUNTER;
            case 3 -> value<65?Tier.FRESH:value<85?Tier.GROWING:value<95?Tier.MATURE:value<99?Tier.HUNTER:Tier.ANCIENT;
            default -> value<60?Tier.FRESH:value<80?Tier.GROWING:value<92?Tier.MATURE:value<98?Tier.HUNTER:Tier.ANCIENT;
        };
    }
    public static long tissueAge(Tier tier){return switch(tier){case FRESH,HUNTER->0;case GROWING->6000;case MATURE->TissueGrowth.MATURITY_TICKS;case ANCIENT->TissueGrowth.ANCIENT_TICKS;};}
}
