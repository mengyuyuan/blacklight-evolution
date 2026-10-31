package com.codex.blacklight.firearms;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import java.util.UUID;
import java.util.HashSet;

/** Vehicle projectiles retain their explicit weapon ID instead of resolving only by Kind. */
public final class VehicleOrdnanceProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<String> WEAPON=SynchedEntityData.defineId(VehicleOrdnanceProjectile.class,EntityDataSerializers.STRING);
    private UUID originVehicle;
    private Vec3 shotStart;
    private boolean validWeapon;
    public VehicleOrdnanceProjectile(EntityType<? extends VehicleOrdnanceProjectile> type,Level level){super(type,level);}
    public VehicleOrdnanceProjectile(Level level,LivingEntity actor,MilitaryVehicle origin,String weapon){
        super(com.codex.blacklight.registry.ModEntities.VEHICLE_ORDNANCE.get(),actor,level);
        var definition=VehicleWeaponDefinition.require(weapon);entityData.set(WEAPON,weapon);validWeapon=true;originVehicle=origin.getUUID();
        Item item=definition.weapon().ammoId().equals("rocket_round")?FirearmsRegistry.ammo("rocket_round"):VehicleRegistry.item(definition.weapon().ammoId());
        setItem(item.getDefaultInstance());setNoGravity(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(WEAPON,"");}
    public String weaponId(){return entityData.get(WEAPON);}
    @Override protected Item getDefaultItem(){return Items.FIRE_CHARGE;}
    @Override protected boolean canHitEntity(Entity entity){
        var vehicle=VehicleDamageBridge.vehicle(entity);
        // Native entity ray queries see the conservative outer AABB. The native tick's
        // collision wrapper adds actual hull contacts back before its impact event.
        if(vehicle!=null)return false;
        if(entity.getVehicle() instanceof MilitaryVehicle)return false;
        return entity!=getOwner()&&super.canHitEntity(entity);
    }
    private static VehicleDefinition.Vec3 point(Vec3 value){return new VehicleDefinition.Vec3(value.x,value.y,value.z);}
    /** Called only at the native ThrowableProjectile collision-query site, before its unchanged impact hook. */
    public HitResult resolveActualHullImpact(HitResult ordinary){
        if(!(level() instanceof ServerLevel level))return ordinary;
        Vec3 from=position(),to=from.add(getDeltaMovement());
        if(!VehicleKinematics.loaded(level,new AABB(from,to).inflate(1)))return ordinary;
        double nearest=Double.POSITIVE_INFINITY;
        if(ordinary instanceof EntityHitResult hit){
            var clipped=hit.getEntity().getBoundingBox().inflate(.3).clip(from,to);
            if(clipped.isPresent()){ordinary=new EntityHitResult(hit.getEntity(),clipped.get());nearest=from.distanceTo(clipped.get());}
            else nearest=from.distanceTo(ordinary.getLocation());
        }else if(ordinary.getType()!=HitResult.Type.MISS)nearest=from.distanceTo(ordinary.getLocation());
        // Preserve a real block as the first contact when an inflated ordinary actor touches that same wall.
        var wall=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS&&from.distanceTo(wall.getLocation())<=nearest+1e-6){ordinary=wall;nearest=from.distanceTo(wall.getLocation());}
        var seen=new HashSet<UUID>();double length=from.distanceTo(to);
        Vec3 half=new Vec3(getBbWidth()*.5,getBbHeight()*.5,getBbWidth()*.5);
        Vec3 sweepFrom=getBoundingBox().getCenter(),sweepTo=sweepFrom.add(getDeltaMovement());
        for(Entity candidate:level.getEntities(this,new AABB(from,to).inflate(1),entity->VehicleDamageBridge.vehicle(entity)!=null)){
            var target=VehicleDamageBridge.vehicle(candidate);
            if(target.getUUID().equals(originVehicle)||target.isRemoved()||target.health()<=0||!seen.add(target.getUUID()))continue;
            var shape=VehicleHullShape.from(target.definition(),target.turretYaw(),target.gunPitch());
            var contact=shape.sweepBox(point(target.position()),target.getYRot(),point(sweepFrom),point(sweepTo),point(half));
            if(contact.isPresent()&&contact.getAsDouble()<nearest-1e-6){
                nearest=contact.getAsDouble();Vec3 impact=length<1e-9?from:from.lerp(to,nearest/length);
                ordinary=new EntityHitResult(target,impact);
            }
        }
        return ordinary;
    }
    @Override public void tick(){
        if(level() instanceof ServerLevel level){
            if(!validWeapon||!(getOwner() instanceof LivingEntity actor)||!VehicleDamageBridge.valid(actor,level)){discard();return;}
            if(shotStart==null)shotStart=position();
            var definition=VehicleWeaponDefinition.require(weaponId()).weapon();
            if(tickCount>100||distanceToSqr(shotStart)>definition.range()*definition.range()||!Double.isFinite(getDeltaMovement().lengthSqr())||getDeltaMovement().lengthSqr()>64*64
                    ||!VehicleKinematics.loaded(level,new AABB(position(),position().add(getDeltaMovement())).inflate(1))){discard();return;}
        }
        super.tick();
    }
    @Override protected void onHit(HitResult result){
        super.onHit(result);
        if(!(level() instanceof ServerLevel level)||isRemoved())return;
        if(!(getOwner() instanceof LivingEntity actor)||!VehicleDamageBridge.valid(actor,level)||!validWeapon){discard();return;}
        var specification=VehicleWeaponDefinition.require(weaponId());var definition=specification.weapon();Vec3 center=result.getLocation();
        if(result instanceof BlockHitResult block)center=center.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.04));
        MilitaryVehicle direct=result instanceof EntityHitResult hit?VehicleDamageBridge.vehicle(hit.getEntity()):null;
        if(direct!=null)VehicleDamageBridge.hurt(actor,direct,definition,definition.damage(),this);
        LivingEntity directLiving=result instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target?target:null;
        if(directLiving!=null&&allowedLiving(level,actor,directLiving))directLiving.hurt(damageSources().explosion(this,actor),definition.damage());
        double radius=Math.min(4,definition.radius());
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),LivingEntity::isAlive)){
            if(target==directLiving||!allowedLiving(level,actor,target)||radius<=0)continue;
            Vec3 point=com.codex.blacklight.combat.CombatTargeting.closestPoint(center,target.getBoundingBox());double distance=center.distanceTo(point);
            if(distance>radius||!VehicleDamageBridge.loaded(level,center,point))continue;
            if(level.clip(new ClipContext(center,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            target.hurt(damageSources().explosion(this,actor),definition.damage()*(float)Math.max(.2,1-distance/Math.max(.1,radius)));
        }
        VehicleDamageBridge.blast(level,actor,definition,center,this,direct);
        if(VehicleKinematics.loaded(level,new AABB(center,center).inflate(radius+1)))FirearmBlockDamage.blast(level,actor,definition,center);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,center.x,center.y,center.z,2,.2,.2,.2,0);
        level.playSound(null,net.minecraft.core.BlockPos.containing(center),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.HOSTILE,1,.8F);
        discard();
    }
    private static boolean allowedLiving(ServerLevel level,LivingEntity actor,LivingEntity target){
        if(!target.isAlive()||target.isRemoved()||target.level()!=level||target.getVehicle() instanceof MilitaryVehicle||FirearmsCombat.protectedTarget(actor,target))return false;
        return !(actor instanceof net.minecraft.world.entity.player.Player a&&target instanceof net.minecraft.world.entity.player.Player b)
                ||level.getServer().isPvpAllowed()&&a.canHarmPlayer(b);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putString("VehicleWeapon",weaponId());if(originVehicle!=null)tag.putUUID("VehicleOrigin",originVehicle);
        var start=shotStart==null?position():shotStart;tag.putDouble("VehicleShotX",start.x);tag.putDouble("VehicleShotY",start.y);tag.putDouble("VehicleShotZ",start.z);tag.putInt("VehicleShotAge",tickCount);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);String id=tag.getString("VehicleWeapon");
        try{VehicleWeaponDefinition.require(id);entityData.set(WEAPON,id);validWeapon=tag.hasUUID("VehicleOrigin")&&tag.contains("VehicleShotX")&&tag.contains("VehicleShotY")&&tag.contains("VehicleShotZ");}
        catch(IllegalArgumentException invalid){validWeapon=false;}
        originVehicle=tag.hasUUID("VehicleOrigin")?tag.getUUID("VehicleOrigin"):null;
        shotStart=new Vec3(tag.getDouble("VehicleShotX"),tag.getDouble("VehicleShotY"),tag.getDouble("VehicleShotZ"));
        validWeapon&=Double.isFinite(shotStart.lengthSqr());tickCount=Math.max(0,Math.min(101,tag.getInt("VehicleShotAge")));setNoGravity(true);
    }
}
