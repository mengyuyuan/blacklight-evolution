package com.codex.blacklight.firearms;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import java.util.HashSet;
import java.util.UUID;

/** One bounded impact, optional protected terrain damage, no fire or secondary infection projectiles. */
public final class OrdnanceProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(OrdnanceProjectile.class,EntityDataSerializers.INT);
    private boolean detonated;
    private long impactEpoch;
    public OrdnanceProjectile(EntityType<? extends OrdnanceProjectile> type,Level level){super(type,level);}
    public OrdnanceProjectile(Level level,LivingEntity owner,WeaponDefinition.Kind kind){
        super(com.codex.blacklight.registry.ModEntities.MILITARY_ORDNANCE.get(),owner,level);
        entityData.set(KIND,kind.ordinal());setItem(new ItemStack(FirearmsRegistry.ammo(definition().ammoId())));
        setNoGravity(kind==WeaponDefinition.Kind.ROCKET||kind==WeaponDefinition.Kind.MISSILE);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(KIND,WeaponDefinition.Kind.GRENADE.ordinal());}
    public WeaponDefinition definition(){int index=entityData.get(KIND);return WeaponCatalog.projectile(index>=0&&index<WeaponDefinition.Kind.values().length?WeaponDefinition.Kind.values()[index]:WeaponDefinition.Kind.GRENADE);}
    @Override protected Item getDefaultItem(){return Items.FIRE_CHARGE;}
    @Override protected boolean canHitEntity(Entity entity){return entity!=getOwner()&&super.canHitEntity(entity);}
    @Override protected void onHit(HitResult result){
        if(!(level() instanceof ServerLevel level)){super.onHit(result);return;}
        if(isRemoved()||detonated)return;
        // Commit before block hooks, infection callbacks, hurt/loot callbacks or
        // effects can reenter/save this projectile. A spent save never replays.
        detonated=true;long epoch=impactEpoch;
        Vec3 incomingFrom=position(),incomingDirection=getDeltaMovement();
        Vec3 center=result.getLocation();
        if(result instanceof BlockHitResult block)center=center.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.04));
        setPos(center);
        super.onHit(result);if(!currentImpact(epoch))return;
        var d=definition();LivingEntity owner=getOwner() instanceof LivingEntity living&&living.isAlive()&&!living.isRemoved()&&living.level()==level?living:null;
        if(d.kind()==WeaponDefinition.Kind.INFECTION&&owner instanceof net.minecraft.world.entity.player.Player)com.codex.blacklight.infection.WorldOutbreakSystem.release(level,blockPosition());
        if(!currentImpact(epoch))return;
        var direct=result instanceof EntityHitResult hit?CombatTargeting.resolveTarget(hit.getEntity()):null;
        var directBody=AnimatedHurtBody.of(direct);
        var directContact=directBody==null?null:HydraHurtContact.ray(directBody.hurtRegions(0),incomingFrom,
                center.add(incomingDirection.normalize().scale(.0001)),InfectedAttackStyle.CRUSH);
        final Vec3 blastCenter=center;double radius=d.radius();var receipts=new HashSet<UUID>();
        for(LivingEntity target:CombatTargeting.candidates(this,new AABB(center,center).inflate(radius),LivingEntity::isAlive)){
            if(!currentImpact(epoch)||owner!=null&&!ownsImpact(level,owner))return;
            if(owner==null||!allowedLiving(level,owner,target)||receipts.contains(target.getUUID()))continue;
            // A direct part keeps its already narrowed contact, but still receives
            // only this one ordinary blast debit, never a second direct-hit bonus.
            OrdnanceBodyContact.Surface surface=target==direct&&directContact!=null&&exposed(level,blastCenter,directContact.point(),radius)
                    ?new OrdnanceBodyContact.Surface(target,directContact.point(),directContact)
                    :OrdnanceBodyContact.nearest(target,center,point->exposed(level,blastCenter,point,radius));
            if(surface==null||!receipts.add(target.getUUID()))continue;
            if(d.kind()==WeaponDefinition.Kind.INFECTION){
                if(owner instanceof net.minecraft.world.entity.player.Player)com.codex.blacklight.infection.BlacklightInfectionSystem.infect(target,this);
                else com.codex.blacklight.infection.BlacklightInfectionSystem.infectNaturally(target,this);
            }
            else surface.hurt(damageSources().explosion(this,owner),d.damage()*(float)Math.max(.2,1-center.distanceTo(surface.point())/radius));
        }
        if(!currentImpact(epoch)||owner!=null&&!ownsImpact(level,owner))return;
        // Existing mechanical radius, armor, occupant policy and terrain branch
        // stay separate; this patch changes only living body's blast reception.
        if(owner!=null)VehicleDamageBridge.blast(level,owner,d,center,this,null);
        if(!currentImpact(epoch)||owner!=null&&!ownsImpact(level,owner))return;
        if(owner!=null)FirearmBlockDamage.blast(level,owner,d,center);
        if(!currentImpact(epoch)||owner!=null&&!ownsImpact(level,owner))return;
        GunplayEffects.explosion(level,owner,d,center,result instanceof BlockHitResult block?Vec3.atLowerCornerOf(block.getDirection().getNormal()):new Vec3(0,1,0));
        if(currentImpact(epoch))discard();
    }
    private boolean currentImpact(long epoch){return detonated&&impactEpoch==epoch&&!isRemoved();}
    private boolean ownsImpact(ServerLevel level,LivingEntity owner){return getOwner()==owner&&VehicleDamageBridge.valid(owner,level);}
    private boolean exposed(ServerLevel level,Vec3 center,Vec3 surface,double radius){
        return radius>0&&center.distanceToSqr(surface)<=radius*radius&&VehicleDamageBridge.loaded(level,center,surface)
                &&level.clip(new ClipContext(center,surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private static boolean allowedLiving(ServerLevel level,LivingEntity owner,LivingEntity target){
        if(!target.isAlive()||target.isRemoved()||target.level()!=level||FirearmsCombat.protectedTarget(owner,target))return false;
        return !(owner instanceof net.minecraft.world.entity.player.Player a&&target instanceof net.minecraft.world.entity.player.Player b)
                ||level.getServer().isPvpAllowed()&&a.canHarmPlayer(b);
    }
    @Override public void tick(){
        if(!level().isClientSide&&detonated){discard();return;}
        super.tick();if(level().isClientSide)com.codex.blacklight.firearms.OrdnanceTrail.tick(this,definition().kind());else if(tickCount>100)discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("OrdnanceKind",entityData.get(KIND));tag.putInt("OrdnanceAge",tickCount);tag.putBoolean("OrdnanceDetonated",detonated);}
    @Override public void readAdditionalSaveData(CompoundTag tag){impactEpoch++;super.readAdditionalSaveData(tag);entityData.set(KIND,tag.getInt("OrdnanceKind"));tickCount=Math.max(0,tag.getInt("OrdnanceAge"));detonated=tag.getBoolean("OrdnanceDetonated");}
}

