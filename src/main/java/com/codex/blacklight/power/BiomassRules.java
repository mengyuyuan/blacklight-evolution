package com.codex.blacklight.power;
import net.minecraft.world.level.GameRules;
/** Server-owned world rules; terrain controls do not disable the form or its animation. */
public final class BiomassRules {
    private BiomassRules(){}
    public static final GameRules.Key<GameRules.BooleanValue> TERRAIN=GameRules.register("blacklightDeathSlimeTerrain",GameRules.Category.PLAYER,GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> CHANCE=GameRules.register("blacklightDeathSlimeTerrainChance",GameRules.Category.PLAYER,GameRules.IntegerValue.create(15));
    public static final GameRules.Key<GameRules.IntegerValue> LIMIT=GameRules.register("blacklightDeathSlimeTerrainLimit",GameRules.Category.PLAYER,GameRules.IntegerValue.create(12));
    public static final GameRules.Key<GameRules.IntegerValue> COST=GameRules.register("blacklightDeathSlimeCost",GameRules.Category.PLAYER,GameRules.IntegerValue.create(150));
    public static void initialize(){}
}
