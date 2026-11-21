package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Spider form from the Greene pursuit. Spawned independently for model review. */
public final class GreeneSpider extends Entity implements GeoEntity {
    public EncounterMob encounter;
    public boolean husk;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LIMB=net.minecraft.network.syncher.SynchedEntityData.defineId(GreeneSpider.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public int fragment(){return entityData.get(LIMB);}
    public static void detach(EncounterMob boss,int leg,net.minecraft.world.phys.Vec3 impulse){
        if(!(boss.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var piece=com.codex.blacklight.registry.EncounterEntities.GREENE_SPIDER.get().create(level);
        if(piece==null)return;
        var local=com.codex.blacklight.combat.SpiderAnatomy.shinCenter(leg);
        var start=com.codex.blacklight.combat.SpiderAnatomy.point(boss,1,local);
        piece.entityData.set(LIMB,leg);piece.noPhysics=false;piece.setNoGravity(false);
        piece.moveTo(start.x,start.y,start.z,boss.skinYaw(1),0);
        piece.setDeltaMovement(impulse.normalize().scale(.41).add(0,.21,0));
        level.addFreshEntity(piece);
    }
    @Override public void tick(){
        super.tick();
        if(fragment()<0 || level().isClientSide)return;
        if(tickCount>80){discard();return;}
        boolean landed=onGround();
        var motion=getDeltaMovement().add(0,-.08,0);
        move(net.minecraft.world.entity.MoverType.SELF,motion);
        setDeltaMovement(motion.multiply(onGround()?.68:.97,onGround()?0:.98,onGround()?.68:.97));
        if(!landed && onGround())playSound(net.minecraft.sounds.SoundEvents.BONE_BLOCK_FALL,1.5F,.57F);
    }
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.greene_spider.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GreeneSpider(EntityType<? extends GreeneSpider> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {entityData.define(LIMB,-1);}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {if(tag.contains("BrokenShin")){entityData.set(LIMB,tag.getInt("BrokenShin"));noPhysics=false;setNoGravity(false);}}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {if(fragment()>=0)tag.putInt("BrokenShin",fragment());}

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // Allow for the complete planted footprint and the height of the cranial web.
    @Override public AABB getBoundingBoxForCulling() {
        return new AABB(getX()-22, getY()-1, getZ()-22, getX()+22, getY()+26, getZ()+22);
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256.0 * 256.0; }
}
