package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Physical, dodgeable bone projectile. Owner attribution preserves the normal damage pipeline. */
public final class EvolvedQuill extends ThrowableItemProjectile {
    private float damage=8;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SPEAR=net.minecraft.network.syncher.SynchedEntityData.defineId(EvolvedQuill.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BARRAGE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EvolvedQuill.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public static final int CROWN_NEEDLE=1, LOW_SURF=2, HIGH_SURF=3;
    public int barrage(){return entityData.get(BARRAGE);}
    public void barrage(int kind){
        entityData.set(BARRAGE,kind);
        if(kind!=0)setNoGravity(true);
    }
    private net.minecraft.core.BlockPos support;
    private int lodgedTicks;
    private net.minecraft.core.Direction anchorFace;
    public boolean lodgedSpear(){return entityData.get(SPEAR)==2;}
    public com.codex.blacklight.combat.HydraContact.Box shaft(){
        // Throwable projectiles store yaw from +Z toward +X and pitch positive upward.
        double yaw=Math.toRadians(getYRot()),pitch=Math.toRadians(getXRot());
        Vec3 right=new Vec3(Math.cos(yaw),0,-Math.sin(yaw));
        Vec3 axis=new Vec3(Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        return com.codex.blacklight.combat.HydraContact.oriented(position().subtract(axis.scale(.98)),
                new Vec3(.20,.20,1.16),right,axis.cross(right),axis);
    }
    private boolean hasAnchor(){
        if(support==null)return false;
        var shape=level().getBlockState(support).getCollisionShape(level(),support);
        if(anchorFace==null)return !shape.isEmpty(); // Spears saved before face data existed.
        Vec3 inward=Vec3.atLowerCornerOf(anchorFace.getNormal()).scale(-.20);
        return shape.clip(position(),position().add(inward),support)!=null;
    }
    public boolean bloodSpear(){return entityData.get(SPEAR)!=0;}
    public void makeBloodSpear(){entityData.set(SPEAR,1);setNoGravity(true);}
    @Override public boolean isPickable(){return bloodSpear() || super.isPickable();}
    @Override public float getPickRadius(){return bloodSpear()?.38F:super.getPickRadius();}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        if(!bloodSpear())return super.hurt(source,amount);
        if(level().isClientSide || amount<=0)return false;
        com.codex.blacklight.combat.SpiderFoley.play(this,position(),com.codex.blacklight.combat.SpiderFoley.Cue.FRACTURE,.85F);
        discard();return true;
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(SPEAR,0);entityData.define(BARRAGE,0);}

    public EvolvedQuill(EntityType<? extends EvolvedQuill> type,Level level){super(type,level);}
    public EvolvedQuill(Level level,LivingEntity owner){super(com.codex.blacklight.registry.ModEntities.EVOLVED_QUILL.get(),owner,level);}
    public void damage(float amount){damage=Float.isFinite(amount)?com.codex.blacklight.forge.WingMath.clamp(amount,0,1000000):0;}
    @Override protected Item getDefaultItem(){return Items.IRON_NUGGET;}
    @Override protected float getGravity(){return .012F;}
    @Override protected boolean canHitEntity(Entity e){if((bloodSpear() || barrage()!=0) && e instanceof EvolvedQuill other && other.getOwner()==getOwner())return false;
        return entityData.get(SPEAR)!=4 && !(getPersistentData().hasUUID("GoddessSpineCaster") && e.getUUID().equals(getPersistentData().getUUID("GoddessSpineCaster")))
            && super.canHitEntity(e)&&e!=getOwner()&&(!(getOwner() instanceof LivingEntity owner)||!owner.isAlliedTo(e));}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(hit.getEntity() instanceof LivingEntity target&&target.hurt(damageSources().thrown(this,getOwner()),damage)){
            var server=(net.minecraft.server.level.ServerLevel)level();server.sendParticles(com.codex.blacklight.registry.ModParticles.BIOMASS_SLASH.get(),target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ(),5,.15,.2,.15,.03);
            // Accepted-hit observer provides the bone/flesh contact sound once.
        }
    }
    @Override protected void onHit(HitResult hit){
        if(bloodSpear() && entityData.get(SPEAR)==1 && hit instanceof BlockHitResult block){
            if(!level().isClientSide){
                if(block.getDirection().getAxis().isVertical()){discard();return;}
                Vec3 flight=getDeltaMovement();
                if(flight.lengthSqr()>.001){
                    setYRot((float)Math.toDegrees(Math.atan2(flight.x,flight.z)));
                    setXRot((float)Math.toDegrees(Math.atan2(flight.y,flight.horizontalDistance())));
                    yRotO=getYRot();xRotO=getXRot();
                }
                anchorFace=block.getDirection();
                support=block.getBlockPos().immutable();lodgedTicks=0;entityData.set(SPEAR,2);
                setPos(block.getLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.12)));
                setDeltaMovement(Vec3.ZERO);hurtMarked=true;
                com.codex.blacklight.combat.SpiderFoley.play(this,position(),com.codex.blacklight.combat.SpiderFoley.Cue.PLANT,.7F);
            }
            return;
        }
        super.onHit(hit);if(!level().isClientSide)discard();
    }
    @Override public void tick(){
        if(getPersistentData().contains("GoddessSequence") && !level().isClientSide &&
            (!(getOwner() instanceof EncounterMob boss) || !boss.goddess.active() || !boss.goddess.damageable() ||
                getPersistentData().getInt("GoddessSequence")!=boss.goddess.attacks.sequence())){discard();return;}
        if(entityData.get(SPEAR)==2){
            if(level().isClientSide)return;
            if(!hasAnchor()){
                entityData.set(SPEAR,4);setNoGravity(false);setDeltaMovement(0,-.12,0);hurtMarked=true;return;
            }
            if(++lodgedTicks==12)com.codex.blacklight.combat.SpiderFoley.play(this,position(),com.codex.blacklight.combat.SpiderFoley.Cue.SCRAPE,.85F);
            if(lodgedTicks<21)return;
            if(!(getOwner() instanceof EncounterMob boss)){discard();return;}
            var destination=boss.hurtRegions(1).get(1).bounds().getCenter();
            var direction=destination.subtract(position()).normalize();entityData.set(SPEAR,3);
            setPos(position().add(direction.scale(.32)));shoot(direction.x,direction.y,direction.z,3.2F,0);hurtMarked=true;
        }
        super.tick();if(level().isClientSide&&tickCount%2==0)level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.55F,.09F,.12F),.65F),getX(),getY(),getZ(),0,0,0);if(!level().isClientSide&&tickCount>65)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("BarrageKind",barrage());tag.putInt("SpearStage",entityData.get(SPEAR));if(anchorFace!=null)tag.putString("SpearFace",anchorFace.getName());tag.putInt("SpearWait",lodgedTicks);if(support!=null)tag.putLong("SpearBlock",support.asLong());tag.putFloat("QuillDamage",damage);tag.putInt("QuillAge",tickCount);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);barrage(tag.getInt("BarrageKind"));entityData.set(SPEAR,tag.getInt("SpearStage"));lodgedTicks=tag.getInt("SpearWait");anchorFace=net.minecraft.core.Direction.byName(tag.getString("SpearFace"));support=tag.contains("SpearBlock")?net.minecraft.core.BlockPos.of(tag.getLong("SpearBlock")):null;damage(tag.getFloat("QuillDamage"));tickCount=com.codex.blacklight.forge.WingMath.clamp(tag.getInt("QuillAge"),0,66);}
}
