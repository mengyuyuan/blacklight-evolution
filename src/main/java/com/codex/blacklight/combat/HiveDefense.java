package com.codex.blacklight.combat;

import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

/** Structural tissue resists conventional weapons, including armor-piercing rounds. */
public final class HiveDefense {
    public static final int CHIP_INTERVAL = 10;
    public static final float CONVENTIONAL_FACTOR = .1F, MAX_CHIP = 2F;
    private HiveDefense() {}
    public static boolean fullDamage(DamageSource source) {
        // Only actual administrative/void types bypass building defense. A gun
        // type tagged bypasses_armor/invulnerability must not become a hive killer.
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return true;
        if (!(source.getEntity() instanceof Player player) || !PrototypeMetabolism.active(player)) return false;
        return DevastatorDamage.isTrueDamage(source) || source.is(DamageTypes.PLAYER_ATTACK)
                && source.getDirectEntity() == player && !source.is(DamageTypeTags.IS_PROJECTILE);
    }
    public static float chipDamage(float incoming) {
        return Float.isFinite(incoming) && incoming > 0 ? Math.min(MAX_CHIP, incoming * CONVENTIONAL_FACTOR) : 0;
    }
}
