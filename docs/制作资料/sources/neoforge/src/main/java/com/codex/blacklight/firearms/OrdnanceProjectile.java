package com.codex.blacklight.firearms;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** One bounded impact, optional protected terrain damage, no fire or secondary infection projectiles. */
public final class OrdnanceProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(OrdnanceProjectile.class,EntityDataSerializers.INT);
    public OrdnanceProjectile(EntityType<? extends OrdnanceProjectile> type,Level level){super(type,level);}
    public OrdnanceProjectile(Level level,LivingEntity owner,WeaponDefinition.Kind kind){
        super(com.codex.blacklight.registry.ModEntities.MILITARY_ORDNANCE.get(),owner,level);
        entityData.set(KIND,kind.ordinal());setItem(new ItemStack(FirearmsRegistry.ammo(definition().ammoId())));
        setNoGravity(kind==WeaponDefinition.Kind.ROCKET||kind==WeaponDefinition.Kind.MISSILE);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(KIND,WeaponDefinition.Kind.GRENADE.ordinal());}
    public WeaponDefinition definition(){int index=entityData.get(KIND);return WeaponCatalog.projectile(index>=0&&index<WeaponDefinition.Kind.values().length?WeaponDefinition.Kind.values()[index]:WeaponDefinition.Kind.GRENADE);}
    @Override protected Item getDefaultItem(){return Items.FIRE_CHARGE;}
    @Override protected boolean canHitEntity(Entity entity){return entity!=getOwner()&&super.canHitEntity(entity);}
    @Override protected void onHit(HitResult result){
        super.onHit(result);
        if(!(level() instanceof ServerLevel level)||isRemoved())return;
        Vec3 center=result.getLocation();
        if(result instanceof BlockHitResult block)center=center.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.04));
        setPos(center);var d=definition();LivingEntity owner=getOwner() instanceof LivingEntity living&&living.isAlive()&&!living.isRemoved()&&living.level()==level?living:null;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(d.radius()),LivingEntity::isAlive)){
            if(owner==null)continue;
            if(FirearmsCombat.protectedTarget(owner,target)||target.distanceToSqr(center)>d.radius()*d.radius())continue;
            var wall=level.clip(new ClipContext(center,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS)continue;
            if(d.kind()==WeaponDefinition.Kind.INFECTION)com.codex.blacklight.infection.BlacklightInfectionSystem.infect(target,this);
            else target.hurt(damageSources().explosion(this,getOwner()),d.damage()*(float)Math.max(.2,1-Math.sqrt(target.distanceToSqr(center))/d.radius()));
        }
        if(owner!=null)FirearmBlockDamage.blast(level,owner,d,center);
        level.sendParticles(d.kind()==WeaponDefinition.Kind.INFECTION?net.minecraft.core.particles.ParticleTypes.SNEEZE:net.minecraft.core.particles.ParticleTypes.EXPLOSION,center.x,center.y,center.z,12,d.radius()*.3,.5,d.radius()*.3,.03);
        level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.HOSTILE,1,1);
        discard();
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>100)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("OrdnanceKind",entityData.get(KIND));tag.putInt("OrdnanceAge",tickCount);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(KIND,tag.getInt("OrdnanceKind"));tickCount=Math.max(0,tag.getInt("OrdnanceAge"));}
}

