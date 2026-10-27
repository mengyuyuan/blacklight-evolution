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
    public void damage(float amount){damage=Float.isFinite(amount)?Math.clamp(amount,0,1000000):0;}
    @Override protected Item getDefaultItem(){return Items.IRON_NUGGET;}
    @Override protected double getDefaultGravity(){return .012;}
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&(!(getOwner() instanceof LivingEntity owner)||!owner.isAlliedTo(e));}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(hit.getEntity() instanceof LivingEntity target)target.hurt(damageSources().thrown(this,getOwner()),damage);
    }
    @Override protected void onHit(HitResult hit){super.onHit(hit);if(!level().isClientSide)discard();}
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>65)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("QuillDamage",damage);tag.putInt("QuillAge",tickCount);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);damage(tag.getFloat("QuillDamage"));tickCount=Math.clamp(tag.getInt("QuillAge"),0,66);}
}
