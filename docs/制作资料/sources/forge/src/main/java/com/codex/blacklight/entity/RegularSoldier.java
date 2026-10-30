package com.codex.blacklight.entity;

import com.codex.blacklight.military.MilitaryFaction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Conventional military, kept distinct from Blackwatch without asserting an unverified branch. */
public final class RegularSoldier extends MilitarySoldier {
    public RegularSoldier(EntityType<? extends RegularSoldier> type, Level level) { super(type, level); }
    public RegularSoldier(EntityType<? extends RegularSoldier> type,Level level,com.codex.blacklight.military.MilitaryRole role){super(type,level,role);}
    @Override public MilitaryFaction faction() { return MilitaryFaction.MILITARY; }
}
