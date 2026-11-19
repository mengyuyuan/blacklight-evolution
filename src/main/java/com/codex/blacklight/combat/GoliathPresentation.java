package com.codex.blacklight.combat;

/** Minecraft dimensions and terrain tuning for the source-bound Goliath rig. */
public final class GoliathPresentation {
    public static final float SCALE = 1.5F;
    public static final float WIDTH = 4.5F * SCALE;
    public static final float HEIGHT = 8F * SCALE;
    // Roughly a third of its standing height. Allow three full blocks with voxel clearance.
    public static final float STEP_HEIGHT = 3.25F;

    private GoliathPresentation() {}
}
