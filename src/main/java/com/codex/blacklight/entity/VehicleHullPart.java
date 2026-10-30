package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Native multipart query proxy; health, resources, passengers and saves belong only to its parent. */
public final class VehicleHullPart extends net.neoforged.neoforge.entity.PartEntity<MilitaryVehicle> {
    private final MilitaryVehicle vehicle;
    VehicleHullPart(MilitaryVehicle parent){super(parent);vehicle=parent;followParent();}
    // Broad-phase coverage includes the barrel; EntityGetter substitutes the parent's contact shape.
    void followParent(){setPos(vehicle.getX(),vehicle.getY(),vehicle.getZ());setBoundingBox(vehicle.getBoundingBox());}
    @Override public boolean canCollideWith(Entity other){return !other.is(vehicle)&&!other.isPassengerOfSameVehicle(vehicle)&&super.canCollideWith(other);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isPickable(){return !vehicle.isRemoved();}
    @Override public boolean canBeCollidedWith(){return !vehicle.isRemoved();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){return vehicle.hurt(source,amount);}
    @Override public boolean is(Entity other){return this==other||vehicle==other;}
    @Override public InteractionResult interact(Player player,InteractionHand hand){return vehicle.interact(player,hand);}
    @Override public boolean shouldBeSaved(){return false;}
}
