package com.codex.blacklight.infection;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world controls, separate from building infection and player passive skills. */
public final class TissueCompetitionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED,INHERITANCE;
    public static final ModConfigSpec.IntValue SEARCHES,SESSIONS,RADIUS;
    public static final ModConfigSpec.DoubleValue FUSION;
    static{
        var b=new ModConfigSpec.Builder();
        ENABLED=b.define("infectedCompetition",true);INHERITANCE=b.define("inheritOrgans",true);
        SEARCHES=b.comment("Maximum local prey searches per dimension each tick.").defineInRange("searchesPerTick",2,1,8);
        SESSIONS=b.defineInRange("simultaneousConsumptions",8,1,32);
        RADIUS=b.defineInRange("searchRadius",16,4,24);
        FUSION=b.defineInRange("fusionCoefficient",.35,0,1);SPEC=b.build();
    }
    public static TissueInheritance.Rules rules(){return new TissueInheritance.Rules(FUSION.get(),300,6,1000);}
    private TissueCompetitionConfig(){}
}
