package com.codex.blacklight.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** A real player-owned hit: normal damage/death hooks still run. Never removes a target or sets its health. */
public final class BulletdiveDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("blacklight", "bulletdive"));
    private BulletdiveDamage() {}
    public static DamageSource source(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE), player);
    }
}
