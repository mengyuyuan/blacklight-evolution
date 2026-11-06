package com.codex.blacklight.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned balance. Clients never choose damage multipliers. */
public final class CrossModCombatConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEFENSE, OFFENSE, TRUE_DAMAGE, REDUCE_PLAYER_ATTACKS, PROTECT_COMMON, LEGACY_ADVANTAGE, NATIVE_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue CULTIVATION_NATIVE_HEALTH, CULTIVATION_INHERIT_ATTACK, CULTIVATION_TRUE_DAMAGE, CULTIVATION_LATE_BUDGET, CULTIVATION_NATIVE_HITS;
    public static final ModConfigSpec.DoubleValue INCOMING, OUTGOING, WINDOW_FRACTION;
    public static final ModConfigSpec.IntValue WINDOW_TICKS;
    public static final ModConfigSpec.DoubleValue MOB_HIT_FRACTION;
    static {
        var b = new ModConfigSpec.Builder();
        NATIVE_DIAGNOSTICS=b.comment("Keep one value-only native hit receipt per target for diagnostics: HP, absorption and supported reserve consumption. No tick logging, chunk scans, forced damage or foreign state writes.").define("nativeHitDiagnostics",false);
        DEFENSE = b.comment("Reduce external NPC attacks against Blacklight elites, active Prototypes and dropped Blacklight items. Player ownership and ordinary-fodder policies are separate.")
                .define("externalDefense", true);
        REDUCE_PLAYER_ATTACKS=b.comment("Legacy opt-in. Apply external damage reduction to player-owned attacks too, including pets and spells. Default false keeps other player classes and weapons viable.").define("reducePlayerOwnedAttacks",false);
        PROTECT_COMMON=b.comment("Legacy opt-in. Apply elite cross-mod anti-spike rules to ordinary infected fodder too. Default false.").define("protectCommonInfected",false);
        INCOMING = b.defineInRange("externalIncomingMultiplier", .25, 0, 1);
        WINDOW_FRACTION = b.comment("Maximum external damage per window, as a fraction of maximum health. Item entities accept at least one HP per window.")
                .defineInRange("externalWindowHealthFraction", .15, .01, 1);
        WINDOW_TICKS = b.defineInRange("externalWindowTicks", 10, 1, 200);
        MOB_HIT_FRACTION=b.comment("Single external-mod hit limit for Blacklight creatures only. Does not cap native player skills.")
                .defineInRange("externalMobSingleHitHealthFraction",.06,.001,1);
        LEGACY_ADVANTAGE=b.comment("Explicit opt-in for blanket cross-mod offense. Kept separate so old saved externalOffense=true configurations do not silently defeat ecosystem coexistence. Formal devastators and Bulletdive stay true damage independently.").define("legacyBlanketOffense",false);
        OFFENSE = b.comment("Enhance native Blacklight attacks against other mods' living entities, excluding players.")
                .define("externalOffense", false);
        OUTGOING = b.defineInRange("externalOutgoingMultiplier", 2D, 1D, 100D);
        TRUE_DAMAGE = b.comment("Native attacks bypass armor, resistance, enchantments and shields on external mobs. Does not bypass administrative invulnerability or cancel death events.")
                .define("externalTrueDamage", false);
        b.push("cultivationCompatibility");
        CULTIVATION_NATIVE_HEALTH = b.comment("Keep Xiaoxiang creatures and their infected hosts in native HP units, instead of adding Blacklight's fivefold display conversion. Existing mobs migrate while preserving health ratio; normal infection/evolution growth remains.")
                .define("nativeHealthUnits", true);
        CULTIVATION_INHERIT_ATTACK = b.comment("Inherit supported authored cultivation melee/technique bonuses when converting a creature. Does not copy spells or foreign capability data.")
                .define("inheritMeleeBonuses", true);
        CULTIVATION_NATIVE_HITS = b.comment("Let active Prototype native melee damage cultivation NPC/beast shields without their automatic melee repulsion. Does not multiply damage, bypass ordinary armor, affect player shields, or disable other mods' protections.")
                .define("nativeHitsBypassMobShield", true);
        CULTIVATION_TRUE_DAMAGE = b.comment("Allow native Blacklight true damage through cultivation NPC/beast Qi shields and flat body defense. Formal Devastator/Bulletdive true damage works independently of blanket external offense. Other attacks require explicit legacyBlanketOffense/externalOffense/externalTrueDamage. Player shields and scripted invulnerability remain intact.")
                .define("nativeTrueDamageBypassesMobQi", true);
        CULTIVATION_LATE_BUDGET = b.comment("For infected players who also cultivate, apply the existing cultivation-creature damage budget after their flat defenses, preventing an early two-point cap from being entirely subtracted. Does not bypass player shields or force a minimum hit.")
                .define("budgetAfterPlayerDefense", true);
        b.pop();
        SPEC = b.build();
    }
    private CrossModCombatConfig() {}
}
