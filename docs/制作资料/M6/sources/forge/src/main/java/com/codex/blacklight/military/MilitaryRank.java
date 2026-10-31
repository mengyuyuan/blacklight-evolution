package com.codex.blacklight.military;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Game ranks, independent of an appointment and of combat attributes. Stable IDs are saved. */
public enum MilitaryRank {
    PRIVATE, CORPORAL, SERGEANT, LIEUTENANT, CAPTAIN, MAJOR, COLONEL, GENERAL;
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String labelKey() { return "rank.blacklight." + id(); }
    public static Optional<MilitaryRank> parse(String id) {
        return Arrays.stream(values()).filter(v -> v.id().equals(id)).findFirst();
    }
    public static MilitaryRank fromId(String id) { return parse(id).orElse(PRIVATE); }
}
