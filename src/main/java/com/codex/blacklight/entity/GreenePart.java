package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.PartEntity;

/** Source-posed boss skin proxies; the legacy name preserves existing ray adapters.
 * Weak regions come first. Every proxy keeps one living parent and is never saved. */
public final class GreenePart extends PartEntity<EncounterMob> {
    public final int slot;
    GreenePart(EncounterMob parent,int slot){super(parent);this.slot=slot;}
    void bounds(AABB now,AABB next){setPos(now.getCenter());setBoundingBox(now.minmax(next).inflate(.04));}
    public Optional<Vec3> clip(Vec3 from,Vec3 to){return getParent().hurtRegions(HydraPicking.partial(this)).get(slot).boxes().stream().map(b->b.clip(from,to)).flatMap(Optional::stream).min(Comparator.comparingDouble(from::distanceToSqr));}
    public boolean contains(Vec3 point){return getParent().hurtRegions(HydraPicking.partial(this)).get(slot).boxes().stream().anyMatch(b->b.contains(point));}
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    public boolean hasSurface(){
        var owner=getParent();
        if(!owner.isAlive() || owner.isRemoved())return false;
        if(owner.kind()==EncounterKind.GOLIATH)
            return !GoliathLockPart.severed(GoliathContact.regionBones().get(slot),owner.goliathBrokenMask());
        return owner.greeneBattle().targetable() && (slot>=owner.kind().weakParts() || !owner.broken(slot));
    }
    @Override public boolean isPickable(){return hasSurface()&&!ConsumeControl.protectedEntity(getParent());}
    @Override public boolean isPushable(){return false;}
    @Override public Entity getRootVehicle(){return getParent()==null?this:getParent().getRootVehicle();}
    @Override public boolean hurt(DamageSource source,float amount){if(!isPickable()||level().isClientSide)return false;var contact=HydraHurtContact.nativeHit(getParent(),slot,source);return contact!=null&&getParent().hurtAt(source,amount,contact);}
    @Override public boolean is(Entity entity){return entity==this||entity==getParent();}
    @Override public boolean shouldBeSaved(){return false;}
}
