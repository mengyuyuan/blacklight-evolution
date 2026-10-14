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
    CRITICAL_MASS_BOOST_3("critical_mass_boost_3", 1 << 8, 700, CRITICAL_MASS_BOOST_2),
    SHIELD_BORER("shield_borer", 1 << 9, 350, null),
    SHIELD_BORER_BOOST_1("shield_borer_boost_1", 1 << 10, 250, SHIELD_BORER),
    SHIELD_BORER_BOOST_2("shield_borer_boost_2", 1 << 11, 450, SHIELD_BORER_BOOST_1),
    BLOCK_THROW_1("block_throw_1", 1 << 12, 150, null),
    BLOCK_THROW_2("block_throw_2", 1 << 13, 250, BLOCK_THROW_1),
    BLOCK_THROW_3("block_throw_3", 1 << 14, 450, BLOCK_THROW_2),
    ADAPTATION("adaptation", 1 << 15, 350, null),
    TISSUE_REGENERATION("tissue_regeneration", 1 << 16, 350, null),
    ADAPTATION_PERCENT("adaptation_percent", 1 << 17, 500, ADAPTATION);

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
    public boolean adaptivePassive() { return this == ADAPTATION || this == TISSUE_REGENERATION || this == ADAPTATION_PERCENT; }
    public boolean shieldBorer() { return this==SHIELD_BORER||this==SHIELD_BORER_BOOST_1||this==SHIELD_BORER_BOOST_2; }
    public boolean blockThrow() { return this==BLOCK_THROW_1||this==BLOCK_THROW_2||this==BLOCK_THROW_3; }
    public PowerForm powerRequires() { return shieldBorer() ? PowerForm.SHIELD : blockThrow() ? PowerForm.MUSCLE_MASS : null; }
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
