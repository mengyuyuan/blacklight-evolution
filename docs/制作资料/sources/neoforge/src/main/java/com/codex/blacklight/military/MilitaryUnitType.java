package com.codex.blacklight.military;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Labels and defaults for this mod, not a claim about Prototype's canonical force sizes. */
public enum MilitaryUnitType {
    TACTICAL_SQUAD(0, 8), SECTION(0, 8), PLATOON(1, 24), COMPANY(2, 72), SQUADRON(1, 24), BATTALION(3, 216);
    private final int level, size;
    MilitaryUnitType(int level, int size) { this.level = level; this.size = size; }
    public int level() { return level; }
    public boolean leaf() { return level == 0; }
    public int defaultSize(MilitaryFaction faction) { return this == BATTALION && faction == MilitaryFaction.BLACKWATCH ? 72 : size; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String labelKey() { return "unit.blacklight." + id(); }
    public static Optional<MilitaryUnitType> parse(String id) {
        return Arrays.stream(values()).filter(v -> v.id().equals(id)).findFirst();
    }
}
