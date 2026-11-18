package com.codex.blacklight.firearms;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.military.aircraft.HelicopterBlastWave;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Persistent aircraft explosion timeline; stable entity ID also loads old Hellfire saves. */
public final class HellfireBlastEntity extends Entity {
    private UUID ownerId;
    private HelicopterBlastWave wave;
    private long lastTick;
    public HellfireBlastEntity(EntityType<? extends HellfireBlastEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);setInvisible(true);setSilent(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    public int blastAge(){return wave==null?-1:wave.age();}
    public boolean rapidBlast(){return wave!=null&&wave.rapid();}
    public static HellfireBlastEntity start(ServerLevel level,LivingEntity owner,Vec3 at,Vec3 normal){
        return start(level,owner,at,normal,false);
    }
    public static HellfireBlastEntity startRapid(ServerLevel level,LivingEntity owner,Vec3 at,Vec3 normal){
        return start(level,owner,at,normal,true);
    }
    private static HellfireBlastEntity start(ServerLevel level,LivingEntity owner,Vec3 at,Vec3 normal,boolean rapid){
        if(!VehicleDamageBridge.valid(owner,level)||!Double.isFinite(at.lengthSqr())||!level.hasChunkAt(net.minecraft.core.BlockPos.containing(at)))return null;
        var blast=new HellfireBlastEntity(ModEntities.HELLFIRE_BLAST.get(),level);blast.setPos(at);blast.ownerId=owner.getUUID();blast.wave=new HelicopterBlastWave(rapid);blast.lastTick=level.getGameTime();
        if(!level.addFreshEntity(blast))return null;
        blast.applyWave(level,owner);
        var definition=VehicleWeaponDefinition.require(blast.wave.weaponId()).weapon();
        // Existing Minecraft block-damage permissions and radius remain a separate
        // adaptation; source WorldDamage/impulse units are not inferred from Hit radii.
        if(VehicleKinematics.loaded(level,new AABB(at,at).inflate(definition.radius()+1)))FirearmBlockDamage.blast(level,owner,definition,at);
        GunplayEffects.explosion(level,owner,definition,at,normal);return blast;
    }
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        if(wave==null||ownerId==null||wave.expired()||now<lastTick||now-lastTick>=wave.duration()){discard();return;}
        if(now==lastTick)return;wave.advance((int)(now-lastTick));lastTick=now;
        if(wave.expired()){discard();return;}
        if(wave.stage()==null)return;
        if(!(level.getEntity(ownerId) instanceof LivingEntity owner)||!VehicleDamageBridge.valid(owner,level)){discard();return;}
        applyWave(level,owner);
    }
    private static VehicleDefinition.Vec3 point(Vec3 p){return new VehicleDefinition.Vec3(p.x,p.y,p.z);}
    private boolean exposed(ServerLevel level,Vec3 surface,double radius){
        return position().distanceToSqr(surface)<=radius*radius+1e-8&&VehicleDamageBridge.loaded(level,position(),surface)
                &&level.clip(new ClipContext(position(),surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private void applyWave(ServerLevel level,LivingEntity owner){
        var stage=wave.stage();if(stage==null)return;double radius=stage.radius();
        var definition=VehicleWeaponDefinition.require(wave.weaponId()).weapon();float damage=stage.damage(definition.damage());
        var area=new AABB(position(),position()).inflate(radius);var candidates=new HashSet<UUID>();
        for(var entity:level.getEntities(this,area,e->VehicleDamageBridge.vehicle(e)!=null)){
            var hull=VehicleDamageBridge.vehicle(entity);var id=hull.getUUID();
            if(!candidates.add(id)||wave.contacted(id)||hull.isRemoved()||hull.health()<=0||VehicleDamageBridge.protectedHull(owner,hull))continue;
            var closest=VehicleHullShape.from(hull.definition(),hull.turretYaw(),hull.gunPitch()).closestPoint(point(hull.position()),hull.getYRot(),point(position()));
            var surface=new Vec3(closest.x(),closest.y(),closest.z());if(!exposed(level,surface,radius)||!wave.contact(id))continue;
            VehicleDamageBridge.hurt(owner,hull,definition,damage,this,surface,surface.subtract(position()));
        }
        // Its rotated mechanical body exceeds vanilla's small entity-section query
        // padding. Broaden candidates, then use the actual surface/radius below.
        for(var hull:level.getEntitiesOfClass(BlackwatchHelicopter.class,area.inflate(8),c->c.health()>0&&!c.isRemoved())){
            if(wave.contacted(hull.getUUID())||VehicleDamageBridge.protectedAircraft(owner,hull))continue;
            Vec3 surface=com.codex.blacklight.combat.CombatTargeting.closestPoint(position(),hull.getBoundingBox());
            if(exposed(level,surface,radius)&&wave.contact(hull.getUUID()))hull.hurt(damageSources().explosion(this,owner),damage);
        }
        for(var target:CombatTargeting.candidates(this,area,LivingEntity::isAlive)){
            if(wave.contacted(target.getUUID())||!VehicleOrdnanceProjectile.allowedLiving(level,owner,target))continue;
            var surface=OrdnanceBodyContact.nearest(target,position(),point->exposed(level,point,radius));if(surface==null)continue;
            if(wave.contact(target.getUUID())&&surface.hurt(damageSources().explosion(this,owner),damage)){
                double weight=Math.max(.25,1-position().distanceTo(surface.point())/radius);
                VehicleOrdnanceProjectile.impactImpulse(target,target.getBoundingBox().getCenter().subtract(position()),1.15*weight);
            }
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){if(ownerId!=null)tag.putUUID("BlastOwner",ownerId);if(wave!=null)tag.put("BlastWave",wave.save());tag.putLong("BlastLastTick",lastTick);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        ownerId=tag.hasUUID("BlastOwner")?tag.getUUID("BlastOwner"):null;wave=tag.contains("BlastLastTick",4)?HelicopterBlastWave.load(tag.getCompound("BlastWave")):null;lastTick=tag.getLong("BlastLastTick");
        if(!Double.isFinite(position().lengthSqr()))wave=null;
    }
}
