package com.codex.blacklight.effect;
/** Cosmetic marker only: persistence and arbitrary stack counts live in server-owned player state. */
public final class PassiveMarkerEffect extends net.minecraft.world.effect.MobEffect {
    public PassiveMarkerEffect(int color) { super(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, color); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientMobEffectExtensions() {
            @Override public boolean isVisibleInInventory(net.minecraft.world.effect.MobEffectInstance instance) { return false; }
            @Override public boolean isVisibleInGui(net.minecraft.world.effect.MobEffectInstance instance) { return false; }
        });
    }
}
