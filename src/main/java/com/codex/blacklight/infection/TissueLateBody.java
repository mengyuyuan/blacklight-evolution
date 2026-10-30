package com.codex.blacklight.infection;

/** Saved-age shape key for the second, species-specific body silhouette.
 * This does not replace inherited organs, award organs or advance boss encounters. */
public final class TissueLateBody {
    public static final long START = TissueGrowth.ANCIENT_TICKS;
    public static final long COMPLETE = START + 4 * TissueEvolution.GENERATION_TICKS;
    private TissueLateBody() {}

    public static float progress(long age) {
        if (age <= START) return 0;
        if (age >= COMPLETE) return 1;
        float t = (age - START) / (float)(COMPLETE - START);
        return t * t * (3 - 2 * t);
    }
}
