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
import com.codex.blacklight.combat.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.military.aircraft.HelicopterMissileFlight;
import java.util.UUID;
import java.util.HashSet;
import java.util.Optional;

/** Vehicle projectiles retain their explicit weapon ID instead of resolving only by Kind. */
public final class VehicleOrdnanceProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<String> WEAPON=SynchedEntityData.defineId(VehicleOrdnanceProjectile.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<org.joml.Vector3f> FACING=SynchedEntityData.defineId(VehicleOrdnanceProjectile.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> FLIGHT_AGE=SynchedEntityData.defineId(VehicleOrdnanceProjectile.class,EntityDataSerializers.INT);
    private UUID originVehicle;
    private Vec3 shotStart;
    private boolean validWeapon;
    private HelicopterMissileFlight flight;
    private com.codex.blacklight.military.aircraft.HelicopterRapidFlight rapidFlight;
    private UUID guidedTarget;
    private Vec3 guidedPoint;
    public VehicleOrdnanceProjectile(EntityType<? extends VehicleOrdnanceProjectile> type,Level level){super(type,level);}
    public VehicleOrdnanceProjectile(Level level,LivingEntity actor,Entity origin,String weapon){
        super(com.codex.blacklight.registry.ModEntities.VEHICLE_ORDNANCE.get(),actor,level);
        var definition=VehicleWeaponDefinition.require(weapon);entityData.set(WEAPON,weapon);validWeapon=true;originVehicle=origin.getUUID();
        Item item=definition.weapon().ammoId().equals("rocket_round")?FirearmsRegistry.ammo("rocket_round"):VehicleRegistry.item(definition.weapon().ammoId());
        setItem(item.getDefaultInstance());setNoGravity(true);
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(WEAPON,"");entityData.define(FACING,new org.joml.Vector3f(0,0,1));entityData.define(FLIGHT_AGE,0);}
    public String weaponId(){return entityData.get(WEAPON);}
    public boolean hellfire(){return weaponId().equals("helicopter_hellfire");}
    public boolean rapidRocket(){return weaponId().equals("helicopter_70mm");}
    public int flightAge(){return entityData.get(FLIGHT_AGE);}
    public Vec3 flightHeading(){var v=entityData.get(FACING);return new Vec3(v.x,v.y,v.z);}
    public boolean trackingLost(){return flight!=null&&flight.lost();}
    public void launchHellfire(Vec3 direction,Vec3 inheritedVelocity,Entity target,Vec3 freeAim,boolean playerProfile){
        if(!hellfire()||flight!=null||!finitePoint(freeAim)||target!=null&&target.level()!=level())throw new IllegalArgumentException("Invalid Hellfire target");
        flight=new HelicopterMissileFlight(direction,inheritedVelocity,playerProfile);guidedTarget=target==null?null:target.getUUID();guidedPoint=freeAim;
        shotStart=position();setDeltaMovement(inheritedVelocity);syncFlight();
    }
    public void launchRapid(Vec3 direction,Vec3 inheritedVelocity){
        if(!rapidRocket()||rapidFlight!=null)throw new IllegalArgumentException("Invalid rapid launch");
        rapidFlight=new com.codex.blacklight.military.aircraft.HelicopterRapidFlight(direction,inheritedVelocity);
        shotStart=position();setDeltaMovement(direction.normalize().scale(7.5).add(inheritedVelocity));syncFlight();
    }
    private void syncFlight(){var heading=flight!=null?flight.heading():rapidFlight.heading();entityData.set(FACING,new org.joml.Vector3f((float)heading.x,(float)heading.y,(float)heading.z));entityData.set(FLIGHT_AGE,flight!=null?flight.age():rapidFlight.age());}
    private static boolean finitePoint(Vec3 p){return p!=null&&Double.isFinite(p.lengthSqr())&&Math.abs(p.x)<=30000100&&Math.abs(p.z)<=30000100&&Math.abs(p.y)<4096;}
    private boolean guidanceAllowed(ServerLevel level,LivingEntity actor,Entity target){
        if(target==null||target.isRemoved()||!target.isAlive()||target.level()!=level||!level.hasChunkAt(target.blockPosition()))return false;
        Entity body=target.getRootVehicle();
        if(body instanceof MilitaryVehicle hull&&(hull.health()<=0||VehicleDamageBridge.protectedHull(actor,hull)))return false;
        if(body instanceof com.codex.blacklight.entity.BlackwatchHelicopter hull&&(hull.health()<=0||VehicleDamageBridge.protectedAircraft(actor,hull)))return false;
        if(target instanceof LivingEntity living){
            if(FirearmsCombat.protectedTarget(actor,living))return false;
            if(actor instanceof com.codex.blacklight.entity.MilitarySoldier soldier&&!VehicleDamageBridge.militaryEnemy(soldier,living))return false;
            if(actor instanceof net.minecraft.world.entity.player.Player a&&living instanceof net.minecraft.world.entity.player.Player b&&(!level.getServer().isPvpAllowed()||!a.canHarmPlayer(b)))return false;
            return true;
        }
        return target instanceof MilitaryVehicle||target instanceof com.codex.blacklight.entity.BlackwatchHelicopter;
    }
    private static Entity guidanceBody(Entity target){var body=target.getRootVehicle();return body instanceof MilitaryVehicle||body instanceof com.codex.blacklight.entity.BlackwatchHelicopter?body:target;}
    private static Vec3 guidancePoint(Entity body){return body instanceof MilitaryVehicle hull?hull.position().add(0,hull.definition().height()*.45,0):body.getBoundingBox().getCenter();}
    @Override protected Item getDefaultItem(){return Items.FIRE_CHARGE;}
    @Override protected boolean canHitEntity(Entity entity){
        if(entity.getUUID().equals(originVehicle)||entity.getVehicle()!=null&&entity.getVehicle().getUUID().equals(originVehicle))return false;
        var vehicle=VehicleDamageBridge.vehicle(entity);
        // Native entity ray queries see the conservative outer AABB. The native tick's
        // collision wrapper adds actual hull contacts back before its impact event.
        if(vehicle!=null)return false;
        if(entity.getRootVehicle() instanceof MilitaryVehicle||entity.getRootVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter&&entity instanceof LivingEntity)return false;
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
            // ProjectileUtil already narrowed animated parts to their visible skin.
            // Re-clipping their broad query box would move the explosion into empty air.
            if(AnimatedHurtBody.of(CombatTargeting.resolveTarget(hit.getEntity()))!=null)nearest=from.distanceTo(hit.getLocation());
            else{
                var clipped=hit.getEntity().getBoundingBox().inflate(.3).clip(from,to);
                if(clipped.isPresent()){ordinary=new EntityHitResult(hit.getEntity(),clipped.get());nearest=from.distanceTo(clipped.get());}
                else nearest=from.distanceTo(ordinary.getLocation());
            }
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
        // Aircraft have a yaw-dependent envelope wider than a vanilla entity query's
        // small search padding. Sweep the native body explicitly, keeping the same
        // first-block/first-actor ordering and the unchanged native impact hook.
        for(var target:level.getEntitiesOfClass(com.codex.blacklight.entity.BlackwatchHelicopter.class,new AABB(sweepFrom,sweepTo).inflate(8),h->h.health()>0&&!h.isRemoved())){
            if(!canHitEntity(target))continue;
            var box=target.getBoundingBox().inflate(half.x,half.y,half.z);
            var clipped=box.contains(sweepFrom)?Optional.of(sweepFrom):box.clip(sweepFrom,sweepTo);
            if(clipped.isEmpty())continue;double distance=sweepFrom.distanceTo(clipped.get());
            if(distance<nearest-1e-6){nearest=distance;ordinary=new EntityHitResult(target,length<1e-9?from:from.lerp(to,distance/length));}
        }
        return ordinary;
    }
    @Override public void tick(){
        Vec3 flightMovement=null;
        if(level() instanceof ServerLevel level){
            if(!validWeapon||!(getOwner() instanceof LivingEntity actor)||!VehicleDamageBridge.valid(actor,level)){discard();return;}
            if(shotStart==null)shotStart=position();
            var definition=VehicleWeaponDefinition.require(weaponId()).weapon();
            if(hellfire()){
                if(flight==null||flight.expired()||distanceToSqr(shotStart)>HelicopterMissileFlight.RANGE*HelicopterMissileFlight.RANGE){discard();return;}
                Vec3 point=guidedPoint,velocity=Vec3.ZERO;
                if(guidedTarget!=null){
                    var target=level.getEntity(guidedTarget);
                    if(!guidanceAllowed(level,actor,target)){flight.loseTarget();point=null;}
                    else{var body=guidanceBody(target);point=guidancePoint(body);velocity=body.getDeltaMovement().scale(20);}
                }
                flightMovement=flight.step(position(),point,velocity);setDeltaMovement(flightMovement);syncFlight();
            }
            if(rapidRocket()){
                if(rapidFlight==null||rapidFlight.expired()){discard();return;}
                flightMovement=rapidFlight.step();setDeltaMovement(flightMovement);syncFlight();
            }
            if(!hellfire()&&!rapidRocket()&&tickCount>100||distanceToSqr(shotStart)>definition.range()*definition.range()||!Double.isFinite(getDeltaMovement().lengthSqr())||getDeltaMovement().lengthSqr()>64*64
                    ||!VehicleKinematics.loaded(level,new AABB(position(),position().add(getDeltaMovement())).inflate(1))){discard();return;}
        }
        super.tick();
        // Native collision/event handling remains intact; its generic .99 drag is
        // replaced by the source flight state's lateral friction on the server.
        if(flightMovement!=null&&!isRemoved())setDeltaMovement(flightMovement);
        if(level().isClientSide){
            if(hellfire()){if(flightAge()>=8)OrdnanceTrail.tick(this,WeaponDefinition.Kind.MISSILE,flightAge()>=10);}
            else OrdnanceTrail.tick(this,rapidRocket()||weaponId().equals("vehicle_rocket")?WeaponDefinition.Kind.ROCKET:WeaponDefinition.Kind.GRENADE);
        }
    }
    @Override protected void onHit(HitResult result){
        super.onHit(result);
        if(!(level() instanceof ServerLevel level)||isRemoved())return;
        if(!(getOwner() instanceof LivingEntity actor)||!VehicleDamageBridge.valid(actor,level)||!validWeapon){discard();return;}
        var specification=VehicleWeaponDefinition.require(weaponId());var definition=specification.weapon();Vec3 center=result.getLocation();
        if(result instanceof BlockHitResult block)center=center.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.04));
        if(hellfire()){
            HellfireBlastEntity.start(level,actor,center,result instanceof BlockHitResult block?Vec3.atLowerCornerOf(block.getDirection().getNormal()):new Vec3(0,1,0));
            discard();return;
        }
        Entity directAircraft=result instanceof EntityHitResult aircraftHit&&aircraftHit.getEntity() instanceof com.codex.blacklight.entity.BlackwatchHelicopter?aircraftHit.getEntity():null;
        if(directAircraft instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft)
            craft.hurt(actor.damageSources().thrown(this,actor),definition.damage());
        MilitaryVehicle direct=result instanceof EntityHitResult hit?VehicleDamageBridge.vehicle(hit.getEntity()):null;
        if(direct!=null)VehicleDamageBridge.hurt(actor,direct,definition,definition.damage(),this,center);
        LivingEntity directLiving=result instanceof EntityHitResult hit?CombatTargeting.resolveTarget(hit.getEntity()):null;
        if(directLiving!=null&&allowedLiving(level,actor,directLiving)){
            var body=AnimatedHurtBody.of(directLiving);
            if(body!=null){
                var contact=HydraHurtContact.ray(body.hurtRegions(0),position(),
                        center.add(getDeltaMovement().normalize().scale(.0001)),com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
                if(contact!=null&&body.hurtAt(damageSources().explosion(this,actor),definition.damage(),contact))
                    impactImpulse(directLiving,getDeltaMovement(),.95);
            }else if(directLiving.hurt(damageSources().explosion(this,actor),definition.damage()))
                impactImpulse(directLiving,getDeltaMovement(),.95);
        }
        if(rapidRocket()){
            // The ordinary missile contact handles source60 first. Its final FIG
            // has no Hit until .433s; CollisionActionBehaviour clears inactive
            // history during that gap. The two later rings share a fresh history.
            // Native hurt immunity/events remain authoritative for each attempt.
            HellfireBlastEntity.startRapid(level,actor,center,result instanceof BlockHitResult block?Vec3.atLowerCornerOf(block.getDirection().getNormal()):new Vec3(0,1,0));
            discard();return;
        }
        double radius=Math.min(4,definition.radius());
        final Vec3 blastCenter=center;
        for(LivingEntity target:CombatTargeting.candidates(this,new AABB(center,center).inflate(radius),LivingEntity::isAlive)){
            if(target==directLiving||!allowedLiving(level,actor,target)||radius<=0)continue;
            var surface=OrdnanceBodyContact.nearest(target,center,point->blastCenter.distanceToSqr(point)<=radius*radius
                    &&VehicleDamageBridge.loaded(level,blastCenter,point)
                    &&level.clip(new ClipContext(blastCenter,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS);
            if(surface==null)continue;
            Vec3 point=surface.point();double distance=center.distanceTo(point);
            double falloff=Math.max(.2,1-distance/Math.max(.1,radius));
            if(surface.hurt(damageSources().explosion(this,actor),definition.damage()*(float)falloff))
                impactImpulse(target,target.getBoundingBox().getCenter().subtract(center),falloff);
        }
        VehicleDamageBridge.blast(level,actor,definition,center,this,directAircraft==null?direct:directAircraft);
        if(VehicleKinematics.loaded(level,new AABB(center,center).inflate(radius+1)))FirearmBlockDamage.blast(level,actor,definition,center);
        GunplayEffects.explosion(level,actor,definition,center,result instanceof BlockHitResult block?Vec3.atLowerCornerOf(block.getDirection().getNormal()):new Vec3(0,1,0));
        discard();
    }
    /** Gameplay impulse for an accepted vehicle hit. Anchored bosses retain their authored reactions. */
    public static void impactImpulse(LivingEntity target,Vec3 incoming,double strength){
        if(CombatTargeting.anchoredBody(target)||target.isPassenger())return;
        var horizontal=incoming.normalize().multiply(1,0,1);
        double force=strength*(1-target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        if(force<=0)return;
        target.push(horizontal.x*force,.32*force,horizontal.z*force);
        target.hurtMarked=true;
    }
    static boolean allowedLiving(ServerLevel level,LivingEntity actor,LivingEntity target){
        if(!target.isAlive()||target.isRemoved()||target.level()!=level||target.getRootVehicle() instanceof MilitaryVehicle||target.getRootVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter||FirearmsCombat.protectedTarget(actor,target))return false;
        return !(actor instanceof net.minecraft.world.entity.player.Player a&&target instanceof net.minecraft.world.entity.player.Player b)
                ||level.getServer().isPvpAllowed()&&a.canHarmPlayer(b);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putString("VehicleWeapon",weaponId());if(originVehicle!=null)tag.putUUID("VehicleOrigin",originVehicle);
        var start=shotStart==null?position():shotStart;tag.putDouble("VehicleShotX",start.x);tag.putDouble("VehicleShotY",start.y);tag.putDouble("VehicleShotZ",start.z);tag.putInt("VehicleShotAge",tickCount);
        if(flight!=null){tag.put("HellfireFlight",flight.save());if(guidedTarget!=null)tag.putUUID("HellfireTarget",guidedTarget);tag.putDouble("HellfirePointX",guidedPoint.x);tag.putDouble("HellfirePointY",guidedPoint.y);tag.putDouble("HellfirePointZ",guidedPoint.z);}
        if(rapidFlight!=null)tag.put("RapidFlight",rapidFlight.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);String id=tag.getString("VehicleWeapon");
        try{VehicleWeaponDefinition.require(id);entityData.set(WEAPON,id);validWeapon=tag.hasUUID("VehicleOrigin")&&tag.contains("VehicleShotX")&&tag.contains("VehicleShotY")&&tag.contains("VehicleShotZ");}
        catch(IllegalArgumentException invalid){validWeapon=false;}
        originVehicle=tag.hasUUID("VehicleOrigin")?tag.getUUID("VehicleOrigin"):null;
        shotStart=new Vec3(tag.getDouble("VehicleShotX"),tag.getDouble("VehicleShotY"),tag.getDouble("VehicleShotZ"));
        validWeapon&=Double.isFinite(shotStart.lengthSqr());tickCount=Math.max(0,Math.min(hellfire()?201:rapidRocket()?301:101,tag.getInt("VehicleShotAge")));setNoGravity(true);
        flight=null;rapidFlight=null;guidedTarget=null;guidedPoint=null;
        if(hellfire()){
            flight=HelicopterMissileFlight.load(tag.getCompound("HellfireFlight"));guidedTarget=tag.hasUUID("HellfireTarget")?tag.getUUID("HellfireTarget"):null;
            guidedPoint=new Vec3(tag.getDouble("HellfirePointX"),tag.getDouble("HellfirePointY"),tag.getDouble("HellfirePointZ"));
            validWeapon&=flight!=null&&tag.contains("HellfirePointX",6)&&tag.contains("HellfirePointY",6)&&tag.contains("HellfirePointZ",6)&&finitePoint(guidedPoint);
            if(flight!=null)syncFlight();
        }
        if(rapidRocket()){rapidFlight=com.codex.blacklight.military.aircraft.HelicopterRapidFlight.load(tag.getCompound("RapidFlight"));validWeapon&=rapidFlight!=null;if(rapidFlight!=null)syncFlight();}
    }
}
