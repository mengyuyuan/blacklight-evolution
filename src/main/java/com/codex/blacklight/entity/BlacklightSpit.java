package com.codex.blacklight.entity;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class BlacklightSpit extends ThrowableItemProjectile {
    public BlacklightSpit(EntityType<? extends BlacklightSpit> type,Level level){super(type,level);}
    public BlacklightSpit(Level level,LivingEntity owner){super(com.codex.blacklight.registry.ModEntities.BLACKLIGHT_SPIT.get(),owner,level);}
    @Override protected Item getDefaultItem(){return Items.ROTTEN_FLESH;}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(hit.getEntity() instanceof LivingEntity target&&!(target instanceof BlacklightMutant)&&!(target instanceof BlacklightHunter)){
            if(target.hurt(damageSources().thrown(this,getOwner()),4))target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,45,0));
        }
    }
    @Override protected void onHit(HitResult hit){super.onHit(hit);if(!level().isClientSide){var server=(net.minecraft.server.level.ServerLevel)level();server.sendParticles(com.codex.blacklight.registry.ModParticles.IMPACT_SMOKE.get(),getX(),getY(),getZ(),3,.15,.15,.15,.025);server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.32F,.025F,.05F),.8F),getX(),getY(),getZ(),6,.18,.18,.18,.025);discard();}}
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>80)discard();}
}
