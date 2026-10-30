package com.codex.blacklight.power;

/** Repeatable EP investments; no authored maximum rank. */
public enum EvolutionAttribute {
    VITALITY("infinite_vitality"), STRENGTH("infinite_strength"), SKILL_POWER("infinite_skill_power"),
    RESILIENCE("infinite_resilience"), RECOVERY("infinite_recovery"), MOBILITY("infinite_mobility");
    public final String id;
    EvolutionAttribute(String id){this.id=id;}
    public static EvolutionAttribute byId(String id){for(var a:values())if(a.id.equals(id))return a;return null;}
    public static double attackMultiplier(long level){return 1+PlayerEvolutionConfig.value(PlayerEvolutionConfig.ATTACK_PER_LEVEL)*Math.max(0,level);}
    public static double skillMultiplier(long level){return 1+PlayerEvolutionConfig.value(PlayerEvolutionConfig.SKILL_PER_LEVEL)*Math.max(0,level);}
    public static double defenseMultiplier(long level){return 1/(1+PlayerEvolutionConfig.value(PlayerEvolutionConfig.DEFENSE_PER_LEVEL)*Math.max(0,level));}
    public static double mobilityMultiplier(long level){return 1+PlayerEvolutionConfig.value(PlayerEvolutionConfig.MOBILITY_PER_LEVEL)*Math.log1p(Math.max(0,level));}
    public double effect(long level){long n=Math.max(0,level);return switch(this){
        case VITALITY->n*PlayerEvolutionConfig.value(PlayerEvolutionConfig.HEALTH_PER_LEVEL);case STRENGTH->n*(100*PlayerEvolutionConfig.value(PlayerEvolutionConfig.ATTACK_PER_LEVEL));case SKILL_POWER->n*(100*PlayerEvolutionConfig.value(PlayerEvolutionConfig.SKILL_PER_LEVEL));
        case RESILIENCE->100*(1-defenseMultiplier(n));case RECOVERY->n*PlayerEvolutionConfig.value(PlayerEvolutionConfig.RECOVERY_PER_LEVEL);case MOBILITY->100*(mobilityMultiplier(n)-1);
    };}
}
