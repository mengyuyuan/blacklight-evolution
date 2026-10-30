package com.codex.blacklight.military;

/** Per-dimension intent. A null order on a unit inherits its ancestor's intent. */
public record MilitaryOrder(Mode mode, Point target) {
    public enum Mode { GUARD, PATROL, MOVE, REGROUP, RETURN }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public boolean valid() { return mode != null && (target == null || target.finite()) && (mode != Mode.MOVE || target != null); }
}
