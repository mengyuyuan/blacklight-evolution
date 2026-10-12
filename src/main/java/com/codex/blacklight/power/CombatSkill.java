package com.codex.blacklight.power;

/** Original skill identities with Minecraft EP prices and prerequisite adaptation. */
public enum CombatSkill {
    CRITICAL_MASS("critical_mass", 1, 250, null),
    ADRENALINE_SURGE("adrenaline_surge", 1 << 1, 350, CRITICAL_MASS),
    BULLETDIVE_DROP("bulletdive_drop", 1 << 2, 350, null),
    GROUNDSPIKE_GRAVEYARD("groundspike_graveyard", 1 << 3, 500, CRITICAL_MASS),
    TENDRIL_BARRAGE("tendril_barrage", 1 << 4, 500, CRITICAL_MASS),
    CRITICAL_PAIN("critical_pain", 1 << 5, 650, CRITICAL_MASS),
    CRITICAL_MASS_BOOST_1("critical_mass_boost_1", 1 << 6, 350, CRITICAL_MASS),
    CRITICAL_MASS_BOOST_2("critical_mass_boost_2", 1 << 7, 500, CRITICAL_MASS_BOOST_1),
    CRITICAL_MASS_BOOST_3("critical_mass_boost_3", 1 << 8, 700, CRITICAL_MASS_BOOST_2);

    private final String id;
    private final int bit, epCost;
    private final CombatSkill requires;

    CombatSkill(String id, int bit, int epCost, CombatSkill requires) {
        this.id = id; this.bit = bit; this.epCost = epCost; this.requires = requires;
    }
    public String id() { return id; }
    public int bit() { return bit; }
    public int epCost() { return epCost; }
    public CombatSkill requires() { return requires; }
    public MovementUpgrade movementRequires() { return this == BULLETDIVE_DROP ? MovementUpgrade.GLIDE_1 : null; }
    /** Each verified +400 source-health boost adds one base Devastator cost of storage. */
    public static int criticalMassCapacity(int mask) {
        return 1 + ((mask & CRITICAL_MASS_BOOST_3.bit) != 0 ? 3
                : (mask & CRITICAL_MASS_BOOST_2.bit) != 0 ? 2
                : (mask & CRITICAL_MASS_BOOST_1.bit) != 0 ? 1 : 0);
    }
    public static int validMask() { return (1 << values().length) - 1; }
    public static CombatSkill byId(String id) {
        for (var skill : values()) if (skill.id.equals(id)) return skill;
        return null;
    }
}
