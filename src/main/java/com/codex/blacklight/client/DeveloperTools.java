package com.codex.blacklight.client;

/** Explicit local opt-in; release clients expose no model/animation/tuning editors. */
public final class DeveloperTools {
    private DeveloperTools() {}
    public static boolean enabled() { return Boolean.getBoolean("blacklight.developerTools"); }
}
