package com.codex.blacklight.combat;

import com.codex.blacklight.power.CombatScale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;

/** Devastator budgets are actual HP, independent of the victim's blood-unit presentation. */
public final class DevastatorDamage {
    public static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("blacklight","devastator"));
    private DevastatorDamage(){}
    public static float primary(int kind){return switch(kind){case 0->2000;case 1->1600;case 2->4000;default->throw new IllegalArgumentException("Not a formal Devastator: "+kind);};}
    /** Keep secondary source-contact feedback small beside the primary strike. */
    public static float secondary(float sourceDamage){return sourceDamage*.2F;}
    public static boolean isTrueDamage(DamageSource source){return source.is(TYPE)||source.is(BulletdiveDamage.TYPE);}
    public static DamageSource source(ServerPlayer player){return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),player);}
    public static boolean hurt(ServerPlayer player,LivingEntity target,float healthDamage){
        if(!Float.isFinite(healthDamage)||healthDamage<=0||target.level()!=player.level()||!target.isAlive()||target.isRemoved())return false;
        return target.hurt(source(player),healthDamage/CombatScale.units(target));
    }
}
