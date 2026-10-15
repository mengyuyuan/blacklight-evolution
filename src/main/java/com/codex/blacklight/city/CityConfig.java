package com.codex.blacklight.city;
import net.minecraftforge.common.ForgeConfigSpec;
public final class CityConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SPAWNS,SPREAD,DESTRUCTION;
    public static final ForgeConfigSpec.IntValue MOB_LIMIT,SPREAD_SECONDS;
    static {var b=new ForgeConfigSpec.Builder();
        SPAWNS=b.comment("City residents, infected and military population. Server authoritative.").define("population",true);
        SPREAD=b.comment("Slow spread across generated city blocks only; sanctuary remains intact.").define("infectionSpread",true);
        DESTRUCTION=b.comment("Allow hive wounds to affect its generated facade. Does not replace player blocks.").define("hiveFacadeDamage",true);
        MOB_LIMIT=b.defineInRange("cityMobLimit",80,0,200);
        SPREAD_SECONDS=b.defineInRange("spreadIntervalSeconds",180,30,3600);SPEC=b.build();
    }
}
