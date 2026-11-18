package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.PartEntity;

/** Query proxies for an animated rooted body. Health, saves and drops stay on the parent. */
public final class HydraPart extends PartEntity<BlacklightHydra> {
    public final int slot;
    HydraPart(BlacklightHydra parent,int slot){super(parent);this.slot=slot;}
    void bounds(AABB now,AABB next){setPos(now.getCenter());setBoundingBox(now.minmax(next).inflate(.04));}
    public Optional<Vec3> clip(Vec3 from,Vec3 to){
        return getParent().hurtRegions(HydraPicking.partial(this)).get(slot).boxes().stream().map(b->b.clip(from,to))
                .flatMap(Optional::stream).min(Comparator.comparingDouble(from::distanceToSqr));
    }
    public boolean contains(Vec3 point){return getParent().hurtRegions(HydraPicking.partial(this)).get(slot).boxes().stream().anyMatch(b->b.contains(point));}
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isPickable(){return getParent().isAlive()&&!getParent().isRemoved()&&!getParent().isInvisible();}
    @Override public boolean isPushable(){return false;}
    @Override public Entity getRootVehicle(){return getParent()==null?this:getParent().getRootVehicle();}
    @Override public boolean hurt(DamageSource source,float amount){return isPickable()&&getParent().hurtPart(slot,source,amount);}
    @Override public boolean is(Entity e){return e==this||e==getParent();}
    @Override public boolean shouldBeSaved(){return false;}
}
