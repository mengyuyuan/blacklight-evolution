package com.codex.blacklight.infection;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OutbreakConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue DAY_TICKS,INTERVAL,LOCAL_CAP,DIMENSION_CAP,BATCH,COMMON_RESERVE;
    public static final ModConfigSpec.DoubleValue COMMON_HEAL;
    static{
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("World infection starts when a player releases a virus vial. Saved world clock; loaded chunks only.").define("enabled",true);
        DAY_TICKS=b.defineInRange("progressionDayTicks",24000,200,24000000);
        INTERVAL=b.defineInRange("spawnIntervalTicks",60,20,24000);
        LOCAL_CAP=b.defineInRange("infectedWithin96Blocks",18,0,128);
        DIMENSION_CAP=b.defineInRange("infectedPerDimension",128,0,1024);
        BATCH=b.comment("Maximum successful births per dimension per interval, regardless of city count.").defineInRange("birthsPerBatch",4,1,16);
        COMMON_RESERVE=b.comment("Reserve these local population slots for ordinary consumable infected.").defineInRange("commonReserve",6,0,64);
        COMMON_HEAL=b.comment("Minimum fraction of current maximum health restored by consuming a common infected, before assimilation upgrades.").defineInRange("commonConsumeHealFraction",.10,0,.5);
        SPEC=b.build();
    }
    private OutbreakConfig(){}
}
