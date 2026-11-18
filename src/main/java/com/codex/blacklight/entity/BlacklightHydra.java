package com.codex.blacklight.entity;

import com.codex.blacklight.city.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.infection.InfectedSoundCue;
import com.codex.blacklight.registry.ModInfectedSounds;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.UUID;

/** Rooted P1 hydra. The planted variant grows into an owner-operated dimension entrance. */
public final class BlacklightHydra extends Monster implements GeoEntity,AnimatedHurtBody {
    private static final EntityDataAccessor<Boolean> PLANTED=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> GROWTH=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BODY_SIZE=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIME=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> COMBAT_STARTED=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> COMBAT_RECOIL=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FEED_TARGET=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HELD=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3f> PICKUP=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> PREVIOUS=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREVIOUS_TIME=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCENE_PLAYER=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_AGE=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_KIND=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private long deathTick=Long.MIN_VALUE,deathEpoch,deathCallEpoch;
    private int deathCallDepth;
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final HydraPart[] parts;
    private HydraHurtContact.Contact incomingContact;
    private Vec3 lastIncomingPoint;
    private long lastIncomingTick=Long.MIN_VALUE;
    private int incomingEffects;
    private final HydraDestination destination=new HydraDestination();
    private UUID owner;
    /** Task ownership is separate from the planted, player-owned transport system. */
    private UUID greeneOwner;
    private boolean greeneBuried,greeneRetiring;
    public boolean ownedByGreene(UUID id){return id!=null&&id.equals(greeneOwner);}
    public boolean bindGreene(UUID id,boolean buried){
        if(level().isClientSide||id==null||planted()||arrivalOnly||scenePlayerId()>=0||greeneRetiring
                ||greeneOwner!=null&&!greeneOwner.equals(id))return false;
        greeneOwner=id;setPersistenceRequired();
        if(tickCount==0){greeneBuried=buried;action(buried?11:5);entityData.set(PREVIOUS,-1);cooldown=0;return true;}
        return setGreeneUnderground(id,buried);
    }
    public boolean setGreeneUnderground(UUID id,boolean buried){
        if(level().isClientSide||!ownedByGreene(id)||!isAlive()||planted()||scenePlayerId()>=0)return false;
        greeneBuried=buried;
        // Reapplying a task command after either chunk reload is idempotent.
        // A surface attack is already above ground; it must not restart hyd_enter.
        boolean undergroundMotion=action()==4||action()==11;
        if(buried==undergroundMotion)return true;
        if(combatCargo!=null&&!combatCargo.released())combatCargo.discard();
        combatCargo=null;combatEnemy=null;getNavigation().stop();setTarget(null);cooldown=0;
        action(buried?4:5);return true;
    }
    /** P1 cleanup buries the hydra, then requests off-camera removal. No camera oracle exists server-side. */
    public boolean retireGreene(UUID id){
        if(!ownedByGreene(id))return false;
        setGreeneUnderground(id,true);greeneOwner=null;greeneBuried=true;greeneRetiring=true;return true;
    }
    private void saveGreene(CompoundTag tag){
        if(greeneOwner!=null)tag.putUUID("GreeneOwner",greeneOwner);
        tag.putBoolean("GreeneBuried",greeneBuried);tag.putBoolean("GreeneRetiring",greeneRetiring);
        if(greeneOwner!=null||greeneRetiring){
            tag.putInt("GreeneMotion",action()==4||action()==5||action()==11?action():0);
            tag.putInt("GreeneMotionTime",actionTime());
        }
    }
    private void loadGreene(CompoundTag tag){
        greeneOwner=!planted()&&tag.hasUUID("GreeneOwner")?tag.getUUID("GreeneOwner"):null;
        greeneRetiring=!planted()&&tag.getBoolean("GreeneRetiring");greeneBuried=tag.getBoolean("GreeneBuried")||greeneRetiring;
        if(greeneOwner==null&&!greeneRetiring)return;
        int phase=tag.getInt("GreeneMotion");
        if(phase!=4&&phase!=5&&phase!=11)phase=greeneBuried?11:0;
        if(greeneBuried&&phase!=4&&phase!=11)phase=11;
        action(phase);entityData.set(TIME,net.minecraft.util.Mth.clamp(tag.getInt("GreeneMotionTime"),0,phase==4?35:phase==5?79:0));
        entityData.set(PREVIOUS,-1);cooldown=0;
    }

