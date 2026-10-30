package com.codex.blacklight.city;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class CityConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPAWNS,SPREAD,DESTRUCTION,WARMUP;
    public static final ModConfigSpec.IntValue MOB_LIMIT,MILITARY_GARRISON_LIMIT,SPREAD_SECONDS,TILE_CACHE,PREGEN_INTERVAL,TRAVEL_TIMEOUT;
    static {var b=new ModConfigSpec.Builder();
        SPAWNS=b.comment("City residents, infected and military population. Server authoritative.").define("population",true);
        SPREAD=b.comment("Slow spread across generated city blocks only; sanctuary remains intact.").define("infectionSpread",true);
        DESTRUCTION=b.comment("Allow hive wounds to affect its generated facade. Does not replace player blocks.").define("hiveFacadeDamage",true);
        MOB_LIMIT=b.comment("City population spawning budget (counts loaded mobs). Military base issuance has an independent militaryGarrisonLimit.").defineInRange("cityMobLimit",80,0,200);
        MILITARY_GARRISON_LIMIT=b.comment("Maximum alive, loaded soldiers referenced by saved military-base slots, per dimension. Applies to natural and administrator-placed bases in every dimension; unrelated mobs and non-base soldiers do not consume it. Zero pauses new issuance without replacing or clearing saved slots.").defineInRange("militaryGarrisonLimit",80,0,200);
        SPREAD_SECONDS=b.defineInRange("spreadIntervalSeconds",180,30,3600);
        TILE_CACHE=b.comment("Decoded map tile cache. About 129 KiB per tile; transient worldgen buffers are additional.").defineInRange("mapCacheTiles",64,8,256);
        WARMUP=b.comment("Prepare 7x7 arrival chunks in the background once per Futian world. Can be paused with /blacklightcity pregen pause.").define("arrivalWarmup",true);
        PREGEN_INTERVAL=b.comment("Minimum ticks between background chunk requests. Only one request is active; busy servers defer new work.").defineInRange("pregenIntervalTicks",10,1,200);
        TRAVEL_TIMEOUT=b.comment("Maximum active waiting time for asynchronous city arrival, in seconds. Large cold municipal scenes need more than 30 seconds on slower storage. Movement or logout still cancels travel.").defineInRange("travelTimeoutSeconds",120,30,600);
        SPEC=b.build();
    }
}
