package com.codex.passives;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
public final class MarkerEffect extends MobEffect {
    public MarkerEffect(int color) { super(MobEffectCategory.BENEFICIAL, color); }
}
