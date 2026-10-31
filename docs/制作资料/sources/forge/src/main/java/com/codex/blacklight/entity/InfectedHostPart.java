package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/** Damage surfaces, not solid terrain collision or separately saved entities. */
public final class InfectedHostPart extends PartEntity<BlacklightInfectedHost> {
    private EntityDimensions size=EntityDimensions.scalable(.01F,.01F);
    private boolean active;
    private String partName="unused";

    InfectedHostPart(BlacklightInfectedHost parent) { super(parent); }
    void configure(InfectedBossProfile.Part part,float scale) {
        active=part!=null;
        partName=active?part.name():"unused";
        size=active?EntityDimensions.scalable(part.width()*scale,part.height()*scale):EntityDimensions.scalable(.01F,.01F);
        refreshDimensions();
    }
    public boolean active() { return active; }
    public String partName() { return partName; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public EntityDimensions getDimensions(Pose pose) { return size==null?EntityDimensions.scalable(.01F,.01F):size; }
    @Override public boolean isPickable() { return active&&getParent().isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public Entity getRootVehicle() { return getParent()==null?this:getParent().getRootVehicle(); }
    @Override public boolean hurt(DamageSource source,float amount) { return isPickable()&&getParent().hurt(source,amount); }
    @Override public boolean is(Entity entity) { return entity==this||entity==getParent(); }
    @Override public boolean shouldBeSaved() { return false; }
}
