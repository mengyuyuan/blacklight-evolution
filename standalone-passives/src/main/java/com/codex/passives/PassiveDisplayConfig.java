package com.codex.passives;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Local display only: never used by damage, healing or server-side progression. */
public final class PassiveDisplayConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_STACKS;
    static {
        var b = new ModConfigSpec.Builder();
        SHOW_STACKS = b.comment("Show this mod's passive stack HUD. Does not change gameplay.").define("showPassiveStacks", true);
        SPEC = b.build();
    }
    private PassiveDisplayConfig() {}
    public static void toggle() { SHOW_STACKS.set(!SHOW_STACKS.get()); SPEC.save(); }
}
