package com.codex.blacklight.infection;

import net.minecraftforge.common.ForgeConfigSpec;

public final class OutbreakConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue DAY_TICKS,INTERVAL,LOCAL_CAP,DIMENSION_CAP,BATCH,COMMON_RESERVE;
    public static final ForgeConfigSpec.DoubleValue COMMON_HEAL,LAIR_CHANCE_PER_DAY,LAIR_MAX_CHANCE;
    public static final ForgeConfigSpec.IntValue LAIR_SPACING;
    public static final ForgeConfigSpec.IntValue PLAYER_CROWD_CAP,CHUNK_CAP,TRIM_DISTANCE;
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
        LAIR_CHANCE_PER_DAY=b.comment("Lairs: chance per newly seen chunk after release, multiplied by (1 + outbreak days).").defineInRange("lairChancePerDay",.003,0,1);
        LAIR_MAX_CHANCE=b.comment("Lairs: maximum chance per newly seen chunk.").defineInRange("lairMaxChance",.015,0,1);
        LAIR_SPACING=b.comment("Lairs: minimum horizontal distance between two Lairs in one dimension.").defineInRange("lairSpacing",160,32,4096);
        PLAYER_CROWD_CAP=b.comment("Performance: no natural infected birth while this many infected are within 48 blocks of a nearby player. 0 disables.").defineInRange("infectedNearPlayer",24,0,256);
        CHUNK_CAP=b.comment("Performance: no natural infected birth into a chunk already holding this many infected. 0 disables.").defineInRange("infectedPerChunk",6,0,128);
        TRIM_DISTANCE=b.comment("Performance: idle, unnamed natural infected farther than this from every player are removed slowly (16 per 10 s). Hosts, bosses, named and converted mobs are kept. 0 disables.").defineInRange("trimAmbientBeyondBlocks",96,0,512);
        SPEC=b.build();
    }
    private OutbreakConfig(){}
}
