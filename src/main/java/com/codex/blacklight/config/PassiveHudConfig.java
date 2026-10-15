package com.codex.blacklight.config;
import net.minecraftforge.common.ForgeConfigSpec;
/** Local display only: never used by damage, healing or server-side progression. */
public final class PassiveHudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_STACKS;
    static {
        var b = new ForgeConfigSpec.Builder();
        SHOW_STACKS = b.comment("Show this mod's passive stack HUD. Does not change gameplay.").define("showPassiveStacks", true);
        SPEC = b.build();
    }
    private PassiveHudConfig() {}
    public static void toggle() { SHOW_STACKS.set(!SHOW_STACKS.get()); SPEC.save(); }
}
