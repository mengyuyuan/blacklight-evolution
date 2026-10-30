package com.codex.blacklight.city;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class CityConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPAWNS,SPREAD,DESTRUCTION,WARMUP;
    public static final ModConfigSpec.IntValue MOB_LIMIT,SPREAD_SECONDS,TILE_CACHE,PREGEN_INTERVAL,TRAVEL_TIMEOUT;
    public static final ModConfigSpec.IntValue POPULATION_INTERVAL,HIVE_BROOD_INTERVAL;
    public static final ModConfigSpec.DoubleValue HYDRA_CHANCE;
    static {var b=new ModConfigSpec.Builder();
        SPAWNS=b.comment("City residents, infected and military population. Server authoritative.").define("population",true);
        SPREAD=b.comment("Slow spread across generated city blocks only; sanctuary remains intact.").define("infectionSpread",true);
        DESTRUCTION=b.comment("Allow hive wounds to affect its generated facade. Does not replace player blocks.").define("hiveFacadeDamage",true);
        MOB_LIMIT=b.comment("城市非感染体（居民、军方及其他生物）的独立名额；感染体使用outbreak配置的infectedPerDimension，合计为城市调度硬上限。0仍关闭城市全部人口生成，不删除已有生物。").defineInRange("cityMobLimit",48,0,200);
        SPREAD_SECONDS=b.defineInRange("spreadIntervalSeconds",180,30,3600);
        TILE_CACHE=b.comment("Decoded map tile cache. About 129 KiB per tile; transient worldgen buffers are additional.").defineInRange("mapCacheTiles",64,8,256);
        WARMUP=b.comment("Prepare 7x7 arrival chunks in the background once per Futian world. Can be paused with /blacklightcity pregen pause.").define("arrivalWarmup",true);
        PREGEN_INTERVAL=b.comment("Minimum ticks between background chunk requests. Only one request is active; busy servers defer new work.").defineInRange("pregenIntervalTicks",10,1,200);
        TRAVEL_TIMEOUT=b.comment("Maximum active waiting time for asynchronous city arrival, in seconds. Large cold municipal scenes need more than 30 seconds on slower storage. Movement or logout still cancels travel.").defineInRange("travelTimeoutSeconds",120,30,600);
        POPULATION_INTERVAL=b.comment("城市居民和环境人口尝试间隔；按20刻向上取整，以匹配人口更新节拍。").defineInRange("populationIntervalTicks",100,20,240000);
        HIVE_BROOD_INTERVAL=b.comment("已有城市母巢的繁殖等待刻数；需实际供能、区域阶段和人口预算。普通建筑菌落使用gameplay配置中的broodIntervalTicks。").defineInRange("hiveBroodIntervalTicks",200,20,240000);
        HYDRA_CHANCE=b.comment("城市母巢抽到古老谱系、已有猎手且阶段允许时，改生九头蛇的概率。").defineInRange("hiveHydraChance",1D/12,0,1);
        SPEC=b.build();
    }
    public static int populationIntervalTicks(){return ((POPULATION_INTERVAL.get()+19)/20)*20;}
}
