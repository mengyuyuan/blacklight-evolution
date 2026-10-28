package com.codex.blacklight.entity;

import com.codex.blacklight.city.*;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.UUID;

/** Rooted P1 hydra. The planted variant grows into an owner-operated dimension entrance. */
public final class BlacklightHydra extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> PLANTED=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> GROWTH=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BODY_SIZE=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIME=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FEED_TARGET=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HELD=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3f> PICKUP=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> PREVIOUS=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREVIOUS_TIME=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCENE_PLAYER=SynchedEntityData.defineId(BlacklightHydra.class,EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final HydraDestination destination=new HydraDestination();
    private UUID owner;
    private HydraLink link;
    private boolean waitForDeparture;
    private int age,feeds,spreadCursor,cooldown,prepareTicks;
    private Mob food;
    private boolean destinationReady;
    private boolean arrivalOnly,expiredArrival;
    private Vec3 pickupPosition;
    private int combatSequence;
    private final java.util.Set<UUID> attackVictims=new java.util.HashSet<>();
    public BlacklightHydra(EntityType<? extends BlacklightHydra> t,Level l){super(t,l);setPersistenceRequired();xpReward=30;if(!l.isClientSide)setBodySize(1.6F+random.nextFloat()*.8F);}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,180).add(Attributes.ATTACK_DAMAGE,14).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.FOLLOW_RANGE,18);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(SCENE_PLAYER,-1);b.define(PLANTED,false);b.define(GROWTH,1F);b.define(BODY_SIZE,1.6F);b.define(ACTION,0);b.define(TIME,0);b.define(FEED_TARGET,-1);b.define(HELD,-1);b.define(PICKUP,new org.joml.Vector3f());b.define(PREVIOUS,-1);b.define(PREVIOUS_TIME,0);}
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
    public void setBodySize(float size){entityData.set(BODY_SIZE,Float.isFinite(size)?Math.clamp(size,1.6F,2.4F):1.6F);}
    public float visualScale(){return bodySize()*(planted()?.18F+.82F*growth():1F);}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return super.getDefaultDimensions(pose).scale(visualScale());}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(GROWTH)||key.equals(PLANTED)||key.equals(BODY_SIZE))refreshDimensions();}
    public HydraLink link(){return link;}
    public void connect(UUID player,HydraLink endpoint){
        owner=player;link=endpoint;arrivalOnly=false;entityData.set(PLANTED,true);
        age=HydraGrowth.MATURE_TICKS;feeds=HydraGrowth.REQUIRED_FEEDS;entityData.set(GROWTH,1F);
    }
    public boolean availableForArrival(ServerPlayer p){
        return isAlive()&&planted()&&growth()>=1&&p.getUUID().equals(owner)&&heldId()<0&&(action()==0||action()==11);
    }
    private boolean prepareDestination(ServerLevel source){
        if(link==null)return destination.prepare(source);
        return destination.prepare(source.getServer().getLevel(link.dimension()),link.position());
    }
    public void plant(UUID player){owner=player;entityData.set(PLANTED,true);entityData.set(GROWTH,0F);}
    @Override public boolean canBeLeashed(){return false;}
    private void action(int value){if(value==0||value==11){if(scenePlayerId()>=0)destination.release();entityData.set(SCENE_PLAYER,-1);}entityData.set(PREVIOUS,action());entityData.set(PREVIOUS_TIME,actionTime());entityData.set(ACTION,value);entityData.set(TIME,0);}
    public float[][] motionPose(float partial){return HydraMotion.INSTANCE.pose(action(),actionTime()+partial,entityData.get(PREVIOUS),entityData.get(PREVIOUS_TIME));}
    public boolean transporting(){return action()==2||action()==6||action()==7||action()==8;}
    public int scenePlayerId(){return entityData.get(SCENE_PLAYER);}
    public int heldId(){return entityData.get(HELD);}
    public ServerPlayer heldPlayer(){return level().getEntity(heldId()) instanceof ServerPlayer p?p:null;}
    public boolean hold(ServerPlayer p){
        if(!transporting()||heldId()>=0||p.level()!=level()||p.isPassenger()||!p.isAlive()||HydraTransport.active(p)||com.codex.blacklight.power.BiomassSystem.active(p))return false;
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
        p.setData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER,getId());p.syncData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER);return true;
    }
    private void releaseHeld(){
        var p=heldPlayer();if(p!=null&&p.getData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER)==getId()){p.setData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER,-1);p.syncData(com.codex.blacklight.registry.ModAttachments.HYDRA_CARRIER);}
        entityData.set(HELD,-1);
    }
    public boolean beginArrival(ServerPlayer player){
        if(heldId()>=0||!isAlive()||player.level()!=level())return false;
        // Retain the destination while the real client loads terrain and begins
        // tracking the carrier; the departure hydra releases its own ticket.
        destination.prepare((ServerLevel)level(),blockPosition());
        arrivalOnly=link==null;owner=player.getUUID();pickupPosition=player.position();waitForDeparture=true;action(7);entityData.set(SCENE_PLAYER,player.getId());
        // Arrival starts already buried; don't blend from standing idle across the dimension boundary.
        entityData.set(PREVIOUS,-1);if(!hold(player)){discard();return false;}player.fallDistance=0;return true;
    }
    @Override public void tick(){
        super.tick();setDeltaMovement(0,getDeltaMovement().y,0);
        if(level().isClientSide||!isAlive())return;
        if(expiredArrival){discard();return;}
        var level=(ServerLevel)level();
        entityData.set(TIME,Math.min(1000000,actionTime()+1));
        if(tickCount==1&&!planted()&&action()==0&&!isNoAi()){action(5);entityData.set(PREVIOUS,-1);}
        if(cooldown>0)cooldown--;
        if(action()!=0){
            if(scenePlayerId()>=0&&(action()==7||action()==8||action()==4)&&tickCount%20==0)
                destination.prepare(level,blockPosition());
            // Keep the prepared destination alive throughout the source cinematic.
            // Maturation + grab + burrow can outlast the short arrival ticket.
            if(planted()&&(action()==2||action()==6)&&tickCount%20==0)prepareDestination(level);
            if((action()==3||action()==9||action()==10)&&(isNoAi()||level.getDifficulty()==Difficulty.PEACEFUL
                    ||com.codex.blacklight.combat.ImpactControl.stunned(this))){finishAction();cooldown=40;return;}
            tickAction(level);var held=heldPlayer();if(held!=null){HydraTransport.place(held);held.connection.resetPosition();}return;}
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
                p.setData(com.codex.blacklight.registry.ModAttachments.HYDRA_THROWN_AT,level.getGameTime());p.syncData(com.codex.blacklight.registry.ModAttachments.HYDRA_THROWN_AT);
                p.setDeltaMovement(velocity);p.setOnGround(false);p.hurtMarked=true;p.fallDistance=0;
                p.getPersistentData().remove("BlacklightHydraGrounded");
                p.getPersistentData().putLong("BlacklightHydraLanding",level.getGameTime()+200);
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
                playSound(SoundEvents.RAVAGER_ATTACK,1,.7F);
            }
            if(t>=140){cooldown=100;action(4);}
        }else if(action()==3){
            // FIG owner-relative contacts. Two coincident hit records share one victim ledger.
            if(t==43)combatHit(level,0,5,.2);
            if(t==60||t==62||t==64){double outer=3+(t-60)*1.5;combatHit(level,outer-3,outer,-1);groundParticles(level,t);}
            if(t>=148){finishAction();cooldown=40;}
        }else if(action()==9||action()==10){
            int contact=action()==9?24:40,duration=action()==9?47:83;
            if(t==contact)combatHit(level,0,action()==9?6:8,action()==9?.05:-.55);
            if(t>=duration){finishAction();cooldown=35;}
        }else if(action()==11){
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
        if(isNoAi()||cooldown>0||level.getDifficulty()==Difficulty.PEACEFUL)return;
        Player p=level.getNearestPlayer(getX(),getY(),getZ(),8*bodySize(),e->e instanceof Player q&&q.isAlive()&&!q.isCreative()&&!q.isSpectator());
        if(p==null&&actionTime()>200){action(4);return;}
        if(p!=null&&hasLineOfSight(p))beginCombat(p,switch(Math.floorMod(combatSequence++,3)){case 1->9;case 2->10;default->3;});
    }
    public boolean beginCombat(Player target,int move){
        if(level().isClientSide||planted()||arrivalOnly||action()!=0||isNoAi()||!isAlive()
                ||target==null||!target.isAlive()||target.level()!=level()||target.isCreative()||target.isSpectator()
                ||(move!=3&&move!=9&&move!=10)||!hasLineOfSight(target))return false;
        setYRot((float)(Math.toDegrees(Math.atan2(target.getZ()-getZ(),target.getX()-getX()))-90));
        yBodyRot=getYRot();yHeadRot=getYRot();attackVictims.clear();action(move);playSound(SoundEvents.RAVAGER_ATTACK,1,.6F);return true;
    }
    private void combatHit(ServerLevel level,double inner,double outer,double arc){
        inner*=bodySize();outer*=bodySize();double verticalReach=3*bodySize();
        for(var p:level.getEntitiesOfClass(Player.class,getBoundingBox().inflate(outer,verticalReach,outer),p->p.isAlive()&&!p.isCreative()&&!p.isSpectator())){
            double distance=Math.hypot(p.getX()-getX(),p.getZ()-getZ());
            if(distance<inner||distance>outer||Math.abs(p.getY()-getY())>verticalReach||attackVictims.contains(p.getUUID())||!hasLineOfSight(p)
                    ||!com.codex.blacklight.combat.CreatureAttack.inArc(p.getX()-getX(),p.getZ()-getZ(),getYRot(),arc))continue;
            attackVictims.add(p.getUUID());
            if(p.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)))p.knockback(1.2,getX()-p.getX(),getZ()-p.getZ());
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(),.8F,.7F);
        for(int i=0;i<20;i++){double a=i*Math.PI/10;level.sendParticles(ParticleTypes.POOF,getX()+Math.cos(a)*outer,getY()+.2,getZ()+Math.sin(a)*outer,1,.1,.1,.1,.02);}
    }
    private void spread(ServerLevel level){
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!CityConfig.SPREAD.get()
            ||!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level,this))return;
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
        if(heldId()==p.getId())releaseHeld();var safe=CityTravel.safePosition((ServerLevel)level(),BlockPos.containing(pickupPosition==null?position().add(3,0,0):pickupPosition));
        if(safe!=null){p.teleportTo(safe.getX()+.5,safe.getY(),safe.getZ()+.5);p.setDeltaMovement(Vec3.ZERO);p.getPersistentData().remove("BlacklightHydraRescue");}
        p.fallDistance=0;p.getPersistentData().putLong("BlacklightHydraLanding",level().getGameTime()+200);
    }
    /** A fatal slime transformation must release the paired animation before changing body shape. */
    public void abortTransport(ServerPlayer p){if(heldPlayer()==p)abortTravel();}
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
        if(action()==2&&actionTime()<58){var offset=entityData.get(PICKUP);float f=Math.clamp((actionTime()+partial-50)/8,0,1);f=f*f*(3-2*f);return position().add(offset.x,offset.y,offset.z).lerp(target,f);}
        return target;
    }
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){
        if(planted()){
            if(!level().isClientSide){p.displayClientMessage(growth()>=1&&!destinationReady?Component.translatable("hydra.blacklight.wait"):Component.translatable("hydra.blacklight.progress",feeds,HydraGrowth.REQUIRED_FEEDS,Math.round(growth()*100)),true);if(p.getUUID().equals(owner)){prepareTicks=0;waitForDeparture=false;}}
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(p,hand);
    }
    @Override public boolean hurt(DamageSource source,float amount){if(!level().isClientSide&&transporting()&&!isInvulnerableTo(source))abortTravel();return super.hurt(source,amount);}
    @Override public void remove(RemovalReason reason){destination.release();if(!level().isClientSide)if(heldPlayer()!=null)restorePassenger(heldPlayer());releaseHeld();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("BodySize",bodySize());tag.putBoolean("ArrivalOnly",arrivalOnly);tag.putBoolean("Planted",planted());if(owner!=null)tag.putUUID("Planter",owner);tag.putInt("GrowthAge",age);tag.putInt("Feeds",feeds);tag.putInt("SpreadCursor",spreadCursor);if(link!=null)tag.put("HydraLink",link.save());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(tag.contains("BodySize"))setBodySize(tag.getFloat("BodySize"));link=HydraLink.load(tag.getCompound("HydraLink"));expiredArrival=tag.getBoolean("ArrivalOnly")&&link==null;waitForDeparture=true;entityData.set(PLANTED,tag.getBoolean("Planted"));owner=tag.hasUUID("Planter")?tag.getUUID("Planter"):null;age=HydraGrowth.age(tag.getInt("GrowthAge"));feeds=HydraGrowth.feeds(tag.getInt("Feeds"));spreadCursor=Math.clamp(tag.getInt("SpreadCursor"),0,169);entityData.set(GROWTH,planted()?HydraGrowth.progress(age,feeds):1F);finishAction();cooldown=100;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){
        c.add(new AnimationController<>(this,"body",5,s->{
            String clip=HydraMotion.clip(action());
            return s.setAndContinue(action()==0?RawAnimation.begin().thenLoop(clip):RawAnimation.begin().thenPlayAndHold(clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
