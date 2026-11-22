package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Spider form from the Greene pursuit. Spawned independently for model review. */
public final class GreeneSpider extends Entity implements GeoEntity {
    public EncounterMob encounter;
    public boolean husk;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LIMB=SynchedEntityData.defineId(GreeneSpider.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<CompoundTag> RELEASE =
            SynchedEntityData.defineId(GreeneSpider.class,net.minecraft.network.syncher.EntityDataSerializers.COMPOUND_TAG);
    public org.joml.Quaternionf fragmentPose(float partial){
        var s=entityData.get(RELEASE);
        if(!s.contains("Start"))return new org.joml.Quaternionf().rotateY((float)Math.toRadians(180-getYRot()));
        double t=level().getGameTime()-s.getLong("Start")+partial;
        double stop=s.contains("Land")?s.getLong("Land")-s.getLong("Start"):t;
        var q=new org.joml.Quaternionf(s.getFloat("X"),s.getFloat("Y"),s.getFloat("Z"),s.getFloat("W"));
        float spin=(float)Math.min(1.9,.061*Math.max(0,Math.min(t,stop)));
        q.premul(new org.joml.Quaternionf().rotationAxis(spin,s.getFloat("Ax"),s.getFloat("Ay"),s.getFloat("Az")));
        if(s.contains("Land")){
            int leg=fragment();String shin=com.codex.blacklight.combat.SpiderAnatomy.shin(leg);
            var longAxis=com.codex.blacklight.combat.SpiderAnatomy.TIPS.get(shin).subtract(com.codex.blacklight.combat.SpiderAnatomy.PIVOTS.get(shin)).normalize().toVector3f();
            q.transform(longAxis);var flat=new org.joml.Vector3f(longAxis.x,0,longAxis.z);if(flat.lengthSquared()<.0001)flat.set(1,0,0);flat.normalize();
            var lay=new org.joml.Quaternionf().rotationTo(longAxis,flat).mul(q);
            float settle=net.minecraft.util.Mth.clamp((float)(t-stop)/6,0,1);q.slerp(lay,settle*settle*(3-2*settle));
        }
        return q;
    }
    public int fragment(){return entityData.get(LIMB);}
    public static void detach(EncounterMob boss,int leg,net.minecraft.world.phys.Vec3 impulse){
        if(!(boss.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var piece=com.codex.blacklight.registry.EncounterEntities.GREENE_SPIDER.get().create(level);
        if(piece==null)return;
        var local=com.codex.blacklight.combat.SpiderAnatomy.shinCenter(leg);
        var joint=com.codex.blacklight.combat.SpiderAnatomy.shin(leg);
        var frozen=boss.spider.jointMatrix(com.codex.blacklight.combat.SpiderAnatomy.root(boss,1),joint,1);
        var start=boss.position().add(new net.minecraft.world.phys.Vec3(frozen.transformPosition(local.toVector3f())));
        var q=frozen.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
        var axis=impulse.cross(new net.minecraft.world.phys.Vec3(0,1,0)).normalize();
        if(axis.lengthSqr()<.001)axis=new net.minecraft.world.phys.Vec3(0,0,leg<4?1:-1);
        var released=new CompoundTag();released.putLong("Start",level.getGameTime());
        released.putFloat("X",q.x);released.putFloat("Y",q.y);released.putFloat("Z",q.z);released.putFloat("W",q.w);
        released.putFloat("Ax",(float)axis.x);released.putFloat("Ay",(float)axis.y);released.putFloat("Az",(float)axis.z);
        piece.entityData.set(RELEASE,released);
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
        if(!landed && onGround() && !entityData.get(RELEASE).contains("Land")){
            var st=entityData.get(RELEASE).copy();st.putLong("Land",level().getGameTime());entityData.set(RELEASE,st);
            com.codex.blacklight.combat.SpiderFoley.play(this,position(),com.codex.blacklight.combat.SpiderFoley.Cue.PLANT,1.35F);
        }
    }
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.greene_spider.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GreeneSpider(EntityType<? extends GreeneSpider> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {builder.define(LIMB,-1);builder.define(RELEASE,new CompoundTag());}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {if(tag.contains("BrokenShin")){entityData.set(LIMB,tag.getInt("BrokenShin"));entityData.set(RELEASE,tag.getCompound("Release"));noPhysics=false;setNoGravity(false);}}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {if(fragment()>=0){tag.putInt("BrokenShin",fragment());tag.put("Release",entityData.get(RELEASE).copy());}}

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
