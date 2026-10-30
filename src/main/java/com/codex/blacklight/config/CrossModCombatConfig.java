package com.codex.blacklight.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned balance. Clients never choose damage multipliers. */
public final class CrossModCombatConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DEFENSE, OFFENSE, TRUE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue INCOMING, OUTGOING, WINDOW_FRACTION;
    public static final ForgeConfigSpec.IntValue WINDOW_TICKS;
    public static final ForgeConfigSpec.DoubleValue MOB_HIT_FRACTION;
    static {
        var b = new ForgeConfigSpec.Builder();
        DEFENSE = b.comment("Reduce external-mod attacks against Blacklight mobs, active Prototypes and dropped Blacklight items.")
                .define("externalDefense", true);
        INCOMING = b.defineInRange("externalIncomingMultiplier", .25, 0, 1);
        WINDOW_FRACTION = b.comment("Maximum external damage per window, as a fraction of maximum health. Item entities accept at least one HP per window.")
                .defineInRange("externalWindowHealthFraction", .15, .01, 1);
        WINDOW_TICKS = b.defineInRange("externalWindowTicks", 10, 1, 200);
        MOB_HIT_FRACTION=b.comment("Single external-mod hit limit for Blacklight creatures only. Does not cap native player skills.")
                .defineInRange("externalMobSingleHitHealthFraction",.06,.001,1);
        OFFENSE = b.comment("Enhance native Blacklight attacks against other mods' living entities, excluding players.")
                .define("externalOffense", true);
        OUTGOING = b.defineInRange("externalOutgoingMultiplier", 2D, 1D, 100D);
        TRUE_DAMAGE = b.comment("Native attacks bypass armor, resistance, enchantments and shields on external mobs. Does not bypass administrative invulnerability or cancel death events.")
                .define("externalTrueDamage", true);
        SPEC = b.build();
    }
    private CrossModCombatConfig() {}
}
