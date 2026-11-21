package com.codex.blacklight.entity;

import com.codex.blacklight.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import com.codex.blacklight.combat.QuillWedge;
import com.codex.blacklight.combat.QuillHarvest;

/** Each detached quill travels and collides with the world independently. */
public final class BackspineQuill extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> HEAVY=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.BOOLEAN);
    private final Vec3[] passed=new Vec3[4];
    public Vec3 pastPosition(int index){return passed[index];}
    private static final EntityDataAccessor<Integer> VOLLEY_SLOT=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROOF_INDEX=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.INT);
    public int roofIndex(){return entityData.get(ROOF_INDEX);}
    public void setRoofIndex(int index){entityData.set(ROOF_INDEX,Math.max(0,Math.min(5,index)));}
    public int volleySlot(){return entityData.get(VOLLEY_SLOT);}
    public void setVolleySlot(int slot){entityData.set(VOLLEY_SLOT,Math.max(-2,Math.min(2,slot)));}
    public boolean heavy(){return entityData.get(HEAVY);}
    public void setHeavy(boolean value){entityData.set(HEAVY,value);}
    private static final EntityDataAccessor<Integer> SIDE=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3f> TRAVEL=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> BOW=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SPAN=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<org.joml.Quaternionf> RELEASE=SynchedEntityData.defineId(BackspineQuill.class,EntityDataSerializers.QUATERNION);
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        super.defineSynchedData(b);
        b.define(SIDE,0);
        b.define(HEAVY,false);
        b.define(VOLLEY_SLOT,0);
        b.define(ROOF_INDEX,0);
        b.define(TRAVEL,new org.joml.Vector3f());
        b.define(BOW,new org.joml.Vector3f());
        b.define(SPAN,0);
        b.define(START,-1L);
        b.define(RELEASE,new org.joml.Quaternionf());
    }
    public int side(){return entityData.get(SIDE);}
    public org.joml.Quaternionf releaseOrientation(){return new org.joml.Quaternionf(entityData.get(RELEASE));}
    private static final int CLEAVE_LIMIT=3;
    private java.util.Set<java.util.UUID> cutTargets;
    public void enableCleaving(){cutTargets=new java.util.LinkedHashSet<>();}
    private static java.util.UUID bodyId(Entity target){
        var body=com.codex.blacklight.combat.CombatTargeting.resolveTarget(target);
        return body==null?target.getUUID():body.getUUID();
    }
    private float damage=4;
    private java.util.UUID aimTarget, volleyId;
    public java.util.UUID volleyId(){return volleyId;}
    public void setVolleyId(java.util.UUID id){volleyId=id;}
    public java.util.UUID aimTarget(){return aimTarget;}
    public void setAimTarget(java.util.UUID target){aimTarget=target;}
    private boolean canWedge, canPierceWedge, piercingWedge;
    private LivingEntity wedgedTarget;
    public void setWedgeSkills(boolean wedge, boolean pierce) {
        canWedge = wedge;
        canPierceWedge = wedge && pierce;
    }
    public boolean reducesArmorOf(LivingEntity victim) { return wedgedTarget == victim; }
    private int cleaveLimit() { return piercingWedge ? 2 : CLEAVE_LIMIT; }

    public BackspineQuill(EntityType<? extends BackspineQuill> type,Level level){super(type,level);}
    public BackspineQuill(Level level,Player owner,float damage,int side){
        super(ModEntities.BACKSPINE_QUILL.get(),owner,level);this.damage=damage;entityData.set(SIDE,side);
    }
    @Override protected Item getDefaultItem(){return Items.BONE;}
    @Override protected double getDefaultGravity(){return .008;}
    @Override protected boolean canHitEntity(Entity other){
        if(QuillHarvest.protectedPet(other))return false;
        var owner=getOwner();
        if(owner!=null&&(owner==other||owner.isAlliedTo(other)))return false;
        if(owner instanceof Player source&&other instanceof Player target&&!source.canHarmPlayer(target))return false;
        return (cutTargets==null||!cutTargets.contains(bodyId(other)))&&super.canHitEntity(other);
    }
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);
        if(level().isClientSide)return;
        var receiver=hit.getEntity();
        var living=com.codex.blacklight.combat.CombatTargeting.resolveTarget(receiver);
        int immunity=living==null?0:living.invulnerableTime;
        var previous=living==null?null:living.getLastDamageSource();
        boolean ownVolley=immunity>0&&previous!=null&&previous.getDirectEntity() instanceof BackspineQuill other
                &&getOwner()!=null&&other.getOwner()==getOwner();
        var source = damageSources().thrown(this, getOwner());
        var direction = getDeltaMovement().normalize();
        var box = receiver.getBoundingBox();
        // ProjectileUtil can report the entity origin. Preserve the actual flight line for a local wound.
        var from = position().subtract(direction.scale(.001));
        var end = position().add(getDeltaMovement()).add(direction.scale(.30));
        var intersection = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, end);
        var entry = intersection.orElseGet(() -> box.clip(from, box.getCenter()).orElse(box.getCenter()));
        var owner = getOwner();
        long now = level().getGameTime();
        var site = canWedge && owner != null && living != null && intersection.isPresent() && volleySlot() == 0 && !piercingWedge
                ? QuillWedge.site(receiver, living, from, end, entry) : null;
        boolean wedge = heavy() && site != null && QuillWedge.matches(receiver, owner.getUUID(), site, now);
        boolean harvestable=owner instanceof net.minecraft.server.level.ServerPlayer p
                && com.codex.blacklight.combat.QuillHarvest.canHarvestImpact(p,living);
        if (ownVolley) living.invulnerableTime = 0;
        boolean accepted = false;
        wedgedTarget = wedge ? living : null;
        try {
            accepted = receiver.hurt(source, damage);
        } finally {
            wedgedTarget = null;
            if (ownVolley && !accepted) living.invulnerableTime = immunity;
        }
        if (accepted && living != null && owner instanceof net.minecraft.server.level.ServerPlayer player) {
            com.codex.blacklight.combat.QuillHarvest.impact(player,living,entry,roofIndex(),harvestable);
            if (site != null && (!heavy() || wedge)) QuillWedge.accepted(receiver, owner.getUUID(), site, now, heavy());
            com.codex.blacklight.combat.QuillImpact.hit(player, living, site == null ? entry : site.point(),
                    direction, heavy(), volleySlot());
            if (wedge && canPierceWedge && cutTargets == null) {
                piercingWedge = true;
                enableCleaving();
                damage *= .6F;
                // Continue along the segment which hit the wedge, including the next server tick.
                entityData.set(TRAVEL, getDeltaMovement().toVector3f());
                entityData.set(BOW, new org.joml.Vector3f());
            }
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit){
        super.onHitBlock(hit);
        if(level() instanceof net.minecraft.server.level.ServerLevel server){
            var state=server.getBlockState(hit.getBlockPos());
            var point=hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.035));
            var material = state.getSoundType(server, hit.getBlockPos(), this);
            float gain = volleySlot() == 0 ? (heavy() ? .98F : .82F) : .24F;
            server.playSound(null, point.x, point.y, point.z, material.getHitSound(), net.minecraft.sounds.SoundSource.PLAYERS,
                    gain * material.getVolume(), material.getPitch() * (heavy() ? .84F : 1.16F));
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,state),
                    point.x,point.y,point.z,heavy()?13:5,.065,.065,.065,heavy()?.13:.06);
        }
    }
    @Override protected void onHit(HitResult hit){
        if(level() instanceof net.minecraft.server.level.ServerLevel server){
            var end=hit.getLocation();
            double length=Math.min(2.3,position().distanceTo(end));
            var backwards=getDeltaMovement().normalize().scale(-1);
            for(double offset=.12;offset<length;offset+=heavy()?.23:.38){
                var p=end.add(backwards.scale(offset));
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE,p.x,p.y,p.z,1,.012,.012,.012,.018);
            }
        }
        super.onHit(hit);
        if(level().isClientSide||isRemoved())return;
        if(cutTargets==null||!(hit instanceof EntityHitResult first)){
            discard();
            return;
        }
        cutTargets.add(bodyId(first.getEntity()));
        Vec3 end=position().add(getDeltaMovement());
        var wall=level().clip(new net.minecraft.world.level.ClipContext(position(),end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
        var box = first.getEntity().getBoundingBox();
        Vec3 from = box.contains(position()) ? position() : box.clip(position(), end).orElse(end);
        // Vanilla reports the nearest contact in a tick. Consume the remainder
        // of that same segment so adjacent bodies and a wall behind one agree.
        while(cutTargets.size()<cleaveLimit()){
            var next=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level(),this,from,
                    wall.getLocation(),new AABB(from,wall.getLocation()).inflate(1),this::canHitEntity);
            if(next==null){
                if(wall.getType()==HitResult.Type.BLOCK&&!net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,wall)){
                    super.onHit(wall);
                    discard();
                }
                return;
            }
            if(net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this,next)||isRemoved())return;
            cutTargets.add(bodyId(next.getEntity()));
            super.onHit(next);
            if(isRemoved())return;
            from=next.getLocation();
        }
        discard();
    }
    public void launch(Vec3 heading,double distance,float speed,float height,float lateral,org.joml.Quaternionf orientation){
        int span=Math.max(1,Math.min(6,(int)(distance/speed)));
        var across=new Vec3(-heading.z,0,heading.x);
        if(across.lengthSqr()<.001)across=new Vec3(1,0,0);
        across=across.normalize();
        var up=across.cross(heading).normalize();
        double amplitude=Math.min(1,span/4.0);
        var bow=across.scale(lateral).add(up.scale(height)).scale(amplitude);
        entityData.set(TRAVEL,heading.scale(speed).toVector3f());
        entityData.set(BOW,bow.toVector3f());
        entityData.set(SPAN,span);
        entityData.set(RELEASE,new org.joml.Quaternionf(orientation).normalize());
        setDeltaMovement(velocity(0,span));
    }

    private static double curve(double age,int span){
        double t=Math.min(1,Math.max(0,(double)age/span));
        return 4*t*(1-t);
    }
    private Vec3 velocity(int age,int span){
        // The first half-step clears the shoulder before the crescent reaches cruising speed.
        double from=Math.max(0,age-.6),to=age+1-.6;
        return new Vec3(entityData.get(TRAVEL)).scale(to-from).add(new Vec3(entityData.get(BOW))
                .scale(curve(to,span)-curve(from,span)));
    }
    @Override public void tick(){
        int span=entityData.get(SPAN);
        if(span>0){
            long start=entityData.get(START);
            if(start<0&&!level().isClientSide){start=level().getGameTime();entityData.set(START,start);}
            int age=start<0?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,level().getGameTime()-start));
            if(age<=span+1)setDeltaMovement(velocity(age,span));
            if(age>=span && !piercingWedge && aimTarget!=null && level() instanceof net.minecraft.server.level.ServerLevel server
                    && server.getEntity(aimTarget) instanceof Mob prey && prey.isAlive()
                    && !QuillHarvest.protectedPet(prey)
                    && !com.codex.blacklight.combat.CombatTargeting.multipart(prey)){
                // The authored release clears the shoulder first. Keep the central quill on its
                // committed target during the long flight, still using normal block/entity collisions.
                double speed=entityData.get(TRAVEL).length();
                var point=prey.getBoundingBox().getCenter();
                if(prey instanceof net.minecraft.world.entity.animal.Animal)
                    point=point.add(0,Math.max(0,prey.getEyeY()-point.y)*.65,0);
                double lead=Math.min(3,position().distanceTo(point)/Math.max(.1,speed));
                var travel=point.add(prey.getDeltaMovement().scale(lead)).subtract(position());
                var direction=getDeltaMovement().normalize().lerp(travel.normalize(),.35).normalize();
                setDeltaMovement(direction.scale(speed));
                hasImpulse=true;
            }
        }
        if(level().isClientSide){
            System.arraycopy(passed,0,passed,1,passed.length-1);
            passed[0]=position();
        }
        super.tick();
        if(!level().isClientSide&&tickCount>=38)discard();
    }

    private void saveVector(CompoundTag tag,String key,EntityDataAccessor<org.joml.Vector3f> id){
        var v=entityData.get(id);
        tag.putFloat(key+"X",v.x);tag.putFloat(key+"Y",v.y);tag.putFloat(key+"Z",v.z);
    }
    private void loadVector(CompoundTag tag,String key,EntityDataAccessor<org.joml.Vector3f> id){
        entityData.set(id,new org.joml.Vector3f(tag.getFloat(key+"X"),tag.getFloat(key+"Y"),tag.getFloat(key+"Z")));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        if(volleyId!=null)tag.putUUID("QuillVolley",volleyId);
        super.addAdditionalSaveData(tag);tag.putFloat("QuillDamage",damage);tag.putInt("FalxSide",side());tag.putBoolean("QuillHeavy",heavy());
        if(aimTarget!=null)tag.putUUID("QuillAimTarget",aimTarget);
        tag.putBoolean("CanWedge", canWedge);
        tag.putBoolean("CanPierceWedge", canPierceWedge);
        tag.putBoolean("PiercingWedge", piercingWedge);
        tag.putInt("VolleySlot",volleySlot());tag.putInt("RoofIndex",roofIndex());
        tag.putInt("FalxSpan",entityData.get(SPAN));tag.putLong("FalxStart",entityData.get(START));
        if(cutTargets!=null){
            tag.putBoolean("Cleaving",true);
            int index=0;
            for(var id:cutTargets)tag.putUUID("QuillCut"+index++,id);
        }
        var rotation=releaseOrientation();
        tag.putFloat("ReleaseX",rotation.x);tag.putFloat("ReleaseY",rotation.y);
        tag.putFloat("ReleaseZ",rotation.z);tag.putFloat("ReleaseW",rotation.w);
        saveVector(tag,"Travel",TRAVEL);saveVector(tag,"Bow",BOW);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        volleyId=tag.hasUUID("QuillVolley")?tag.getUUID("QuillVolley"):null;
        super.readAdditionalSaveData(tag);if(tag.contains("QuillDamage"))damage=tag.getFloat("QuillDamage");
        aimTarget=tag.hasUUID("QuillAimTarget")?tag.getUUID("QuillAimTarget"):null;
        setWedgeSkills(tag.getBoolean("CanWedge"), tag.getBoolean("CanPierceWedge"));
        piercingWedge = tag.getBoolean("PiercingWedge");
        setVolleySlot(tag.getInt("VolleySlot"));
        setRoofIndex(tag.contains("RoofIndex")?tag.getInt("RoofIndex"):2*Math.abs(volleySlot())+(tag.getInt("FalxSide")&1));
        setHeavy(tag.getBoolean("QuillHeavy"));
        entityData.set(SIDE,tag.getInt("FalxSide")&1);
        if(tag.getBoolean("Cleaving")){
            enableCleaving();
            for(int i=0;i<CLEAVE_LIMIT;i++)if(tag.hasUUID("QuillCut"+i))cutTargets.add(tag.getUUID("QuillCut"+i));
        }
        if(tag.contains("ReleaseW")){
            var rotation=new org.joml.Quaternionf(tag.getFloat("ReleaseX"),tag.getFloat("ReleaseY"),
                    tag.getFloat("ReleaseZ"),tag.getFloat("ReleaseW"));
            if(rotation.lengthSquared()>.0001F)entityData.set(RELEASE,rotation.normalize());
        }
        entityData.set(SPAN,Math.min(6,Math.max(0,tag.getInt("FalxSpan"))));
        entityData.set(START,tag.contains("FalxStart")?tag.getLong("FalxStart"):-1L);
        loadVector(tag,"Travel",TRAVEL);loadVector(tag,"Bow",BOW);
    }
}
