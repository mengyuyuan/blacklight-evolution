package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.EncounterEntities;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Source missile flight with an explicit MC collision/damage adapter.
 * No ThrowableProjectile gravity, water drag, invented blast radius or block damage. */
public final class GreeneProjectile extends Projectile {
    private static final EntityDataAccessor<Integer> AMMO=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3f> FLIGHT_STEP=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> HEADING=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> FLIGHT_SECONDS=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FLESH=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CANNON=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.BOOLEAN);
    public boolean cannon(){return entityData.get(CANNON);}
    public void cannon(boolean value){entityData.set(CANNON,value);}
    public boolean flesh(){return entityData.get(FLESH);}
    public GreeneProjectile asFlesh(){entityData.set(FLESH,true);return this;}
    private static final EntityDataAccessor<Long> FINISH_START=SynchedEntityData.defineId(GreeneProjectile.class,EntityDataSerializers.LONG);
    private GreeneMissileFlight flight;
    private UUID targetId;
    private Vec3 launch;
    private Vec3 damageContact;
    private Vec3 previousHeading=Vec3.ZERO;
    private float previousFlightSeconds;
    // Retains the old rifle's 6 base MC damage at source 100. Native damage and
    // impulse are recorded separately; this factor is not a source unit conversion.
    public static final float LIVING_DAMAGE_ADAPTER=.06F;
    public GreeneProjectile(EntityType<? extends GreeneProjectile> type,Level level){super(type,level);setNoGravity(true);}
    public GreeneProjectile(Level level,EncounterMob owner,GreeneMissileFlight.Ammo ammo,GreeneMissileFlight.User user,Vec3 from,Vec3 direction,Entity target){
        this(EncounterEntities.GREENE_PROJECTILE.get(),level);setOwner(owner);setPos(from);launch=from;targetId=target.getUUID();
        flight=new GreeneMissileFlight(ammo,user,direction,owner.getDeltaMovement().scale(20));entityData.set(AMMO,ammo.ordinal());syncFlight();syncStep(flight.velocity().scale(.05));
    }
    @Override protected void defineSynchedData(){
        entityData.define(CANNON,false);entityData.define(FLESH,false);entityData.define(AMMO,GreeneMissileFlight.Ammo.RIFLE.ordinal());entityData.define(FLIGHT_STEP,new org.joml.Vector3f());
        entityData.define(HEADING,new org.joml.Vector3f(0,0,-1));entityData.define(FLIGHT_SECONDS,0F);entityData.define(FINISH_START,Long.MIN_VALUE);
    }
    public GreeneMissileFlight.Ammo ammo(){int i=entityData.get(AMMO);return GreeneMissileFlight.Ammo.values()[Math.max(0,Math.min(GreeneMissileFlight.Ammo.values().length-1,i))];}
    public Vec3 damageContact(){return damageContact;}
    public double flightSeconds(){return flight==null?0:flight.ageSeconds();}
    public boolean finished(){return entityData.get(FINISH_START)!=Long.MIN_VALUE;}
    public float finishAge(float partial){return finished()?Math.max(0,level().getGameTime()-entityData.get(FINISH_START)+partial):0;}
    public float renderFlightSeconds(float partial){return previousFlightSeconds+(entityData.get(FLIGHT_SECONDS)-previousFlightSeconds)*net.minecraft.util.Mth.clamp(partial,0,1);}
    public Vec3 renderHeading(float partial){
        var value=entityData.get(HEADING);Vec3 current=new Vec3(value.x,value.y,value.z);
        Vec3 direction=previousHeading.lengthSqr()<1e-8?current:previousHeading.lerp(current,net.minecraft.util.Mth.clamp(partial,0,1));
        return direction.lengthSqr()<1e-8?current:direction.normalize();
    }
    private void syncFlight(){
        Vec3 heading=flight.heading();entityData.set(HEADING,new org.joml.Vector3f((float)heading.x,(float)heading.y,(float)heading.z));
        entityData.set(FLIGHT_SECONDS,(float)flight.ageSeconds());
    }
    private void syncStep(Vec3 step){setDeltaMovement(step);entityData.set(FLIGHT_STEP,new org.joml.Vector3f((float)step.x,(float)step.y,(float)step.z));}
    @Override public void tick(){
        previousHeading=renderHeading(1);previousFlightSeconds=entityData.get(FLIGHT_SECONDS);
        super.tick();
        // props_fig final is a non-damaging, stationary presentation state. It
        // survives owner loss and reload without rerunning the impact transaction.
        if(finished()){
            setDeltaMovement(Vec3.ZERO);
            if(!level().isClientSide&&finishAge(0)>=GreeneProjectileAppearance.of(ammo()).finalTicks())discard();
            return;
        }
        if(level().isClientSide){
            // Vanilla velocity packets clamp components to 3.9 blocks/tick; the
            // source anti-vehicle missile reaches 4. Keep client flight in sync.
            var step=entityData.get(FLIGHT_STEP);setDeltaMovement(step.x,step.y,step.z);setPos(position().add(getDeltaMovement()));return;
        }
        if(!(level() instanceof ServerLevel server)||flight==null||launch==null||!(getOwner() instanceof EncounterMob owner)||!owner.isAlive()||owner.isRemoved()
                ||flesh() && (getPersistentData().getBoolean("GoddessHandShot") ? !owner.goddess.attacks.keepsProjectile(getPersistentData().getInt("GoddessSequence")) : !owner.spider.live())||flight.expired()||flight.outOfRange(launch,position())){discard();return;}
        Entity target=targetId==null?null:server.getEntity(targetId);
        if(!GreeneWeaponTarget.valid(owner,target)){flight.loseTarget();target=null;}
        Vec3 from=position(),step=flight.step(from,target==null?null:GreeneWeaponTarget.point(target),target==null?Vec3.ZERO:target.getDeltaMovement().scale(20),.05);
        Vec3 to=from.add(step);double radius=cannon()?.78:flesh()?.23:flight.ammo().thickness;
        var sweep=new AABB(from,to).inflate(radius+.001);
        if(flight.expired()||!Double.isFinite(step.lengthSqr())||step.lengthSqr()>64*64||flight.outOfRange(launch,to)
                ||!VehicleKinematics.loaded(server,sweep)||sweep.minX<server.getWorldBorder().getMinX()||sweep.maxX>server.getWorldBorder().getMaxX()
                ||sweep.minZ<server.getWorldBorder().getMinZ()||sweep.maxZ>server.getWorldBorder().getMaxZ()){discard();return;}
        syncFlight();syncStep(step);
        var hit=firstContact(server,owner,from,to,radius,sweep);
        if(hit!=null){setPos(hit.hit.center());impact(owner,hit);return;}
        setPos(to);
        GreeneProjectileEffects.tick(this);
        if(!flesh() && target!=null&&flight.proximityTriggered(to,target.position())){
            // Native proximity is a trigger against the original target origin,
            // never a radius that damages all nearby actors.
            var contact=proximityContact(server,target,to);
            impact(owner,contact==null?new Contact(null,new ProjectileSphereContact.Hit(1,to,to,step.normalize().scale(-1)),null):contact);
        }
    }
    private record Contact(Entity target,ProjectileSphereContact.Hit hit,HydraHurtContact.Contact body){}
    /** The final FIG contains effects/audio, with no WorldDamage track.
     * MC delivers a proximity hit only to an exposed surface of that same target;
     * it must not hurt a hidden target merely because its origin is nearby. */
    private Contact proximityContact(ServerLevel server,Entity target,Vec3 center){
        if(!(target instanceof LivingEntity living)||living.getRootVehicle()!=living)return null;
        Contact best=null;double nearest=Double.POSITIVE_INFINITY;var body=AnimatedHurtBody.of(living);
        if(body!=null){
            for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                var point=box.nearest(center);double distance=center.distanceToSqr(point);
                if(distance>=nearest||!visibleSurface(server,center,point))continue;
                var normal=center.subtract(point).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
                best=new Contact(target,new ProjectileSphereContact.Hit(1,center,point,normal),new HydraHurtContact.Contact(region.bone(),point,normal,InfectedAttackStyle.PIERCE));nearest=distance;
            }
        }else for(var box:CombatTargeting.hitboxes(living)){
            var point=CombatTargeting.closestPoint(center,box);double distance=center.distanceToSqr(point);
            if(distance>=nearest||!visibleSurface(server,center,point))continue;
            var normal=center.subtract(point).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
            best=new Contact(target,new ProjectileSphereContact.Hit(1,center,point,normal),null);nearest=distance;
        }
        return best;
    }
    private boolean visibleSurface(ServerLevel server,Vec3 from,Vec3 to){
        return VehicleDamageBridge.loaded(server,from,to)&&server.clip(new net.minecraft.world.level.ClipContext(from,to,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private Contact firstContact(ServerLevel server,EncounterMob owner,Vec3 from,Vec3 to,double radius,AABB sweep){
        Contact best=null;
        for(var shape:server.getBlockCollisions(this,sweep))for(var box:shape.toAabbs()){
            var hit=ProjectileSphereContact.sweep(from,to,radius,box);
            if(hit!=null&&(best==null||hit.fraction()<best.hit.fraction()))best=new Contact(null,hit,null);
        }
        var seen=new HashSet<UUID>();
        // Multipart proxies carry their visible limb bounds into the broad query.
        for(var candidate:server.getEntities(this,sweep.inflate(.3),e->!(e instanceof Projectile)&&!e.isRemoved())){
            Entity target=VehicleDamageBridge.vehicle(candidate);
            if(target==null){var living=CombatTargeting.resolveTarget(candidate);target=living==null?candidate:living;}
            if(target==owner||!seen.add(target.getUUID())||!target.isAlive()||target.isSpectator())continue;
            if(target instanceof LivingEntity&&target.getRootVehicle()!=target
                    &&(target.getRootVehicle() instanceof MilitaryVehicle||target.getRootVehicle() instanceof BlackwatchHelicopter))continue;
            if(target instanceof LivingEntity living){
                var body=AnimatedHurtBody.of(living);
                if(body!=null){
                    for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                        var hit=ProjectileSphereContact.sweep(from,to,radius,box);
                        if(nearer(hit,best))best=new Contact(target,hit,new HydraHurtContact.Contact(region.bone(),hit.point(),hit.normal(),InfectedAttackStyle.PIERCE));
                    }
                }else for(var box:CombatTargeting.hitboxes(living)){
                    var hit=ProjectileSphereContact.sweep(from,to,radius,box);if(nearer(hit,best))best=new Contact(target,hit,null);
                }
            }else if(target instanceof MilitaryVehicle hull&&hull.health()>0){
                var geometry=VehicleHullShape.from(hull.definition(),hull.turretYaw(),hull.gunPitch());
                var origin=new VehicleDefinition.Vec3(hull.getX(),hull.getY(),hull.getZ());
                for(var corners:geometry.worldCorners(origin,hull.getYRot())){
                    var points=corners.stream().map(p->new Vec3(p.x(),p.y(),p.z())).toList();
                    var hit=ProjectileSphereContact.sweep(from,to,radius,points);if(nearer(hit,best))best=new Contact(target,hit,null);
                }
            }else if(target instanceof BlackwatchHelicopter h&&h.health()>0){
                // Aircraft currently expose their yaw-dependent body envelope.
                var hit=ProjectileSphereContact.sweep(from,to,radius,h.getBoundingBox());if(nearer(hit,best))best=new Contact(target,hit,null);
            }
        }
        return best;
    }
    private static boolean nearer(ProjectileSphereContact.Hit hit,Contact best){return hit!=null&&(best==null||hit.fraction()<best.hit.fraction()-1e-8);}
    private void impact(EncounterMob owner,Contact contact){
        damageContact=contact.hit.point();
        try{
            var target=contact.target;var ammo=flight.ammo();
            if(target instanceof LivingEntity living&&owner.enemy(living)&&!com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(owner,living)){
                float damage=(float)(living instanceof Player?ammo.damageOnPlayer:ammo.damageOnNpc)*LIVING_DAMAGE_ADAPTER;
                if(flesh())damage*=cannon()?3.7F:1.85F;
                var source=damageSources().thrown(this,owner);var body=AnimatedHurtBody.of(living);
                if(body!=null&&contact.body!=null)body.hurtAt(source,damage,contact.body);
                else if(body==null)living.hurt(source,damage);
            }else if(target instanceof MilitaryVehicle hull){
                // Existing armor adapter owns mechanical scaling; keep source NPC
                // damage here rather than fabricating a firearm definition.
                VehicleDamageBridge.hurtPhysical(owner,hull,(float)ammo.damageOnNpc,this,damageContact);
            }else if(target instanceof BlackwatchHelicopter h&&!VehicleDamageBridge.protectedAircraft(owner,h))h.hurt(damageSources().thrown(this,owner),(float)ammo.damageOnNpc);
            if(cannon() && contact.target==null)GreeneTendrilPod.plant(owner,damageContact);
            GreeneProjectileEffects.impact(this,damageContact);
        }finally{
            damageContact=null;entityData.set(FINISH_START,level().getGameTime());syncStep(Vec3.ZERO);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(cannon())tag.putBoolean("HandCannon",true);if(flesh())tag.putBoolean("FleshVolley",true);if(flight!=null)tag.put("GreeneFlight",flight.save());if(targetId!=null)tag.putUUID("GreeneTarget",targetId);
        if(launch!=null){tag.putDouble("LaunchX",launch.x);tag.putDouble("LaunchY",launch.y);tag.putDouble("LaunchZ",launch.z);}
        if(finished())tag.putInt("GreeneFinalAge",(int)Math.min(6,finishAge(0)));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);cannon(tag.getBoolean("HandCannon"));entityData.set(FLESH,tag.getBoolean("FleshVolley"));flight=GreeneMissileFlight.load(tag.getCompound("GreeneFlight"));
        targetId=tag.hasUUID("GreeneTarget")?tag.getUUID("GreeneTarget"):null;
        launch=tag.contains("LaunchX",6)&&tag.contains("LaunchY",6)&&tag.contains("LaunchZ",6)?new Vec3(tag.getDouble("LaunchX"),tag.getDouble("LaunchY"),tag.getDouble("LaunchZ")):null;
        if(launch!=null&&!Double.isFinite(launch.lengthSqr()))launch=null;
        if(flight!=null){entityData.set(AMMO,flight.ammo().ordinal());syncFlight();previousFlightSeconds=(float)flight.ageSeconds();previousHeading=flight.heading();}
        if(tag.contains("GreeneFinalAge",3))entityData.set(FINISH_START,level().getGameTime()-Math.max(0,Math.min(6,tag.getInt("GreeneFinalAge"))));
        if(flight!=null)syncStep(finished()?Vec3.ZERO:flight.velocity().scale(.05));
    }
}
