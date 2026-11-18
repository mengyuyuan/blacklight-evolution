package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Independent P1 segment death presentation. It never follows a living
 * Mother's later collapse/sink, emits loot/XP, or becomes a new damage target.
 * The authored Slug curve retracts it; no invented ballistic ragdoll is added. */
public final class GreeneSegmentCorpse extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Integer> SEGMENT=SynchedEntityData.defineId(GreeneSegmentCorpse.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(GreeneSegmentCorpse.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> COPIED=SynchedEntityData.defineId(GreeneSegmentCorpse.class,EntityDataSerializers.COMPOUND_TAG);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private CompoundTag cachedTag;
    private float[][] copied;
    private int age;
    private long lastTick=Long.MIN_VALUE;

    public GreeneSegmentCorpse(EntityType<? extends GreeneSegmentCorpse> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public GreeneSegmentCorpse(EncounterMob mother,int segment){
        this(EncounterEntities.GREENE_SEGMENT_CORPSE.get(),mother.level());
        if(segment<0||segment>2)throw new IllegalArgumentException("Invalid Greene segment");
        entityData.set(SEGMENT,segment);entityData.set(COPIED,GreeneSegmentDeath.savePose(GreeneMotion.pose(mother,0)));
        setPos(mother.position());setYRot(mother.yBodyRot);setXRot(0);
        lastTick=level().getGameTime();entityData.set(STARTED,lastTick);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(SEGMENT,-1);b.define(STARTED,0L);b.define(COPIED,new CompoundTag());}
    public int segment(){return entityData.get(SEGMENT);}
    private float[][] copied(){
        var tag=entityData.get(COPIED);
        if(cachedTag!=tag){cachedTag=tag;copied=GreeneSegmentDeath.loadPose(tag);}
        return copied;
    }
    public boolean validPresentation(){return segment()>=0&&segment()<3&&copied()!=null;}
    public float deathAge(float partial){return Math.max(0,Math.min(GreeneSegmentDeath.ticks(),level().isClientSide?level().getGameTime()-entityData.get(STARTED)+partial:age+partial));}
    public float[][] pose(float partial){return GreeneSegmentDeath.pose(copied(),deathAge(partial));}
    @Override public void tick(){
        super.tick();setDeltaMovement(Vec3.ZERO);fallDistance=0;
        if(level().isClientSide)return;
        if(!validPresentation()){discard();return;}
        long now=level().getGameTime();if(lastTick==now)return;lastTick=now;
        if(++age>=GreeneSegmentDeath.ticks())discard();
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    // The noncolliding .1-block registration box is not the six-block Slug
    // skin. Retain the same sixteen-chunk visual range as its tracked parent.
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<256D*256D;}
    @Override public AABB getBoundingBoxForCulling(){
        if(!validPresentation())return super.getBoundingBoxForCulling();
        var before=GreeneContact.regions(pose(0),position(),getYRot(),0).get(segment()).bounds();
        var after=GreeneContact.regions(pose(1),position(),getYRot(),0).get(segment()).bounds();
        return before.minmax(after).inflate(.05);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putInt("GreeneSegment",segment());tag.putInt("DeathAge",age);tag.put("CopiedPose",entityData.get(COPIED).copy());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        int slot=tag.getInt("GreeneSegment");entityData.set(SEGMENT,tag.contains("GreeneSegment",3)&&slot>=0&&slot<3?slot:-1);
        int savedAge=tag.getInt("DeathAge");age=tag.contains("DeathAge",3)&&savedAge>=0&&savedAge<=GreeneSegmentDeath.ticks()?savedAge:GreeneSegmentDeath.ticks();
        entityData.set(COPIED,tag.getCompound("CopiedPose").copy());cachedTag=null;copied=null;
        lastTick=level().getGameTime();entityData.set(STARTED,lastTick-age);noPhysics=true;setNoGravity(true);setDeltaMovement(Vec3.ZERO);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
