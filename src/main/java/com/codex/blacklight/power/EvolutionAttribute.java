package com.codex.blacklight.power;

/** Repeatable EP investments; no authored maximum rank. */
public enum EvolutionAttribute {
    VITALITY("infinite_vitality"), STRENGTH("infinite_strength"), SKILL_POWER("infinite_skill_power"),
    RESILIENCE("infinite_resilience"), RECOVERY("infinite_recovery"), MOBILITY("infinite_mobility");
    public final String id;
    EvolutionAttribute(String id){this.id=id;}
    public static EvolutionAttribute byId(String id){for(var a:values())if(a.id.equals(id))return a;return null;}
    public static double attackMultiplier(long level){return 1+.02*Math.max(0,level);}
    public static double skillMultiplier(long level){return 1+.04*Math.max(0,level);}
    public static double defenseMultiplier(long level){return 1/(1+.025*Math.max(0,level));}
    public static double mobilityMultiplier(long level){return 1+.025*Math.log1p(Math.max(0,level));}
    public double effect(long level){long n=Math.max(0,level);return switch(this){
        case VITALITY->n*10D;case STRENGTH->n*2D;case SKILL_POWER->n*4D;
        case RESILIENCE->100*(1-defenseMultiplier(n));case RECOVERY->n*.25;case MOBILITY->100*(mobilityMultiplier(n)-1);
    };}
}
