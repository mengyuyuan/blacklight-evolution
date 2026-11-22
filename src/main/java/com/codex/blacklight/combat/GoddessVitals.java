package com.codex.blacklight.combat;

/** Damage reaching a threshold belongs to the same hit even if an execution or a save separates the phases. */
public final class GoddessVitals {
    public static final int FULL = 150_000;
    public static final int LEFT_ARM = 1, RIGHT_ARM = 2, HEAD = 4;
    private int removed;
    private boolean waiting;
    private float remainder;

    public int removed() { return removed; }
    public boolean waiting() { return waiting; }
    public boolean finished() { return (removed & HEAD) != 0; }
    public int threshold() { return FULL - (Integer.bitCount(removed & 3) + 1) * (FULL / 3); }

    public boolean accepts(int part) {
        if (!waiting || finished()) return false;
        if ((removed & 3) == 3) return part == HEAD;
        return (part == LEFT_ARM || part == RIGHT_ARM) && (removed & part) == 0;
    }

    /** The vanilla entity stays alive at one physical HP until the head has been absorbed. */
    public float healthAfterHit(float current, float proposed) {
        if (waiting || finished() || !Float.isFinite(proposed)) return current;
        int floor = threshold();
        if (proposed > floor) return proposed;
        waiting = true;
        remainder = Math.min(floor, Math.max(0, floor - proposed));
        return Math.max(1, floor);
    }

    public float absorb(int part, float current) {
        if (!accepts(part)) return current;
        int floor = threshold();
        float carried = remainder;
        removed |= part;
        remainder = 0;
        waiting = false;
        return finished() ? 0 : healthAfterHit(floor, floor - carried);
    }

    public float remainder() { return remainder; }

    public void restore(int parts, boolean paused, float carry) {
        removed = (parts & 3) == 3 ? parts & 7 : parts & 3;
        waiting = paused && !finished();
        remainder = waiting && Float.isFinite(carry) ? Math.max(0, Math.min(threshold(), carry)) : 0;
    }
}