    private HydraLink link;
    private boolean waitForDeparture;
    private int age,feeds,spreadCursor,cooldown,prepareTicks;
    private Mob food;
    private boolean destinationReady;
    private boolean arrivalOnly,expiredArrival;
    private Vec3 pickupPosition;
    private int combatSequence;
    private LivingEntity combatEnemy;
    private HydraDebris combatCargo;
    public HydraDebris combatCargo(){return combatCargo;}
    private boolean combatAction(){return HydraCombatAction.get(action())!=null||HydraThrow.action(action());}
    public boolean canHitCombatTarget(LivingEntity target){return combatTarget(target);}
    public Vec3 combatGrip(float partial){return gripAt(motionPose(partial),level().isClientSide?net.minecraft.util.Mth.rotLerp(partial,yBodyRotO,yBodyRot):getYRot());}
    private Vec3 gripAt(float[][] pose,float yaw){
        var p=HydraMotion.INSTANCE.grip(pose).mul(visualScale());p.rotateY((float)Math.toRadians(180-yaw));return position().add(p.x,p.y+.01*visualScale(),p.z);
    }
    private Vec3 damageContact;
    private InfectedAttackStyle damageStyle=InfectedAttackStyle.CRUSH;
    private BlockHitResult groundContact;
    private int groundImpacts,lastGroundAge=-1,shockWaves;
    private HydraContact.Obstacle pendingObstacle;
    private Vec3 obstaclePoint;
    private int wallImpacts;
    private long forecastStarted=Long.MIN_VALUE,forecastTick=Long.MIN_VALUE;
    private float forecastStop=-1;
    private final java.util.Set<Integer> waveEvents=new java.util.HashSet<>();
    private final java.util.Set<String> groundedJoints=new java.util.HashSet<>();
    public Vec3 damageContact(){return damageContact;}
    public InfectedAttackStyle damageStyle(){return damageStyle;}
    public int groundImpacts(){return groundImpacts;}
    public int shockWaves(){return shockWaves;}
    public int lastGroundAge(){return lastGroundAge;}
    public Vec3 groundImpactPoint(){return groundContact==null?null:groundContact.getLocation();}
    public float combatAge(float partial){return (float)Math.max(0,level().getGameTime()-entityData.get(COMBAT_STARTED))+partial;}
    public float recoilStart(){return entityData.get(COMBAT_RECOIL);}
    public boolean recoiling(){return recoilStart()>=0;}
    private float displayedRecoilStart(){return level().isClientSide&&!recoiling()&&forecastStarted==entityData.get(COMBAT_STARTED)?forecastStop:recoilStart();}
    public float combatPoseAge(float partial){return HydraContact.recoilPoseAge(combatAge(partial),displayedRecoilStart());}
    public int wallImpacts(){return wallImpacts;}
    public Vec3 obstaclePoint(){return obstaclePoint;}
    private final java.util.Set<UUID> attackVictims=new java.util.HashSet<>();
    public BlacklightHydra(EntityType<? extends BlacklightHydra> t,Level l){super(t,l);setPersistenceRequired();xpReward=30;if(!l.isClientSide)setBodySize(1.6F+random.nextFloat()*.8F);parts=new HydraPart[HydraContact.regionBones().size()];for(int i=0;i<parts.length;i++)parts[i]=new HydraPart(this,i);setId(ENTITY_COUNTER.getAndAdd(parts.length+1)+1);updateParts();}
    @Override public boolean isMultipartEntity(){return parts!=null;}
    @Override public HydraPart[] getParts(){return parts;}
    @Override public void setId(int id){super.setId(id);if(parts!=null)for(int i=0;i<parts.length;i++)parts[i].setId(id+i+1);}
    @Override public boolean isPickable(){return false;}
    public java.util.List<HydraContact.Region> hurtRegions(float offset){
        float yaw=level().isClientSide?net.minecraft.util.Mth.rotLerp(offset,yBodyRotO,yBodyRot):net.minecraft.util.Mth.rotLerp(Math.max(0,Math.min(1,offset+1)),yBodyRotO,yBodyRot);
        return HydraContact.regions(motionPose(offset),position(),yaw,visualScale());
    }
    public void updateParts(){
        if(parts==null)return;var now=hurtRegions(0);var next=hurtRegions(1);
        for(int i=0;i<parts.length;i++)parts[i].bounds(now.get(i).bounds(),next.get(i).bounds());
    }
    public HydraHurtContact.Contact incomingContact(){return incomingContact;}
    public Vec3 incomingPoint(){return lastIncomingTick==level().getGameTime()&&lastIncomingPoint!=null?lastIncomingPoint:position();}
    public int incomingEffects(){return incomingEffects;}
    public boolean hurtPart(int slot,DamageSource source,float amount){
        if(level().isClientSide||!isAlive()||!Float.isFinite(amount)||amount<=0)return false;
        var contact=HydraHurtContact.nativeHit(this,slot,source);return contact!=null&&hurtAt(source,amount,contact);
    }
    public boolean hurtAt(DamageSource source,float amount,HydraHurtContact.Contact contact){
        if(level().isClientSide||contact==null||!Float.isFinite(amount)||amount<=0)return false;
        var prior=incomingContact;incomingContact=contact;try{return hurt(source,amount);}finally{incomingContact=prior;}
    }
    /** Invoked by the accepted-damage event, after armor, absorption and cancellation. */
    public void confirmedIncoming(DamageSource source,float amount){
        if(incomingContact==null||amount<=0)return;
        var c=incomingContact;lastIncomingPoint=c.point();lastIncomingTick=level().getGameTime();incomingEffects++;
        var n=c.normal();var attacker=source.getEntity();
        var tissue=new com.codex.blacklight.network.InfectedImpactPayload(attacker==null?getId():attacker.getId(),getId(),c.style().ordinal(),c.point().x,c.point().y,c.point().z,(float)n.x,(float)n.y,(float)n.z,.65F,level().getGameTime()*31+getId());
        var sound=ModInfectedSounds.prepare(this,com.codex.blacklight.infection.InfectedSoundCue.contact(c.style()),c.point());
        ContactEffects.body(this,tissue,c.style()==InfectedAttackStyle.CRUSH,sound);
    }
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,180).add(Attributes.ATTACK_DAMAGE,14).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.FOLLOW_RANGE,18);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(DEATH_AGE,-1);entityData.define(DEATH_KIND,HydraDeath.NORMAL);entityData.define(COMBAT_RECOIL,-1F);entityData.define(COMBAT_STARTED,0L);entityData.define(SCENE_PLAYER,-1);entityData.define(PLANTED,false);entityData.define(GROWTH,1F);entityData.define(BODY_SIZE,1.6F);entityData.define(ACTION,0);entityData.define(TIME,0);entityData.define(FEED_TARGET,-1);entityData.define(HELD,-1);entityData.define(PICKUP,new org.joml.Vector3f());entityData.define(PREVIOUS,-1);entityData.define(PREVIOUS_TIME,0);}
    @Override protected void registerGoals(){}
    @Override protected boolean shouldDespawnInPeaceful(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void knockback(double strength,double x,double z){}
    public boolean planted(){return entityData.get(PLANTED);}
    public float growth(){return entityData.get(GROWTH);}
    public int action(){return entityData.get(ACTION);}
    public int actionTime(){return entityData.get(TIME);}
    public int feedTarget(){return entityData.get(FEED_TARGET);}
    public int feeds(){return feeds;}
    public int growthAge(){return age;}
    public float bodySize(){return entityData.get(BODY_SIZE);}
    public void setBodySize(float size){entityData.set(BODY_SIZE,Float.isFinite(size)?net.minecraft.util.Mth.clamp(size,1.6F,2.4F):1.6F);}
    public float visualScale(){return bodySize()*(planted()?.18F+.82F*growth():1F);}
    @Override public EntityDimensions getDimensions(Pose pose){return super.getDimensions(pose).scale(visualScale());}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(GROWTH)||key.equals(PLANTED)||key.equals(BODY_SIZE))refreshDimensions();}
    public HydraLink link(){return link;}
    public void connect(UUID player,HydraLink endpoint){
        owner=player;link=endpoint;arrivalOnly=false;entityData.set(PLANTED,true);
        age=HydraGrowth.MATURE_TICKS;feeds=HydraGrowth.REQUIRED_FEEDS;entityData.set(GROWTH,1F);
    }
    public boolean availableForArrival(ServerPlayer p){
        return com.codex.blacklight.registry.ModAttachments.available(p)&&isAlive()&&planted()&&growth()>=1&&p.getUUID().equals(owner)&&heldId()<0&&(action()==0||action()==11);
    }
    private boolean prepareDestination(ServerLevel source){
        if(link==null)return destination.prepare(source);
        return destination.prepare(source.getServer().getLevel(link.dimension()),link.position());
    }
    public void plant(UUID player){owner=player;entityData.set(PLANTED,true);entityData.set(GROWTH,0F);}
    @Override public boolean canBeLeashed(Player player){return false;}
    private void action(int value){if(value==0||value==11){if(scenePlayerId()>=0)destination.release();entityData.set(SCENE_PLAYER,-1);}entityData.set(PREVIOUS,action());entityData.set(PREVIOUS_TIME,!combatAction()?actionTime():(int)combatPoseAge(0));entityData.set(ACTION,value);entityData.set(TIME,0);if(HydraCombatAction.get(value)!=null||HydraThrow.action(value)){entityData.set(COMBAT_STARTED,level().getGameTime());entityData.set(COMBAT_RECOIL,-1F);}}
    private float[][] combatPoseAt(float age){return HydraMotion.INSTANCE.pose(action(),age,entityData.get(PREVIOUS),entityData.get(PREVIOUS_TIME));}
    public float[][] motionPose(float partial){
        if(nativeDying())return HydraDeath.pose(nativeDeathKind(),nativeDeathAge(partial));
        float yaw=level().isClientSide?net.minecraft.util.Mth.rotLerp(partial,yBodyRotO,yBodyRot):getYRot();
        boolean combat=combatAction();
        if(level().isClientSide&&combat&&!recoiling()){
            long start=entityData.get(COMBAT_STARTED);if(forecastStarted!=start){forecastStarted=start;forecastTick=Long.MIN_VALUE;forecastStop=-1;}
            if(forecastTick!=level().getGameTime()){
                forecastTick=level().getGameTime();float age=(float)Math.floor(combatAge(partial));
                // Locally reserve the next two draw ticks as well: metadata can arrive
                // after a partial frame has already crossed the server contact time.
                // Revalidate an unacknowledged stop: a door or breakable wall may
                // disappear before the server's actual attack reaches it.
                var hit=combatObstacle(forecastStop<0?age:Math.min(age,forecastStop),age+2,yaw);forecastStop=hit==null?-1:hit.safeAge();
            }
        }
        var pose=HydraMotion.INSTANCE.pose(action(),combat?combatPoseAge(partial):actionTime()+partial,entityData.get(PREVIOUS),entityData.get(PREVIOUS_TIME));
        return !planted()&&(action()==0||combat&&displayedRecoilStart()>=0)?HydraContact.restPose(this,pose,yaw,visualScale()):pose;
    }
    private HydraContact.Obstacle combatObstacle(float from,float to,float yaw){
        java.util.function.DoubleFunction<java.util.List<HydraContact.Box>> pose=t->{
            var bones=combatPoseAt((float)t);var boxes=new java.util.ArrayList<>(HydraContact.body(bones,position(),yaw,visualScale()));
            if(action()==HydraThrow.HOLD||action()==HydraThrow.PICKUP&&t>=HydraThrow.ATTACH||action()==HydraThrow.THROW&&t<HydraThrow.RELEASE)
                boxes.add(HydraThrow.box(gripAt(bones,yaw),visualScale(),0));
            return boxes;
        };
        AABB bounds=null;for(float t=from;t<=to;t+=.25F)for(var b:pose.apply(t))bounds=bounds==null?b.bounds:bounds.minmax(b.bounds);
        return HydraContact.obstacle(pose,from,to,bounds==null?java.util.List.of():HydraContact.terrain(this,bounds.inflate(.5*visualScale())));
    }
    public boolean transporting(){return action()==2||action()==6||action()==7||action()==8;}
    public int scenePlayerId(){return entityData.get(SCENE_PLAYER);}
    public int heldId(){return entityData.get(HELD);}
    public ServerPlayer heldPlayer(){
        return level().getEntity(heldId()) instanceof ServerPlayer p&&p.isAlive()
                &&com.codex.blacklight.registry.ModAttachments.available(p)?p:null;
    }
    public boolean hold(ServerPlayer p){
        if(!com.codex.blacklight.registry.ModAttachments.available(p)||!isAlive()||!transporting()||heldId()>=0||p.level()!=level()||p.isPassenger()||!p.isAlive()||HydraTransport.active(p)||com.codex.blacklight.power.BiomassSystem.active(p)
                ||com.codex.blacklight.combat.ConsumeSystem.grappleProtected(p))return false;
        // Capture is an exclusive action: discard queued attacks and their movement leases.
        com.codex.blacklight.combat.WeaponCombatSystem.cancel(p);
        com.codex.blacklight.combat.DevastatorSystem.cancel(p);
        com.codex.blacklight.combat.WeaponCombatSystem.cancelHeldInput(p);
        com.codex.blacklight.combat.ConsumeSystem.cancel(p);
        com.codex.blacklight.movement.WingFlightSystem.stop(p);
        com.codex.blacklight.movement.PrototypeMovementSystem.suspendForTransport(p);
        var cargo=com.codex.blacklight.combat.BlockThrowSystem.held(p);if(cargo!=null)cargo.drop();
        var rescue=new CompoundTag();rescue.putString("Dimension",level().dimension().location().toString());
        rescue.putLong("Position",BlockPos.containing(pickupPosition==null?p.position():pickupPosition).asLong());
        p.getPersistentData().put("BlacklightHydraRescue",rescue);
        entityData.set(HELD,p.getId());entityData.set(PICKUP,p.position().subtract(position()).toVector3f());
        com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER,getId());com.codex.blacklight.registry.ModAttachments.syncData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER);return true;
    }
    private void releaseHeld(){
        var p=heldPlayer();if(com.codex.blacklight.registry.ModAttachments.available(p)&&com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER)==getId()){com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER,-1);com.codex.blacklight.registry.ModAttachments.syncData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER);}
        entityData.set(HELD,-1);
    }
    public boolean beginArrival(ServerPlayer player){
        if(!com.codex.blacklight.registry.ModAttachments.available(player)||heldId()>=0||!isAlive()||player.level()!=level())return false;
        // Retain the destination while the real client loads terrain and begins
        // tracking the carrier; the departure hydra releases its own ticket.
        destination.prepare((ServerLevel)level(),blockPosition());
        arrivalOnly=link==null;owner=player.getUUID();pickupPosition=player.position();waitForDeparture=true;action(7);entityData.set(SCENE_PLAYER,player.getId());
        // Arrival starts already buried; don't blend from standing idle across the dimension boundary.
        entityData.set(PREVIOUS,-1);if(!hold(player)){discard();return false;}player.fallDistance=0;return true;
    }
    @Override public void tick(){try{tickHydra();}finally{updateParts();}}
    private void tickHydra(){
        super.tick();setDeltaMovement(0,getDeltaMovement().y,0);
        if(level().isClientSide||!isAlive())return;
        if(expiredArrival){discard();return;}
        var level=(ServerLevel)level();
        entityData.set(TIME,Math.min(1000000,actionTime()+1));
        if(tickCount==1&&!planted()&&greeneOwner==null&&!greeneRetiring&&action()==0&&!isNoAi()){action(5);entityData.set(PREVIOUS,-1);}
        if(cooldown>0)cooldown--;
        if(action()!=0){
            if(scenePlayerId()>=0&&(action()==7||action()==8||action()==4)&&tickCount%20==0)
                destination.prepare(level,blockPosition());
            // Keep the prepared destination alive throughout the source cinematic.
            // Maturation + grab + burrow can outlast the short arrival ticket.
            if(planted()&&(action()==2||action()==6)&&tickCount%20==0)prepareDestination(level);
            if(combatAction()&&!recoiling()&&(isNoAi()||level.getDifficulty()==Difficulty.PEACEFUL
                    ||ImpactControl.stunned(this)||!combatTarget(combatEnemy))){cancelCombat();return;}
            tickAction(level);var held=heldPlayer();if(held!=null){HydraTransport.place(held);if(held.connection!=null)held.connection.resetPosition();}return;}
        if(!planted()){tickWild(level);return;}
        float previousGrowth=growth();
        age=HydraGrowth.age(age+1);entityData.set(GROWTH,HydraGrowth.progress(age,feeds));
        if(previousGrowth<1&&growth()>=1){spreadCursor=0;action(5);entityData.set(PREVIOUS,-1);playSound(SoundEvents.RAVAGER_ROAR,1,.55F);return;}
        var planter=owner==null?null:level.getPlayerByUUID(owner);
        boolean nearby=planter!=null&&planter.level()==level&&planter.isAlive()&&distanceToSqr(planter)<24*24;
        if(!nearby){destination.release();destinationReady=false;prepareTicks=0;return;}
        if(tickCount%20==0){
            if(feeds>0&&prepareTicks<1200){
                destinationReady=prepareDestination(level);if(!destinationReady)prepareTicks+=20;
            }
            if(growth()<1&&feeds<HydraGrowth.REQUIRED_FEEDS)seekFood(level,planter);
            spread(level);
        }
        if(distanceToSqr(planter)>36)waitForDeparture=false;
        if(growth()>=1&&!waitForDeparture&&cooldown==0&&destinationReady&&distanceToSqr(planter)<16&&hasLineOfSight(planter)
            &&!planter.isSpectator()&&!planter.isPassenger()&&!planter.isShiftKeyDown()&&!planter.isSleeping()){
            // Do not capture anyone else nearby, including a second player's spectators.
            pickupPosition=planter.position();
            setYRot((float)(Math.toDegrees(Math.atan2(planter.getZ()-getZ(),planter.getX()-getX()))-90));
            yBodyRot=getYRot();yHeadRot=getYRot();action(2);entityData.set(SCENE_PLAYER,planter.getId());playSound(SoundEvents.RAVAGER_ROAR,1,.65F);
        }
    }
    private void seekFood(ServerLevel level,Player planter){
        for(var mob:level.getEntitiesOfClass(Mob.class,getBoundingBox().inflate(2.5),m->m.isAlive()&&m!=this
                &&!(m instanceof BlacklightHive)&&!(m instanceof BlacklightHydra)&&!(m instanceof BlacklightHunter)
                &&!m.isPassenger()&&!m.isVehicle())){
            // Named creatures and pets must be explicitly brought on their owner's lead.
            if((mob.hasCustomName()||mob instanceof TamableAnimal)&&mob.getLeashHolder()!=planter)continue;
            if(mob.getLeashHolder()!=null&&mob.getLeashHolder()!=planter)continue;
            if(!hasLineOfSight(mob))continue;
            food=mob;entityData.set(FEED_TARGET,mob.getId());action(1);break;
        }
    }
    private void tickAction(ServerLevel level){
        int t=actionTime();
        if(action()==1){
            if(food==null||!food.isAlive()||distanceToSqr(food)>36){finishAction();return;}
            if(t<30){
                Vec3 pull=position().add(0,.4,0).subtract(food.position());
                food.setDeltaMovement(pull.scale(.12));food.hurtMarked=true;
                if(t%5==0)level.sendParticles(ParticleTypes.CRIMSON_SPORE,food.getX(),food.getY()+.5,food.getZ(),8,.3,.4,.3,.02);
            }
            if(t==30){
                var prey=food;
                boolean accepted=prey.hurt(damageSources().mobAttack(this),Math.max(20,prey.getMaxHealth()*4));
                // A canceled LivingDeath can leave zero HP without accepting the
                // kill. Match other feeding systems: that cannot pay growth.
                if(accepted&&isAlive()&&!prey.isAlive()&&((com.codex.blacklight.mixin.LivingDeathAccess)prey).blacklight$deathAccepted()
                        &&prey.getKillCredit()==this){feeds=HydraGrowth.feeds(feeds+1);playSound(SoundEvents.GENERIC_EAT,1,.6F);}
                food=null;entityData.set(FEED_TARGET,-1);
            }
            if(t>=30)finishAction();
        }else if(action()==2){
            var p=owner==null?null:level.getPlayerByUUID(owner);
            if(p==null||!p.isAlive()||p.level()!=level||distanceToSqr(p)>144*bodySize()*bodySize()){abortTravel();return;}
            // hyd_grab_concrete lowers the head before lifting. Clamp at the low phase,
            // not at the beginning while the horns are still above the player's head.
            if(t==50&&!hold((ServerPlayer)p)){abortTravel();return;}
            if(t>50&&(heldId()!=p.getId()||p.isShiftKeyDown())){abortTravel();return;}
            if(t>=80)action(6);
        }else if(action()==6){
            var p=heldPlayer();if(p==null||!p.isAlive()||p.isShiftKeyDown()){abortTravel();return;}
            p.fallDistance=0;groundParticles(level,t);
            if(t>=36){
                releaseHeld();
                boolean entered=prepareDestination(level)&&CityTravel.transferFromHydra(p,this,blockPosition().offset((int)Math.ceil(6*bodySize()),0,0),bodySize());
                if(!entered){restorePassenger(p);p.displayClientMessage(Component.translatable("hydra.blacklight.wait"),false);}
                destination.release();destinationReady=false;prepareTicks=0;cooldown=240;waitForDeparture=true;action(11);
            }
        }else if(action()==7){
            var p=heldPlayer();if(p==null||!p.isAlive()||p.isShiftKeyDown()){abortTravel();return;}
            p.fallDistance=0;groundParticles(level,t);
            if(t>=80)action(8);
        }else if(action()==8){
            if(t<62&&(heldPlayer()==null||!heldPlayer().isAlive()||heldPlayer().isShiftKeyDown())){abortTravel();return;}
            // Adapted release at the original ped_toss forward swing (3.1 seconds).
            if(t==62&&heldPlayer()!=null){
                var p=heldPlayer();
                Vec3 release=gripPosition(p,0);releaseHeld();p.teleportTo(release.x,release.y,release.z);
                p.getPersistentData().remove("BlacklightHydraRescue");
                double yaw=Math.toRadians(getYRot());Vec3 velocity=new Vec3(-Math.sin(yaw)*.9,.65,Math.cos(yaw)*.9);
                com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_THROWN_AT,level.getGameTime());com.codex.blacklight.registry.ModAttachments.syncData(p,com.codex.blacklight.registry.ModAttachments.HYDRA_THROWN_AT);
                p.setDeltaMovement(velocity);p.setOnGround(false);p.hurtMarked=true;p.fallDistance=0;
                p.getPersistentData().remove("BlacklightHydraGrounded");
                p.getPersistentData().putLong("BlacklightHydraLanding",level.getGameTime()+200);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
                playSound(SoundEvents.RAVAGER_ATTACK,1,.7F);
            }
            if(t>=140){cooldown=100;action(4);}
        }else if(HydraThrow.action(action())){
            tickThrow(level);
        }else if(HydraCombatAction.get(action())!=null){
            tickCombat(level);
        }else if(action()==11){
            if(greeneRetiring){
                // MC substitute for the source camera-dependent cleanup: no nearby active player.
                if(level.players().stream().noneMatch(p->!p.isSpectator()&&p.isAlive()&&p.distanceToSqr(this)<50*50))discard();
                return;
            }
            if(greeneOwner!=null&&greeneBuried)return;
            // Stay fully buried, then emerge on approach. Both wild and gateway hydras use this.
            var visitor=level.getNearestPlayer(getX(),getY(),getZ(),24,p->p instanceof Player q&&!q.isSpectator()&&q.isAlive());
            if(cooldown==0&&visitor!=null)action(5);
        }else if(action()==5){
            if(t%5==0)level.sendParticles(ParticleTypes.CRIMSON_SPORE,getX(),getY()+.5,getZ(),18,1,.4,1,.04);
            if(t>=80)finishAction();
        }else if(t>=36){
            if(arrivalOnly){discard();return;}
            // hyd_exit finishes underground. Resume via hyd_enter rather than snapping
            // from that buried pose to the standing idle on the next frame.
            action(11);
        }
    }
    private void groundParticles(ServerLevel level,int t){
        if(t%4==0){level.sendParticles(ParticleTypes.CRIMSON_SPORE,getX(),getY()+.3,getZ(),22,1.3,.3,1.3,.06);
            var floor=level.getBlockState(blockPosition().below());
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,floor),getX(),getY()+.2,getZ(),16,1,.2,1,.1);}
    }
    private void tickWild(ServerLevel level){
        if(isNoAi()||cooldown>0||level.getDifficulty()==Difficulty.PEACEFUL||tickCount%10!=0)return;
        LivingEntity p=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(HydraThrow.MAX_RANGE,12,HydraThrow.MAX_RANGE),this::combatTarget).stream()
                .filter(this::hasLineOfSight).min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(p==null&&actionTime()>200&&greeneOwner==null){action(4);return;}
        if(p!=null&&hasLineOfSight(p)){
            float yaw=(float)(Math.toDegrees(Math.atan2(p.getZ()-getZ(),p.getX()-getX()))-90);
            for(int i=0;i<3;i++){int move=switch(Math.floorMod(combatSequence++,3)){case 1->9;case 2->10;default->3;};
                if(HydraContact.canReach(move,position(),yaw,visualScale(),p.getBoundingBox())&&beginCombat(p,move))return;
            }
            beginThrow(p);
        }
    }
    private boolean combatTarget(LivingEntity target){
        if(target==null||target==this||!target.isAlive()||target.level()!=level()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()&&!com.codex.blacklight.disguise.DisguiseSystem.fooled(this,p);
        return target instanceof Mob&&!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(target);
    }
    public boolean beginCombat(LivingEntity target,int move){
        if(level().isClientSide||planted()||arrivalOnly||action()!=0||isNoAi()||!isAlive()
                ||!combatTarget(target)||cooldown>0||level().getDifficulty()==Difficulty.PEACEFUL
                ||com.codex.blacklight.combat.ImpactControl.stunned(this)||distanceToSqr(target)>Math.pow(8*bodySize(),2)
                ||(move!=3&&move!=9&&move!=10)||!hasLineOfSight(target))return false;
        setYRot((float)(Math.toDegrees(Math.atan2(target.getZ()-getZ(),target.getX()-getX()))-90));
        yBodyRot=getYRot();yHeadRot=getYRot();attackVictims.clear();waveEvents.clear();groundedJoints.clear();groundContact=null;groundImpacts=0;shockWaves=0;lastGroundAge=-1;
        pendingObstacle=null;obstaclePoint=null;wallImpacts=0;combatEnemy=target;action(move);ModInfectedSounds.play(this,InfectedSoundCue.TENSION);return true;
    }
    public void cancelCombat(){if(combatAction()&&!recoiling()){if(combatCargo!=null&&!combatCargo.released())combatCargo.discard();combatCargo=null;entityData.set(COMBAT_RECOIL,combatAge(0));combatEnemy=null;cooldown=40;}}
    public boolean beginThrow(LivingEntity target){
        if(level().isClientSide||planted()||arrivalOnly||action()!=0||isNoAi()||!isAlive()||!combatTarget(target)
                ||cooldown>0||level().getDifficulty()==Difficulty.PEACEFUL||ImpactControl.stunned(this)
                ||distanceToSqr(target)>HydraThrow.MAX_RANGE*HydraThrow.MAX_RANGE||!hasLineOfSight(target))return false;
        setYRot((float)(Math.toDegrees(Math.atan2(target.getZ()-getZ(),target.getX()-getX()))-90));yBodyRot=getYRot();yHeadRot=getYRot();
        combatEnemy=target;combatCargo=null;pendingObstacle=null;obstaclePoint=null;wallImpacts=0;
        groundContact=null;groundImpacts=0;lastGroundAge=-1;shockWaves=0;groundedJoints.clear();action(HydraThrow.PICKUP);
        ModInfectedSounds.play(this,InfectedSoundCue.TENSION);return true;
    }
    private void tickThrow(ServerLevel level){
        float age=combatAge(0);
        if(recoiling()){
            if(pendingObstacle!=null&&age>=recoilStart()){ContactEffects.obstacle(this,pendingObstacle.point(),pendingObstacle.shape());wallImpacts++;pendingObstacle=null;}
            if(combatPoseAge(0)<=0){finishAction();combatEnemy=null;cooldown=60;}return;
        }
        if(action()!=HydraThrow.PICKUP&&(combatCargo==null||combatCargo.isRemoved())&&(action()!=HydraThrow.THROW||age<HydraThrow.RELEASE)){cancelCombat();return;}
        // The source stops turning before releasing. Keep the telegraphed direction afterwards.
        if(action()==HydraThrow.THROW&&age<=23.33334F&&combatTarget(combatEnemy)&&hasLineOfSight(combatEnemy)){
            float wanted=(float)(Math.toDegrees(Math.atan2(combatEnemy.getZ()-getZ(),combatEnemy.getX()-getX()))-90);
            setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(),wanted,10));yBodyRot=getYRot();yHeadRot=getYRot();
        }
        var obstacle=combatObstacle(age,age+1,getYRot());
        if(obstacle!=null){cancelCombat();entityData.set(COMBAT_RECOIL,obstacle.safeAge());pendingObstacle=obstacle;obstaclePoint=obstacle.point();return;}
        if(action()==HydraThrow.PICKUP){
            if(age<=HydraThrow.ATTACH&&groundContact==null)groundContact(HydraThrow.PICKUP,(int)age,getYRot(),0,new String[]{"Head"});
            if(age>=HydraThrow.ATTACH&&combatCargo==null){
                if(groundContact==null){cancelCombat();return;}
                var pos=groundContact.getBlockPos();var state=level.getBlockState(pos);
                if(!state.isCollisionShapeFullBlock(level,pos)||!state.getFluidState().isEmpty())state=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                combatCargo=new HydraDebris(this,state);if(!level.addFreshEntity(combatCargo)){combatCargo=null;cancelCombat();return;}
            }
            if(age>=HydraThrow.PICKUP_END)action(HydraThrow.HOLD);
        }else if(action()==HydraThrow.HOLD){if(age>=HydraThrow.HOLD_TICKS)action(HydraThrow.THROW);}
        else {
            if(age==17)ModInfectedSounds.play(this,InfectedSoundCue.SWING);
            if(age>=HydraThrow.RELEASE&&combatCargo!=null&&!combatCargo.released()){
                if(!combatTarget(combatEnemy)||!hasLineOfSight(combatEnemy)){cancelCombat();return;}
                combatCargo.release(combatEnemy.getBoundingBox().getCenter());
            }
            if(age>=HydraThrow.THROW_END){finishAction();combatCargo=null;combatEnemy=null;cooldown=60;}
        }
    }
    public boolean debrisStrike(HydraDebris debris,LivingEntity target,Vec3 point){
        if(!combatTarget(target))return false;damageContact=point;damageStyle=InfectedAttackStyle.CRUSH;
        try{
            boolean accepted=target.hurt(damageSources().thrown(debris,this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*1.4F);
            if(accepted){var direction=debris.getDeltaMovement().multiply(1,0,1).normalize();target.knockback(.95,-direction.x,-direction.z);
                if(target instanceof ServerPlayer player)ImpactControl.begin(player,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.BODY,10,0,0,false),getYRot());}
            return accepted;
        }finally{damageContact=null;}
    }
    private void tickCombat(ServerLevel level){
        int move=action(),age=(int)combatAge(0);var spec=HydraCombatAction.get(move);float beforeYaw=getYRot();
        if(recoiling()){
            if(pendingObstacle!=null&&age>=recoilStart()){
                ContactEffects.obstacle(this,pendingObstacle.point(),pendingObstacle.shape());wallImpacts++;pendingObstacle=null;
            }
            if(combatPoseAge(0)<=0){finishAction();combatEnemy=null;cooldown=40;}
            return;
        }
        float turn=spec.turningSpeed(age);
        if(turn>0&&combatTarget(combatEnemy)&&hasLineOfSight(combatEnemy)){
            float wanted=(float)(Math.toDegrees(Math.atan2(combatEnemy.getZ()-getZ(),combatEnemy.getX()-getX()))-90);
            setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(),wanted,turn));yBodyRot=getYRot();yHeadRot=getYRot();
        }
        // Reserve the next rendered tick before it is displayed. The same mesh
        // and source pose are used here and by motionPose on the client.
        var obstacle=combatObstacle(age,age+1,getYRot());
        if(obstacle!=null){
            entityData.set(COMBAT_RECOIL,obstacle.safeAge());pendingObstacle=obstacle;obstaclePoint=obstacle.point();
            if(obstacle.safeAge()<=age){ContactEffects.obstacle(this,obstacle.point(),obstacle.shape());wallImpacts++;pendingObstacle=null;return;}
        }
        float first=java.util.Arrays.stream(spec.windows).map(w->w.from()).min(Float::compare).orElse(0F);
        if(age==(int)Math.ceil(first))ModInfectedSounds.play(this,InfectedSoundCue.SWING);
        var samples=HydraContact.sweep(move,age-1,age,position(),beforeYaw,getYRot(),visualScale());
        if(!samples.isEmpty()){
            AABB bounds=samples.get(0).box().bounds;for(var s:samples)bounds=bounds.minmax(s.box().bounds);
            var victims=level.getEntitiesOfClass(LivingEntity.class,bounds.inflate(.01),this::combatTarget);
            for(var s:samples)for(var victim:victims){
                if(attackVictims.contains(victim.getUUID())||!s.box().touches(victim.getBoundingBox()))continue;
                Vec3 at=s.box().contact(victim.getBoundingBox());if(!clearContact(at)||ray(s.box().center,at).getType()!=HitResult.Type.MISS)continue;
                strike(victim,at,s.window().damage()/500F,s.window().kind().equals("Slash")?InfectedAttackStyle.TEAR:InfectedAttackStyle.CRUSH);
            }
        }
        if(move==3&&groundedJoints.size()<2&&age>=44&&age<=60)groundContact(age,beforeYaw);
        for(var w:spec.windows)if(w.wave()&&age>=w.from()&&age<w.to()&&waveEvents.add(w.offset()))groundWave(level,w);
        if(age>=spec.duration){finishAction();combatEnemy=null;cooldown=40;}
    }
    private BlockHitResult ray(Vec3 from,Vec3 to){return level().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));}
    private boolean clearContact(Vec3 at){return ray(new Vec3(getX(),Math.max(getY()+.15,Math.min(getEyeY(),at.y)),getZ()),at).getType()==HitResult.Type.MISS;}
    private void strike(LivingEntity victim,Vec3 at,float factor,InfectedAttackStyle style){
        damageContact=at;damageStyle=style;
        try{if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)*factor)){
            attackVictims.add(victim.getUUID());var force=victim.position().subtract(position()).multiply(1,0,1).normalize();
            victim.knockback((style==InfectedAttackStyle.CRUSH?.85:.6)*factor,-force.x,-force.z);
            if(victim instanceof ServerPlayer player){
                ImpactControl.begin(player,new ImpactProfile(style==InfectedAttackStyle.CRUSH?HitReaction.HEAVY:HitReaction.STAGGER,ImpactProfile.BODY,style==InfectedAttackStyle.CRUSH?10:6,0,0,false),getYRot());
                com.codex.blacklight.forge.PacketDistributor.sendToPlayer(player,new com.codex.blacklight.network.PhysicalFeedbackPayload(style==InfectedAttackStyle.CRUSH?com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.GROUND_SPIKE:com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HIT,.65F*factor));
            }
        }}finally{damageContact=null;}
    }
    private void groundContact(int age,float beforeYaw){
        groundContact(3,age,beforeYaw,43.333334F,new String[]{"Head","full_rig_body3"});
    }
    private void groundContact(int move,int age,float beforeYaw,float first,String[] joints){
        for(String joint:joints){
            if(groundedJoints.contains(joint))continue;
            var previous=HydraContact.boxes(move,Math.max(first,age-1),joint,position(),beforeYaw,visualScale());
            for(int step=1;step<=16;step++){
                float t=Math.max(first,age-1+step/16F),yaw=beforeYaw+net.minecraft.util.Mth.wrapDegrees(getYRot()-beforeYaw)*step/16;
                var current=HydraContact.boxes(move,t,joint,position(),yaw,visualScale());
                for(int b=0;b<current.size()&&!groundedJoints.contains(joint);b++)for(int p=0;p<current.get(b).points.size();p++){
                    Vec3 from=previous.get(b).points.get(p),to=current.get(b).points.get(p);
                    if(to.y>from.y+.001||!level().hasChunkAt(BlockPos.containing(to)))continue;
                    var hit=ray(from.add(0,.025,0),to.add(0,-.025,0));
                    if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP||!clearContact(hit.getLocation().add(0,.08,0)))continue;
                    boolean head=joint.equals("Head");
                    if(TerrainImpactEffects.emit(this,hit,(move==HydraThrow.PICKUP?1.2F:head?2.4F:1.6F)*bodySize(),head)){
                        if(head||groundContact==null)groundContact=hit;
                        groundedJoints.add(joint);groundImpacts++;lastGroundAge=age;break;
                    }
                }previous=current;
                if(groundedJoints.contains(joint))break;
            }
        }
    }
    private void groundWave(ServerLevel level,HydraCombatAction.Window w){
        if(groundContact==null)return;Vec3 origin=groundContact.getLocation();float radius=w.sourceRadius()*.5F*bodySize();
        // The original late Head waves are adapted to the actual struck MC support.
        // Removed floors, airborne targets and intervening collision shapes block them.
        if(!TerrainImpactEffects.emit(this,groundContact,radius,true))return;shockWaves++;
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(radius,.7,radius),this::combatTarget)){
            Vec3 feet=victim.position();var support=ray(feet.add(0,.08,0),feet.add(0,-.15,0));
            if(attackVictims.contains(victim.getUUID())||support.getType()!=HitResult.Type.BLOCK||support.getDirection()!=net.minecraft.core.Direction.UP)continue;
            Vec3 at=HydraContact.nearest(victim.getBoundingBox(),origin.add(0,.1,0));
            if(at.subtract(origin).horizontalDistance()>radius||ray(origin.add(0,.12,0),at.add(0,.12,0)).getType()!=HitResult.Type.MISS)continue;
            strike(victim,at,w.damage()/500F,InfectedAttackStyle.CRUSH);
        }
    }
    private void spread(ServerLevel level){
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!CityConfig.SPREAD.get()
            ||!net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level,this))return;
        // A deterministic finite disk, two surface replacements per second. Never loads chunks.
        int changed=0,checked=0;
        int extent=2+(int)(growth()*4);
        while(spreadCursor<169&&checked++<16&&changed<2){
            int dx=spreadCursor%13-6,dz=spreadCursor/13-6;
            if(!HydraGrowth.inside(dx,dz)){spreadCursor++;continue;}
            // Sweep the full footprint only once mature; earlier passes stay near the root.
            if(dx*dx+dz*dz>extent*extent){spreadCursor++;continue;}
            BlockPos pos=blockPosition().offset(dx,-1,dz);spreadCursor++;
            for(int dy=2;dy>=-2;dy--){
                var at=pos.offset(0,dy,0);if(!level.hasChunkAt(at)||!level.getWorldBorder().isWithinBounds(at))continue;
                var state=level.getBlockState(at);
                if(state.isAir()||!state.getFluidState().isEmpty()||state.hasBlockEntity()||state.getDestroySpeed(level,at)<0
                    ||!state.isSolidRender(level,at)||!level.getBlockState(at.above()).isAir())continue;
                if(state.is(ModBlocks.INFECTED_SOIL.get())||state.is(ModBlocks.HIVE_FLESH.get())||state.is(ModBlocks.HIVE_VEIN.get()))break;
                var planter=owner==null?null:level.getPlayerByUUID(owner);
                if(planter==null||!level.mayInteract(planter,at))break;
                var next=(Math.floorMod(dx*17+dz*7,5)==0?ModBlocks.HIVE_VEIN:ModBlocks.HIVE_FLESH).get().defaultBlockState();
                level.setBlock(at,next,3);changed++;break;
            }
        }
        if(spreadCursor>=169&&growth()<1)spreadCursor=0;
    }
    private void finishAction(){food=null;entityData.set(FEED_TARGET,-1);action(0);}
    private void restorePassenger(ServerPlayer p){
        if(!com.codex.blacklight.registry.ModAttachments.available(p)||!p.isAlive()||p.level()!=level())return;
        if(heldId()==p.getId())releaseHeld();var safe=CityTravel.safePosition((ServerLevel)level(),BlockPos.containing(pickupPosition==null?position().add(3,0,0):pickupPosition));
        if(safe!=null){p.teleportTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);p.getPersistentData().remove("BlacklightHydraRescue");}
        p.fallDistance=0;p.getPersistentData().putLong("BlacklightHydraLanding",level().getGameTime()+200);
    }
    /** A fatal slime transformation must release the paired animation before changing body shape. */
    public void abortTransport(ServerPlayer p){if(p!=null&&heldId()==p.getId())abortTravel();}
    private void abortTravel(){
        entityData.set(SCENE_PLAYER,-1);
        if(heldPlayer()!=null)restorePassenger(heldPlayer());
        if(owner!=null&&(action()==6||action()==7||action()==8&&actionTime()<62||action()==2&&actionTime()>50)){
            var p=((ServerLevel)level()).getPlayerByUUID(owner);
            if(p instanceof ServerPlayer sp&&sp.isAlive()&&sp.level()==level()&&!sp.isPassenger())restorePassenger(sp);
        }
        releaseHeld();destination.release();destinationReady=false;cooldown=100;action(4);
    }
    public Vec3 gripPosition(Player passenger,float partial){
        var point=HydraMotion.INSTANCE.grip(motionPose(partial));
        point.mul(visualScale());
        point.rotateY((float)Math.toRadians(180-getYRot()));
        var target=new Vec3(getX()+point.x,Math.max(level().getMinBuildHeight()+2,getY()+point.y-passenger.getBbHeight()*.5),getZ()+point.z);
        if(action()==2&&actionTime()<58){var offset=entityData.get(PICKUP);float f=net.minecraft.util.Mth.clamp((actionTime()+partial-50)/8,0,1);f=f*f*(3-2*f);return position().add(offset.x,offset.y,offset.z).lerp(target,f);}
        return target;
    }
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){
        if(planted()){
            if(!level().isClientSide){p.displayClientMessage(growth()>=1&&!destinationReady?Component.translatable("hydra.blacklight.wait"):Component.translatable("hydra.blacklight.progress",feeds,HydraGrowth.REQUIRED_FEEDS,Math.round(growth()*100)),true);if(p.getUUID().equals(owner)){prepareTicks=0;waitForDeparture=false;}}
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(p,hand);
    }
    public boolean nativeDying(){return entityData.get(DEATH_AGE)>=0;}
    public int nativeDeathKind(){return entityData.get(DEATH_KIND);}
    public float nativeDeathAge(float partial){return nativeDying()?Math.min(HydraDeath.duration(nativeDeathKind()),entityData.get(DEATH_AGE)+Math.max(0,partial)):-1;}
    /** Presentation only: all nine native body skins are hidden by ref218.
     * Entity removal remains a separate track and corpse hit rules are unchanged. */
    public boolean nativeDeathBodyHidden(float partial){return nativeDying()&&HydraDeath.bodyHidden(nativeDeathKind(),nativeDeathAge(partial));}
    @Override public AABB getBoundingBoxForCulling(){
        if(!nativeDying())return super.getBoundingBoxForCulling();
        AABB bounds=getBoundingBox();
        for(float partial:new float[]{0,1})for(var region:hurtRegions(partial))bounds=bounds.minmax(region.bounds());
        return bounds.inflate(.05);
    }
    /** Only this explicit weapon kind is the MC candidate for P1 Thermobaric1.
     * Ordinary explosions must not enter this distinct source branch. */
    private static boolean thermobaricDeath(DamageSource source){
        return source.getDirectEntity() instanceof com.codex.blacklight.firearms.OrdnanceProjectile projectile
                &&projectile.definition().kind()==com.codex.blacklight.firearms.WeaponDefinition.Kind.THERMOBARIC;
    }
    @Override public void die(DamageSource source){
        if(dead||isRemoved()||deathCallDepth>0&&deathCallEpoch==deathEpoch)return;
        long epoch=deathEpoch,previousCallEpoch=deathCallEpoch;
        deathCallDepth++;deathCallEpoch=epoch;
        try{super.die(source);}finally{deathCallDepth--;deathCallEpoch=previousCallEpoch;}
        if(level().isClientSide||epoch!=deathEpoch||!dead||isAlive()||isRemoved())return;
        stopForDeath(epoch);
    }
    /** Hydra-only mixin checkpoint: after the cancelable LivingDeath callback,
     * before vanilla's first dead-field read, score/loot and entity-die event.
     * A callback may replace this very object with another saved generation. */
    public boolean acceptNativeDeathTransaction(DamageSource source){
        if(level().isClientSide)return true;
        if(deathCallDepth==0||deathCallEpoch!=deathEpoch||isAlive()||isRemoved())return false;
        // Commit before any reward callback can save the accepted corpse.
        // The post-super cleanup still owns transport/cargo side effects.
        if(!nativeDying()){
            entityData.set(DEATH_KIND,thermobaricDeath(source)?HydraDeath.THERMOBARIC:HydraDeath.NORMAL);
            entityData.set(DEATH_AGE,0);deathTime=0;deathTick=level().getGameTime();
            setNoGravity(true);setDeltaMovement(Vec3.ZERO);fallDistance=0;
        }
        return true;
    }
    private void stopForDeath(long epoch){
        getNavigation().stop();setTarget(null);food=null;entityData.set(FEED_TARGET,-1);
        combatEnemy=null;pendingObstacle=null;forecastStarted=Long.MIN_VALUE;forecastStop=-1;
        var cargo=combatCargo;combatCargo=null;
        if(cargo!=null&&!cargo.released())cargo.discard();
        if(epoch!=deathEpoch||isRemoved())return;
        var passenger=heldPlayer();if(passenger!=null)restorePassenger(passenger);
        if(epoch!=deathEpoch||isRemoved())return;
        releaseHeld();destination.release();destinationReady=false;action(0);
    }
    @Override protected void tickDeath(){
        if(!nativeDying()){super.tickDeath();return;}
        deathTime=entityData.get(DEATH_AGE);setDeltaMovement(Vec3.ZERO);fallDistance=0;
        if(level().isClientSide||isRemoved())return;
        long now=level().getGameTime();if(now==deathTick)return;deathTick=now;
        int duration=HydraDeath.duration(nativeDeathKind());
        int next=Math.min(duration,entityData.get(DEATH_AGE)+1);
        entityData.set(DEATH_AGE,next);deathTime=next;
        if(next>=duration)remove(RemovalReason.KILLED);
    }
    private void saveNativeDeath(CompoundTag tag){
        if(nativeDying()){
            tag.putInt("HydraNativeDeathAge",entityData.get(DEATH_AGE));tag.putInt("HydraNativeDeathKind",nativeDeathKind());
        }else{tag.remove("HydraNativeDeathAge");tag.remove("HydraNativeDeathKind");}
    }
    private void loadNativeDeath(CompoundTag tag){
        entityData.set(DEATH_AGE,-1);entityData.set(DEATH_KIND,HydraDeath.NORMAL);deathTick=level().getGameTime();
        if(getHealth()>0){dead=false;if(getPose()==Pose.DYING)setPose(Pose.STANDING);return;}
        // The previous schema only wrote ordinary ages. Absent kind is therefore
        // normal; a present invalid kind must never be reinterpreted as normal.
        int kind=tag.contains("HydraNativeDeathKind")?(tag.contains("HydraNativeDeathKind",3)?tag.getInt("HydraNativeDeathKind"):-1):HydraDeath.NORMAL;
        if(tag.contains("HydraNativeDeathAge",3)&&tag.getInt("HydraNativeDeathAge")>=0&&HydraDeath.validKind(kind)){
            int saved=Math.min(HydraDeath.duration(kind),tag.getInt("HydraNativeDeathAge"));
            entityData.set(DEATH_KIND,kind);
            entityData.set(DEATH_AGE,saved);deathTime=saved;dead=true;setPose(Pose.DYING);
            setNoGravity(true);setDeltaMovement(Vec3.ZERO);fallDistance=0;
            if(!level().isClientSide)stopForDeath(deathEpoch);
        }
        // Untagged old corpses retain their old vanilla lifetime; do not replay
        // a fresh five-second death or invoke die() and drop rewards again.
    }
    @Override public boolean hurt(DamageSource source,float amount){boolean accepted=super.hurt(source,amount);if(accepted&&!level().isClientSide&&isAlive()&&transporting())abortTravel();return accepted;}
    @Override public void remove(RemovalReason reason){if(combatCargo!=null&&!combatCargo.released())combatCargo.discard();destination.release();if(!level().isClientSide)if(heldPlayer()!=null)restorePassenger(heldPlayer());releaseHeld();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("BodySize",bodySize());tag.putBoolean("ArrivalOnly",arrivalOnly);tag.putBoolean("Planted",planted());if(owner!=null)tag.putUUID("Planter",owner);tag.putInt("GrowthAge",age);tag.putInt("Feeds",feeds);tag.putInt("SpreadCursor",spreadCursor);if(link!=null)tag.put("HydraLink",link.save());saveGreene(tag);saveNativeDeath(tag);}
    @Override public void readAdditionalSaveData(CompoundTag tag){deathEpoch++;super.readAdditionalSaveData(tag);if(tag.contains("BodySize"))setBodySize(tag.getFloat("BodySize"));link=HydraLink.load(tag.getCompound("HydraLink"));expiredArrival=tag.getBoolean("ArrivalOnly")&&link==null;waitForDeparture=true;entityData.set(PLANTED,tag.getBoolean("Planted"));owner=tag.hasUUID("Planter")?tag.getUUID("Planter"):null;age=HydraGrowth.age(tag.getInt("GrowthAge"));feeds=HydraGrowth.feeds(tag.getInt("Feeds"));spreadCursor=net.minecraft.util.Mth.clamp(tag.getInt("SpreadCursor"),0,169);entityData.set(GROWTH,planted()?HydraGrowth.progress(age,feeds):1F);finishAction();cooldown=100;loadGreene(tag);loadNativeDeath(tag);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"body",5,s->{
            String clip=HydraMotion.clip(action());
            return s.setAndContinue(action()==0?RawAnimation.begin().thenLoop(clip):RawAnimation.begin().thenPlayAndHold(clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
