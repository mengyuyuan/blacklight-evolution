package com.codex.blacklight.infection;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Dimension-wide budgets, never multiplied by the number of surface blocks. */
public final class BuildingInfectionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, NATURAL_SEEDS, SPAWNS, VOLUME;
    public static final ModConfigSpec.IntValue WORK, COLONIES, RADIUS, HEIGHT, SURFACES, MATURITY, MOB_LIMIT;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.define("buildingInfection", true);
        VOLUME=b.comment("Grow solid, connected biomass columns using the shared work budget.").define("buildingBiomass",true);
        NATURAL_SEEDS = b.comment("Existing infected tissue can establish a core beside a substantial wall.").define("naturalCores", true);
        SPAWNS = b.define("hatchInfected", true);
        WORK = b.comment("Shared frontier/aging operations per dimension every five ticks.").defineInRange("workBudget", 4, 1, 16);
        COLONIES = b.comment("Includes destroyed cores, so cleared sites cannot endlessly reseed.").defineInRange("maxColonies", 16, 1, 64);
        RADIUS = b.defineInRange("horizontalRadius", 48, 8, 96);
        HEIGHT = b.defineInRange("verticalReach", 96, 8, 192);
        SURFACES = b.defineInRange("surfacesPerColony", 8192, 64, 16384);
        MATURITY = b.comment("Loaded, active seconds; sufficient actual surface growth is also required.").defineInRange("maturitySeconds", 600, 60, 7200);
        MOB_LIMIT = b.defineInRange("nearbyMobLimit", 12, 0, 32);
        SPEC = b.build();
    }
    private BuildingInfectionConfig() {}
}
