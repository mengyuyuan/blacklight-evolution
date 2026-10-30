package com.codex.blacklight.config;
import net.minecraftforge.common.ForgeConfigSpec;
/** Local display only: never used by damage, healing or server-side progression. */
public final class PassiveHudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_STACKS,SHOW_ZONE;
    public static final ForgeConfigSpec.DoubleValue HEALTH_X,HEALTH_Y;
    static {
        var b = new ForgeConfigSpec.Builder();
        SHOW_STACKS = b.comment("Show this mod's passive stack HUD. Does not change gameplay.").define("showPassiveStacks", true);
        SHOW_ZONE=b.comment("Show the server-owned infection zone label. Display only.").define("showInfectionZone",true);
        HEALTH_X = b.comment("Health HUD horizontal placement, 0 = left, 1 = right.").defineInRange("healthHudX",0D,0D,1D);
        HEALTH_Y = b.comment("Health HUD vertical placement, 0 = top, 1 = bottom.").defineInRange("healthHudY",0D,0D,1D);
        SPEC = b.build();
    }
    private PassiveHudConfig() {}
    public static void toggle() { SHOW_STACKS.set(!SHOW_STACKS.get()); SPEC.save(); }
}
