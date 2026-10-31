package com.codex.blacklight.entity;

import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.power.BiomassState;
import com.codex.blacklight.combat.CombatTargeting;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Optional;
import java.util.UUID;

/** Saved, damageable remains. The eruption happens once; the tendril entity does not time out. */
public final class BiomassHusk extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> DEBRIS=SynchedEntityData.defineId(BiomassHusk.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Long> BURST_AT=SynchedEntityData.defineId(BiomassHusk.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> BURST=SynchedEntityData.defineId(BiomassHusk.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(BiomassHusk.class,EntityDataSerializers.OPTIONAL_UUID);
    public BiomassHusk(EntityType<? extends BiomassHusk> type,Level level){super(type,level);setNoAi(true);setPersistenceRequired();xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return createMobAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(DEBRIS,new CompoundTag());entityData.define(BURST,false);entityData.define(BURST_AT,-100L);entityData.define(OWNER,Optional.empty());}
    private CompoundTag cachedDebris;
    private final net.minecraft.world.level.block.state.BlockState[] blockCache=new net.minecraft.world.level.block.state.BlockState[12];
    public int debrisCount(){return Math.max(0,Math.min(12,entityData.get(DEBRIS).getInt("count")));}
    public net.minecraft.world.level.block.state.BlockState debris(int index){
        var data=entityData.get(DEBRIS);
        if(cachedDebris!=data){cachedDebris=data;java.util.Arrays.fill(blockCache,null);}
        if(blockCache[index]==null)blockCache[index]=net.minecraft.nbt.NbtUtils.readBlockState(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK).asLookup(),data.getCompound("block"+index));
        return blockCache[index];
    }
    public boolean worldTissue(){return entityData.get(DEBRIS).getBoolean("tissueWorld");}
    public net.minecraft.core.BlockPos tissuePosition(int index){return net.minecraft.core.BlockPos.of(entityData.get(DEBRIS).getLong("pos"+index));}
    public UUID owner(){return entityData.get(OWNER).orElse(null);}
    public float eruption(float partial){return (float)Math.max(0,Math.min(1,(level().getGameTime()+partial-entityData.get(BURST_AT))/12D));}
    public boolean erupted(){return entityData.get(BURST);}
    public void initialize(ServerPlayer p){entityData.set(OWNER,Optional.of(p.getUUID()));moveTo(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);yBodyRot=p.yBodyRot;setYHeadRot(p.getYRot());}
    public void burst(ServerPlayer p){
        if(level().isClientSide||erupted()||!isAlive())return;
        entityData.set(BURST,true);entityData.set(BURST_AT,level().getGameTime());
        entityData.set(DEBRIS,com.codex.blacklight.power.BiomassTerrain.erupt(p,this));
        setNoGravity(true);setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level().playSound(null,blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.2F,.7F);
        for(var target:level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,getBoundingBox().inflate(11,6,11),v->v!=this&&!(v instanceof BiomassHusk)&&CombatTargeting.damageableFrom(p,v,this))) {
            if(target.distanceToSqr(this)>121||!hasLineOfSight(target))continue;
            if(target.hurt(damageSources().playerAttack(p),24)){
                var direction=target.position().subtract(position()).normalize();target.push(direction.x*.7,.35,direction.z*.7);
            }
        }
    }
    @Override public void tick(){
        super.tick();
        if(erupted()){setNoGravity(true);setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
        if(level().isClientSide||erupted()||owner()==null)return;
        var p=level().getServer().getPlayerList().getPlayer(owner());
        if(p==null)return; // Unloaded/offline owners do not delete a shell.
        var state=BiomassSystem.state(p);
        if(!state.active()||!state.shell().equals(getUUID().toString())||state.consumed())burst(p);
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("BiomassBurst",erupted());tag.put("BiomassDebris",entityData.get(DEBRIS).copy());if(owner()!=null)tag.putUUID("BiomassOwner",owner());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(BURST,tag.getBoolean("BiomassBurst"));entityData.set(DEBRIS,tag.getCompound("BiomassDebris").copy());setNoGravity(erupted());if(tag.hasUUID("BiomassOwner"))entityData.set(OWNER,Optional.of(tag.getUUID("BiomassOwner")));setNoAi(true);setPersistenceRequired();}
}
