package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.entity.PartEntity;

/** Independently hittable armor/tendril surfaces; never separate saved mobs or corpse fragments. */
public final class EncounterPart extends PartEntity<EncounterMob> {
    public final int slot;
    EncounterPart(EncounterMob parent,int slot){super(parent);this.slot=slot;refreshDimensions();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public EntityDimensions getDimensions(Pose pose){return EntityDimensions.scalable(1.5F,2.1F);}
    @Override public boolean isPickable(){return getParent().isAlive()&&!getParent().broken(slot);}
    @Override public boolean isPushable(){return false;}
    @Override public Entity getRootVehicle(){return getParent()==null?this:getParent().getRootVehicle();}
    @Override public boolean hurt(DamageSource source,float amount){return isPickable()&&getParent().hurtPart(slot,source,amount);}
    @Override public boolean is(Entity entity){return entity==this||entity==getParent();}
    @Override public boolean shouldBeSaved(){return false;}
}
