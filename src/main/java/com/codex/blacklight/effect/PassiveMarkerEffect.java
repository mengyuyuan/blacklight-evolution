package com.codex.blacklight.effect;
/** Cosmetic marker only: persistence and arbitrary stack counts live in server-owned player state. */
public final class PassiveMarkerEffect extends net.minecraft.world.effect.MobEffect {
    public PassiveMarkerEffect(int color) { super(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, color); }
}
