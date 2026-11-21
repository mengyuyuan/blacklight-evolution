package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Short-lived, damage-free visual. Groundspike damage is calculated once by the server. */
public final class GroundSpikeEntity extends Entity implements GeoEntity {
    public static final int ERUPTION_DURATION_TICKS = 22;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> PLAYER_SHARD =
            SynchedEntityData.defineId(GroundSpikeEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Long> EMERGED =
            SynchedEntityData.defineId(GroundSpikeEntity.class,net.minecraft.network.syncher.EntityDataSerializers.LONG);
    public void setPlayerShard(int size,int riseTicks) {
        entityData.set(PLAYER_SHARD,(riseTicks << 2) | size);
        entityData.set(EMERGED,level().getGameTime());
    }
    public double ageTicks(float partial){
        long start=entityData.get(EMERGED);
        return (start<0?tickCount:Math.max(0,level().getGameTime()-start))+partial;
    }
    public int playerShardSize() { return entityData.get(PLAYER_SHARD) & 3; }
    public int riseTicks() { return entityData.get(PLAYER_SHARD) >> 2; }
    private static final RawAnimation ERUPT = RawAnimation.begin().thenPlay("animation.blacklight.ground_spike.erupt");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public GroundSpikeEntity(EntityType<? extends GroundSpikeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (ageTicks(0) > ERUPTION_DURATION_TICKS) discard();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PLAYER_SHARD,0);
        entityData.define(EMERGED,-1L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(PLAYER_SHARD,tag.getInt("PlayerShard"));
        entityData.set(EMERGED,tag.contains("EmergedAt")?tag.getLong("EmergedAt"):-1L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("PlayerShard",entityData.get(PLAYER_SHARD));
        tag.putLong("EmergedAt",entityData.get(EMERGED));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "erupt", 0, state -> state.setAndContinue(ERUPT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
