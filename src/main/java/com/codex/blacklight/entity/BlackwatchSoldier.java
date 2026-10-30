package com.codex.blacklight.entity;

import com.codex.blacklight.military.MilitaryFaction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Original registry identities and home NBT remain loadable on the shared military behavior. */
public final class BlackwatchSoldier extends MilitarySoldier {
    public BlackwatchSoldier(EntityType<? extends BlackwatchSoldier> type,Level level){super(type,level);}
    public BlackwatchSoldier(EntityType<? extends BlackwatchSoldier> type,Level level,com.codex.blacklight.military.MilitaryRole role){super(type,level,role);}
    @Override public MilitaryFaction faction(){return MilitaryFaction.BLACKWATCH;}
}
