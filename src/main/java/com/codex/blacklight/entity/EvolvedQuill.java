package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Physical, dodgeable bone projectile. Owner attribution preserves the normal damage pipeline. */
public final class EvolvedQuill extends ThrowableItemProjectile {
    private float damage=8;
    public EvolvedQuill(EntityType<? extends EvolvedQuill> type,Level level){super(type,level);}
    public EvolvedQuill(Level level,LivingEntity owner){super(com.codex.blacklight.registry.ModEntities.EVOLVED_QUILL.get(),owner,level);}
    public void damage(float amount){damage=Float.isFinite(amount)?com.codex.blacklight.forge.WingMath.clamp(amount,0,1000000):0;}
    @Override protected Item getDefaultItem(){return Items.IRON_NUGGET;}
    @Override protected float getGravity(){return .012F;}
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&(!(getOwner() instanceof LivingEntity owner)||!owner.isAlliedTo(e));}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(hit.getEntity() instanceof LivingEntity target&&target.hurt(damageSources().thrown(this,getOwner()),damage)){
            var server=(net.minecraft.server.level.ServerLevel)level();server.sendParticles(com.codex.blacklight.registry.ModParticles.BIOMASS_SLASH.get(),target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ(),5,.15,.2,.15,.03);
            // Accepted-hit observer provides the bone/flesh contact sound once.
        }
    }
    @Override protected void onHit(HitResult hit){super.onHit(hit);if(!level().isClientSide)discard();}
    @Override public void tick(){super.tick();if(level().isClientSide&&tickCount%2==0)level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.55F,.09F,.12F),.65F),getX(),getY(),getZ(),0,0,0);if(!level().isClientSide&&tickCount>65)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("QuillDamage",damage);tag.putInt("QuillAge",tickCount);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);damage(tag.getFloat("QuillDamage"));tickCount=com.codex.blacklight.forge.WingMath.clamp(tag.getInt("QuillAge"),0,66);}
}
