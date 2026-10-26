package com.codex.blacklight.power;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Same fixed price at every rank; synchronized server configuration. */
public final class PlayerEvolutionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue COST;
    public static final ModConfigSpec.IntValue PASSIVE_LEVEL,PERCENT_LEVEL;
    public static final ModConfigSpec.BooleanValue KEEP_LEGACY_PASSIVES;
    static {
        var b=new ModConfigSpec.Builder();
        COST=b.comment("Fixed EP cost for every repeatable attribute purchase, independent of rank.")
                .defineInRange("repeatableAttributeCost",200,1,1000000);
        PASSIVE_LEVEL=b.comment("Test baseline: every core repeatable attribute must reach this rank before learning Adaptation and Tissue Regeneration.")
                .defineInRange("passiveAttributeRank",20,1,1000000);
        PERCENT_LEVEL=b.comment("Test baseline: per-attribute rank for percentage Adaptation; never below the basic passive threshold.")
                .defineInRange("percentAdaptationAttributeRank",50,1,1000000);
        KEEP_LEGACY_PASSIVES=b.comment("Keep old learned passives active without new qualifications. False preserves learning/stacks but suspends effects until qualified.")
                .define("keepLegacyPassivesActive",false);
        SPEC=b.build();
    }
    private PlayerEvolutionConfig(){}
}
