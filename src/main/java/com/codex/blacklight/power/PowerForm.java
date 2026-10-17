package com.codex.blacklight.power;

import java.util.Locale;

public enum PowerForm {
    NONE("none", 0, 0),
    CLAWS("claws", 1 << 0, 0),
    HAMMERFISTS("hammerfists", 1 << 1, 250),
    WHIPFIST("whipfist", 1 << 2, 350),
    MUSCLE_MASS("muscle_mass", 1 << 3, 150),
    BLADE("blade", 1 << 4, 500),
    SHIELD("shield", 1 << 5, 200),
    ARMOR("armor", 1 << 6, 600),
    THERMAL_VISION("thermal_vision", 1 << 7, 250),
    INFECTED_VISION("infected_vision", 1 << 8, 300),
    BIOMASS("biomass",1 << 9,0),
    SOVEREIGN_WINGS("sovereign_wings",1 << 10,1000);

    private final String id;
    private final int bit;
    private final int epCost;

    PowerForm(String id, int bit, int epCost) {
        this.id = id;
        this.bit = bit;
        this.epCost = epCost;
    }

    public String id() {
        return id;
    }

    public int bit() {
        return bit;
    }

    public int epCost() {
        return epCost;
    }

    public static PowerForm byId(String id) {
        if (id == null) return NONE;
        String normalized = id.toLowerCase(Locale.ROOT);
        for (PowerForm form : values()) {
            if (form.id.equals(normalized)) return form;
        }
        return NONE;
    }
}
