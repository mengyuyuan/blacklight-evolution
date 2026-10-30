package com.codex.blacklight.military;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Project factions; "military" deliberately does not assert a particular real-world branch. */
public enum MilitaryFaction {
    MILITARY, BLACKWATCH;
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String labelKey() { return "faction.blacklight." + id(); }
    public static Optional<MilitaryFaction> parse(String id) {
        return Arrays.stream(values()).filter(v -> v.id().equals(id)).findFirst();
    }
}
