package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Visible, collidable shots; explosive damage observes line of sight and never destroys terrain. */
public final class EncounterProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> EXPLOSIVE=SynchedEntityData.defineId(EncounterProjectile.class,EntityDataSerializers.BOOLEAN);
    public EncounterProjectile(EntityType<? extends EncounterProjectile> type,Level level){super(type,level);}
    public EncounterProjectile(Level level,EncounterMob owner,boolean explosive){super(com.codex.blacklight.registry.EncounterEntities.PROJECTILE.get(),owner,level);entityData.set(EXPLOSIVE,explosive);if(explosive)setItem(new ItemStack(Items.FIRE_CHARGE));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(EXPLOSIVE,false);}
    @Override protected Item getDefaultItem(){return Items.SLIME_BALL;}
    @Override protected double getDefaultGravity(){return .03;}
    @Override protected boolean canHitEntity(Entity entity){return !(entity instanceof EncounterPart part&&part.getParent()==getOwner())&&!(entity instanceof GreenePart greene&&greene.getParent()==getOwner())&&!(entity instanceof FlyerPart flyer&&flyer.getParent()==getOwner())&&super.canHitEntity(entity);}
    private boolean hostile(LivingEntity target){return getOwner() instanceof EncounterMob creature?creature.enemy(target):getOwner() instanceof LivingEntity owner&&target!=owner&&!owner.isAlliedTo(target)&&(!(target instanceof net.minecraft.world.entity.player.Player player)||!player.isCreative()&&!player.isSpectator());}
    @Override protected void onHitEntity(EntityHitResult hit){super.onHitEntity(hit);if(level().isClientSide||entityData.get(EXPLOSIVE))return;
        if(hit.getEntity() instanceof LivingEntity target&&hostile(target))target.hurt(damageSources().thrown(this,getOwner()),6);
        else if(hit.getEntity() instanceof EncounterPart part&&hostile(part.getParent()))part.hurt(damageSources().thrown(this,getOwner()),6);
        else if(hit.getEntity() instanceof GreenePart part&&hostile(part.getParent()))part.hurt(damageSources().thrown(this,getOwner()),6);
        else if(hit.getEntity() instanceof FlyerPart part&&hostile(part.getParent()))part.hurt(damageSources().thrown(this,getOwner()),6);
    }
    @Override protected void onHit(HitResult hit){
        super.onHit(hit);if(level().isClientSide)return;
        if(entityData.get(EXPLOSIVE)&&getOwner() instanceof LivingEntity owner){for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(3),this::hostile)){
            Vec3 end=target.getBoundingBox().getCenter();if(position().distanceToSqr(end)>9||level().clip(new ClipContext(position().add(0,.1,0),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            target.hurt(damageSources().thrown(this,owner),8*(float)(1-position().distanceTo(end)/4));
        }}
        ((net.minecraft.server.level.ServerLevel)level()).sendParticles(entityData.get(EXPLOSIVE)?net.minecraft.core.particles.ParticleTypes.EXPLOSION:net.minecraft.core.particles.ParticleTypes.CRIT,getX(),getY(),getZ(),8,.3,.3,.3,.08);
        playSound(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),.6F,1.2F);discard();
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>100)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("EncounterExplosive",entityData.get(EXPLOSIVE));}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(EXPLOSIVE,tag.getBoolean("EncounterExplosive"));}
}
