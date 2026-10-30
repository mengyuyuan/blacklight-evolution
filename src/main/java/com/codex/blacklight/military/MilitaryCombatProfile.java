package com.codex.blacklight.military;

/** Faction/role tuning; rank and command appointments never enter these combat numbers. */
public record MilitaryCombatProfile(double health, double armour, float damage, double speed,
                                    double knockbackResistance, int aimTicks, int cycleTicks) {
    public int burstTicks() { return aimTicks + 17; }
    public static MilitaryCombatProfile of(MilitaryFaction faction, boolean heavy) {
        if (faction == MilitaryFaction.MILITARY)
            return new MilitaryCombatProfile(heavy ? 48 : 20, heavy ? 6 : 2, heavy ? 2F : 1.4F,
                    heavy ? .24 : .29, heavy ? .4 : .1, 16, heavy ? 84 : 72);
        return new MilitaryCombatProfile(heavy ? 60 : 24, heavy ? 10 : 4, heavy ? 2.2F : 1.6F,
                heavy ? .24 : .29, heavy ? .4 : .1, 12, heavy ? 76 : 64);
    }
}
