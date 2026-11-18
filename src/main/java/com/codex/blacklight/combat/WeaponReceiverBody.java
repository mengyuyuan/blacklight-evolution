package com.codex.blacklight.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class WeaponReceiverBody {
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> DISABLED=
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    new net.minecraft.resources.ResourceLocation("blacklight","ragdoll_disabled"));
    private WeaponReceiverBody() {}
    public static boolean supportedBody(LivingEntity v){
        return !(v instanceof Player) && !v.getType().is(DISABLED) && Float.isFinite(v.getBbWidth()+v.getBbHeight())
                && v.getBbWidth()>0 && v.getBbHeight()>0 && v.getBbWidth()<=64 && v.getBbHeight()<=64;
    }
    /** Dedicated rigs have fixed adult anatomy; every other model uses its rendered skeleton. */
    public static boolean legacyBody(LivingEntity v){
        return !v.isBaby()&&!v.isPassenger()&&!v.isSleeping()&&Math.abs(v.getScale()-1)<.01
                &&v.getBbWidth()<=1.2F&&v.getBbHeight()<=2.8F && (v instanceof net.minecraft.world.entity.monster.Zombie
                ||v instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                ||v instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                ||v instanceof com.codex.blacklight.entity.MilitarySoldier
                ||v instanceof com.codex.blacklight.entity.BlacklightCommonInfected
                ||v instanceof net.minecraft.world.entity.animal.Pig);
    }
}
