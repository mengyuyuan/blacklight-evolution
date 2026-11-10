package com.codex.blacklight.animation;

/**
 * Client game-time guard for a running grapple/consume scene. Every participant samples the
 * scene from the client game clock; a server time packet that pulls that clock back (the
 * client ran ahead during a frame stall) would rewind the whole scene, so the visible motion
 * jumps backwards. While a scene runs, a small backward correction is ignored and the clock
 * keeps ticking forward. Pausing instead does not work: the render partial tick keeps
 * cycling while the tick is frozen, so the scene creeps forward and snaps back every tick.
 * The server time is accepted again by the first correction after the scene ends.
 */
public final class SceneClock {
    private long ahead;
    /** Largest correction ignored; anything bigger is a real resync (world load, /time). */
    public static final long MAX_IGNORED = 40;

    /**
     * Resolve a requested game-time write. {@code current} is the client time before the write,
     * {@code requested} the new value, {@code scene} whether the local player's scene is running.
     * Returns the time to store.
     */
    public long resolve(long current, long requested, boolean scene) {
        if (!scene) { ahead = 0; return requested; }
        long back = current - requested;
        if (back > 0 && back <= MAX_IGNORED) { ahead = back; return current; }
        if (requested != current + 1) ahead = 0;
        return requested;
    }

    /** How far the client clock was ahead of the last ignored server correction. */
    public long ahead() { return ahead; }
}
