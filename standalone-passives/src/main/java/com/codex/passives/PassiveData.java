package com.codex.passives;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Versioned independent save; no Blacklight attachment or skill bit is required. */
public final class PassiveData {
    public static final String KEY = "adaptive_passives";
    public int adaptationTier, regenerationTier, adaptation, regeneration;
    public boolean vitality;
    public double bonusHealth;
    public long nextHeal;
    public static PassiveData read(Player p) {
        return decode(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY));
    }
    public static PassiveData decode(CompoundTag n) {
        var d = new PassiveData();
        d.adaptationTier = Math.max(0, Math.min(2, n.getInt("adaptation_tier")));
        d.regenerationTier = Math.max(0, Math.min(2, n.getInt("regeneration_tier")));
        d.adaptation = Math.max(0, n.getInt("adaptation"));
        d.regeneration = Math.max(0, n.getInt("regeneration"));
        d.vitality = n.getBoolean("vitality");
        double bonus = n.getDouble("bonus_health");
        d.bonusHealth = Double.isFinite(bonus) ? Math.max(0, Math.min(Float.MAX_VALUE / 32.0, bonus)) : 0;
        d.nextHeal = Math.max(0, n.getLong("next_heal"));
        return d;
    }
    public CompoundTag encode() {
        var n = new CompoundTag(); n.putInt("version", 1);
        n.putInt("adaptation_tier", adaptationTier); n.putInt("regeneration_tier", regenerationTier);
        n.putInt("adaptation", adaptation); n.putInt("regeneration", regeneration);
        n.putBoolean("vitality", vitality); n.putDouble("bonus_health", bonusHealth); n.putLong("next_heal", nextHeal);
        return n;
    }
    public void save(Player p) {
        var persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, encode()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
