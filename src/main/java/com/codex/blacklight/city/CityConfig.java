package com.codex.blacklight.city;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class CityConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPAWNS,SPREAD,DESTRUCTION,WARMUP;
    public static final ModConfigSpec.IntValue MOB_LIMIT,SPREAD_SECONDS,TILE_CACHE,PREGEN_INTERVAL;
    static {var b=new ModConfigSpec.Builder();
        SPAWNS=b.comment("City residents, infected and military population. Server authoritative.").define("population",true);
        SPREAD=b.comment("Slow spread across generated city blocks only; sanctuary remains intact.").define("infectionSpread",true);
        DESTRUCTION=b.comment("Allow hive wounds to affect its generated facade. Does not replace player blocks.").define("hiveFacadeDamage",true);
        MOB_LIMIT=b.defineInRange("cityMobLimit",80,0,200);
        SPREAD_SECONDS=b.defineInRange("spreadIntervalSeconds",180,30,3600);
        TILE_CACHE=b.comment("Decoded map tile cache. About 129 KiB per tile; transient worldgen buffers are additional.").defineInRange("mapCacheTiles",64,8,256);
        WARMUP=b.comment("Prepare 7x7 arrival chunks in the background once per Futian world. Can be paused with /blacklightcity pregen pause.").define("arrivalWarmup",true);
        PREGEN_INTERVAL=b.comment("Minimum ticks between background chunk requests. Only one request is active; busy servers defer new work.").defineInRange("pregenIntervalTicks",10,1,200);
        SPEC=b.build();
    }
}
