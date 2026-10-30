package com.codex.blacklight.infection;

import net.minecraftforge.common.ForgeConfigSpec;

public final class OutbreakConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue DAY_TICKS,INTERVAL,LOCAL_CAP,DIMENSION_CAP,BATCH,COMMON_RESERVE;
    public static final ForgeConfigSpec.DoubleValue COMMON_HEAL;
    static{
        var b=new ForgeConfigSpec.Builder();
        ENABLED=b.comment("World infection starts when a player releases a virus vial. Saved world clock; loaded chunks only.").define("enabled",true);
        DAY_TICKS=b.defineInRange("progressionDayTicks",24000,200,24000000);
        INTERVAL=b.defineInRange("spawnIntervalTicks",40,20,24000);
        LOCAL_CAP=b.defineInRange("infectedWithin96Blocks",32,0,128);
        DIMENSION_CAP=b.defineInRange("infectedPerDimension",128,0,1024);
        BATCH=b.comment("Maximum successful births per dimension per interval, regardless of city count.").defineInRange("birthsPerBatch",8,1,16);
        COMMON_RESERVE=b.comment("Reserve these local population slots for ordinary consumable infected.").defineInRange("commonReserve",6,0,64);
        COMMON_HEAL=b.comment("Minimum fraction of current maximum health restored by consuming a common infected, before assimilation upgrades.").defineInRange("commonConsumeHealFraction",.10,0,.5);
        SPEC=b.build();
    }
    private OutbreakConfig(){}
}
