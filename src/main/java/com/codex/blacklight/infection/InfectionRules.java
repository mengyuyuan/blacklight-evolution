package com.codex.blacklight.infection;

/** Exposure balance. Explicit infection commands and direct vial hits remain reliable. */
public final class InfectionRules {
    public static final float CONTACT_CHANCE = 0.08F;
    public static final int CONTACT_COOLDOWN_TICKS = 10 * 20;
    public static final String NEXT_CONTACT_EXPOSURE = "BlacklightNextContactExposure";
    public static final double VIAL_RADIUS = 16.0;
    public static final float SPLASH_CENTER_CHANCE = 1.0F;
    public static final float SPLASH_EDGE_CHANCE = 0.50F;

    private InfectionRules() {}

    public static float splashChance(double distance) {
        double fraction = com.codex.blacklight.util.MathCompat.clamp(distance / VIAL_RADIUS, 0, 1);
        return (float)(SPLASH_CENTER_CHANCE + (SPLASH_EDGE_CHANCE - SPLASH_CENTER_CHANCE) * fraction);
    }
}
