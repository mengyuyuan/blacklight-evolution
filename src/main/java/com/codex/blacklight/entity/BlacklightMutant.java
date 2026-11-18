package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.*;

/** Species-specific, committed attacks. The authoritative action clock also drives the model. */
public final class BlacklightMutant extends Monster implements GeoEntity {
    public enum Kind { STALKER,SPITTER,BRUTE }
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(BlacklightMutant.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(BlacklightMutant.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RECOIL=SynchedEntityData.defineId(BlacklightMutant.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GROUND_RECOVERY=SynchedEntityData.defineId(BlacklightMutant.class,EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final Kind kind;
    private int cooldown,sequence,lastProcessed=-1;
    private UUID committedTarget;
    private Vec3 aim=Vec3.ZERO,direction=Vec3.ZERO;
    private float attackYaw;
    private boolean driveBlocked;
    private boolean pounceLaunched,pounceLanded;
    private double pounceSpeed;
    private int clawWindow=-1,previousAttackAge=-1;
    private Vec3 previousAttackPosition=Vec3.ZERO,groundImpact;
    private StalkerContact.Hit clawContact;
    private BruteContact.Hit bruteContact;
    private HydraContact.Obstacle bruteObstacle;
    private net.minecraft.world.phys.BlockHitResult bruteGround;
    private int wallImpacts;
    private boolean chargeWallRecoil;
    public BruteContact.Hit bruteContact(){return bruteContact;}
    public int wallImpacts(){return wallImpacts;}
    public float recoilStart(){return entityData.get(RECOIL);}
    public float attackPoseAge(float partial){return groundRecovery()?recoilStart():kind==Kind.BRUTE?HydraContact.recoilPoseAge(attackAge(partial),recoilStart()):attackAge(partial);}
    public boolean groundRecovery(){return kind==Kind.BRUTE&&entityData.get(GROUND_RECOVERY)&&recoilStart()>=0;}
    private List<net.minecraft.world.phys.AABB> visualGroundShapes=List.of();
    private Vec3 visualGroundOrigin;
    private float visualGroundYaw,visualGroundLimit=Float.MAX_VALUE;
    private long visualGroundStarted=Long.MIN_VALUE;
    public float[][] attackPose(float partial){
        if(groundRecovery())return BruteRecovery.pose(recoilStart(),attackAge(partial)-recoilStart());
        float age=attackPoseAge(partial);
        if(level().isClientSide&&attackMove()==MutantMove.BRUTE_SLAM&&recoilStart()<0&&age>=19&&age<=28)age=visualGroundPoseAge(age);
        return CreaturePoseTable.mutant(kind.name()).sample(attackMove().clip,age);
    }
    /** Hold the visible fist at locally known terrain while the server's contact state is in flight. */
    private float visualGroundPoseAge(float age){
        var shapes=new ArrayList<net.minecraft.world.phys.AABB>();
        for(var shape:level().getBlockCollisions(this,getBoundingBox().inflate(2.5,2,2.5)))shapes.addAll(shape.toAabbs());
        long started=entityData.get(STARTED);var origin=position();float yaw=yBodyRot;
        if(started!=visualGroundStarted||!origin.equals(visualGroundOrigin)||yaw!=visualGroundYaw||!shapes.equals(visualGroundShapes)){
            var contact=HydraContact.obstacle(t->BruteContact.boxes(MutantMove.BRUTE_SLAM,(float)t,origin,yaw,true),19,28,shapes);
            visualGroundLimit=contact==null?Float.MAX_VALUE:contact.safeAge();visualGroundStarted=started;visualGroundOrigin=origin;visualGroundYaw=yaw;visualGroundShapes=List.copyOf(shapes);
        }
        return Math.min(age,visualGroundLimit);
    }
    public StalkerContact.Hit clawContact(){return clawContact;}
    public Vec3 damageContact(){return bruteContact!=null?bruteContact.point():clawContact==null?null:clawContact.point();}
    public Vec3 groundImpactPoint(){return groundImpact;}
    private final Set<UUID> struck=new HashSet<>();
    public BlacklightMutant(EntityType<? extends BlacklightMutant> type,Level level,Kind kind){super(type,level);this.kind=kind;xpReward=kind==Kind.BRUTE?18:8;}
    public Kind kind(){return kind;}
    public String asset(){return "blacklight_"+kind.name().toLowerCase(Locale.ROOT);}
    public MutantMove attackMove(){return MutantMove.byId(entityData.get(MOVE));}
    public float attackAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial);}
    public boolean committedAttack(){return attackMove()!=MutantMove.NONE;}
    public static AttributeSupplier.Builder attributes(Kind k){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,k==Kind.BRUTE?80:k==Kind.SPITTER?28:24).add(Attributes.ATTACK_DAMAGE,k==Kind.BRUTE?10:5).add(Attributes.MOVEMENT_SPEED,k==Kind.STALKER?.34:.24).add(Attributes.FOLLOW_RANGE,28).add(Attributes.KNOCKBACK_RESISTANCE,k==Kind.BRUTE?.7:.1).add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(),.4);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(MOVE,0);entityData.define(STARTED,0L);entityData.define(RECOIL,-1F);entityData.define(GROUND_RECOVERY,false);}
    public boolean validTarget(LivingEntity target){
        if(target==null||target==this||!target.isAlive()||target.level()!=level()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()&&!com.codex.blacklight.disguise.DisguiseSystem.fooled(this,p);
        return target instanceof Mob&&!BlacklightInfectionSystem.isInfected(target);
    }
    @Override public boolean isAlliedTo(Entity other){return other instanceof LivingEntity living&&!(other instanceof Player)&&BlacklightInfectionSystem.isInfected(living)||super.isAlliedTo(other);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new MoveTowardsRestrictionGoal(this,1.1));goalSelector.addGoal(2,new AssaultGoal());goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7));goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,12));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::validTarget));
    }
    private boolean restrained(){return ImpactControl.stunned(this)||ModAttachments.peekData(this,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active()||com.codex.blacklight.power.BiomassSystem.claimant(this)!=null;}
    public boolean beginAttack(MutantMove move,LivingEntity target){
        if(level().isClientSide||!isAlive()||isRemoved()||isNoAi()||restrained()||committedAttack()||cooldown>0
                ||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||!move.species.equals(kind.name())
                ||!validTarget(target)||!hasLineOfSight(target)||!isWithinRestriction(target.blockPosition())
                ||distanceToSqr(target)>Math.pow(move.initiationRange()+target.getBbWidth()*.5,2))return false;
        aim=target.getBoundingBox().getCenter();direction=aim.subtract(position()).multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.01)direction=getLookAngle().multiply(1,0,1).normalize();
        attackYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        if(kind==Kind.STALKER){
            if(move==MutantMove.STALKER_POUNCE){
                var landing=target.position().subtract(direction.scale(.8));
                if((!onGround()&&level().noCollision(this,getBoundingBox().move(0,-.05,0)))||isInWater()
                        ||Math.abs(target.getY()-getY())>2||groundAt(landing,1.1,1.25).getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return false;
            }else if(!StalkerContact.canReach(move,position(),attackYaw,target.getBoundingBox()))return false;
        }
        if(kind==Kind.BRUTE){
            if(move==MutantMove.BRUTE_HAMMER&&!BruteContact.canReach(move,position(),attackYaw,target.getBoundingBox()))return false;
            if((move==MutantMove.BRUTE_SLAM||move==MutantMove.BRUTE_CHARGE)&&(!supportedBody()||isInWater()))return false;
        }
        entityData.set(RECOIL,-1F);entityData.set(GROUND_RECOVERY,false);bruteContact=null;bruteObstacle=null;bruteGround=null;wallImpacts=0;chargeWallRecoil=false;
        committedTarget=target.getUUID();struck.clear();lastProcessed=-1;driveBlocked=false;
        pounceLaunched=false;pounceLanded=false;clawWindow=-1;previousAttackAge=-1;previousAttackPosition=position();groundImpact=null;clawContact=null;
        pounceSpeed=Math.max(.28,Math.min(.62,(aim.subtract(position()).horizontalDistance()-1)/10));
        entityData.set(MOVE,move.ordinal());entityData.set(STARTED,level().getGameTime());getNavigation().stop();
        com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.TENSION);return true;
    }
    public void cancelAttack(){
        if(level().isClientSide)return;
        if(committedAttack())cooldown=Math.max(cooldown,12);
        entityData.set(MOVE,0);entityData.set(RECOIL,-1F);entityData.set(GROUND_RECOVERY,false);committedTarget=null;struck.clear();pounceLaunched=false;clawContact=null;bruteContact=null;bruteObstacle=null;bruteGround=null;getNavigation().stop();
    }
    // Fast lunges otherwise trail their damage by several tracking/interpolation ticks.
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){super.lerpTo(x,y,z,yaw,pitch,kind!=Kind.SPITTER?1:steps,teleport);}
    @Override public void tick(){
        super.tick();if(level().isClientSide||isRemoved()||!ModAttachments.available(this))return;
        if(!isAlive()||isNoAi()||restrained()||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){cancelAttack();return;}
        if(cooldown>0)cooldown--;
        if(com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(this)){cancelAttack();return;}
        var move=attackMove();if(move==MutantMove.NONE)return;
        var target=committedTarget==null?null:((ServerLevel)level()).getEntity(committedTarget);
        if(!(target instanceof LivingEntity living)||!validTarget(living)||!isWithinRestriction(living.blockPosition())){cancelAttack();return;}
        int age=(int)attackAge(0);getNavigation().stop();setYRot(attackYaw);yBodyRot=attackYaw;yHeadRot=attackYaw;
        if(age>=move.ticks&&(kind!=Kind.BRUTE||recoilStart()<0)){entityData.set(MOVE,0);cooldown=move.recovery;committedTarget=null;struck.clear();return;}
        if(age==lastProcessed)return;lastProcessed=age;
        if(kind==Kind.BRUTE){tickBrute(move,age);previousAttackAge=age;previousAttackPosition=position();return;}
        if(move==MutantMove.STALKER_POUNCE)tickPounce(age);
        if(move.driving(age)&&move!=MutantMove.STALKER_POUNCE&&!driveBlocked){
            double speed=-.23;
            Vec3 step=direction.scale(speed),next=position().add(step);
            if(!isWithinRestriction(net.minecraft.core.BlockPos.containing(next))||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),position(),next)
                    ||move==MutantMove.SPITTER_RETREAT&&!safeRetreat(step))driveBlocked=true;
            else {Vec3 before=position();move(MoverType.SELF,step);driveBlocked=position().distanceToSqr(before)<step.lengthSqr()*.15;}
        }
        if(kind==Kind.STALKER){
            for(var w:StalkerContact.windows(move))if(age==(int)w.start()-2)com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.SWING);
            var window=StalkerContact.active(move,previousAttackAge,age);
            if(window!=null&&(move!=MutantMove.STALKER_POUNCE||pounceLaunched&&!driveBlocked))stalkerStrike(move,window,age);
            previousAttackAge=age;previousAttackPosition=position();
        }else if(move.contact(age)){
            if(move.projectile())spit();

        }
    }
    private net.minecraft.world.phys.BlockHitResult groundAt(Vec3 at,double up,double down){
        return level().clip(new net.minecraft.world.level.ClipContext(at.add(0,up,0),at.add(0,-down,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
    }
    private void tickPounce(int age){
        if(age==9&&!pounceLaunched){
            if((!onGround()&&level().noCollision(this,getBoundingBox().move(0,-.05,0)))||isInWater()){driveBlocked=true;return;}
            pounceLaunched=true;setDeltaMovement(direction.scale(pounceSpeed).add(0,.42,0));hasImpulse=true;return;
        }
        if(!pounceLaunched)return;
        if(horizontalCollision&&!driveBlocked){
            driveBlocked=true;var at=getBoundingBox().getCenter();var hit=level().clip(new net.minecraft.world.level.ClipContext(at,at.add(direction.scale(getBbWidth()*.5+.35)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)ContactEffects.obstacle(this,hit.getLocation(),new net.minecraft.world.phys.AABB(hit.getBlockPos()));
        }
        if(age>9&&onGround()&&!pounceLanded){
            pounceLanded=true;var hit=groundAt(position(),.15,.3);
            if(TerrainImpactEffects.emit(this,hit,.65F,false))groundImpact=hit.getLocation();
        }
        if(driveBlocked||pounceLanded)setDeltaMovement(0,getDeltaMovement().y,0);
        else if(age<=19){
            var next=position().add(direction.scale(pounceSpeed));
            if(!isWithinRestriction(net.minecraft.core.BlockPos.containing(next))||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),position(),next)){driveBlocked=true;setDeltaMovement(0,getDeltaMovement().y,0);}
            else setDeltaMovement(direction.x*pounceSpeed,getDeltaMovement().y,direction.z*pounceSpeed);
        }
    }
    private void stalkerStrike(MutantMove move,StalkerContact.Window window,int age){
        if(clawWindow!=window.id()){clawWindow=window.id();struck.clear();}
        var bounds=getBoundingBox().minmax(getBoundingBox().move(previousAttackPosition.subtract(position()))).inflate(1.6);
        for(var victim:level().getEntitiesOfClass(LivingEntity.class,bounds,this::validTarget)){
            if(struck.contains(victim.getUUID()))continue;
            var contact=StalkerContact.contact(move,window,previousAttackAge,age,previousAttackPosition,position(),attackYaw,victim.getBoundingBox());if(contact==null)continue;
            var trace=level().clip(new net.minecraft.world.level.ClipContext(getBoundingBox().getCenter(),contact.point(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
            if(trace.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            struck.add(victim.getUUID());clawContact=contact;
            try{if(victim.hurt(damageSources().mobAttack(this),(float)(getAttributeValue(Attributes.ATTACK_DAMAGE)*move.damage)))victim.knockback(move.push,-direction.x,-direction.z);}finally{clawContact=null;}
        }
    }
    private boolean safeRetreat(Vec3 step){
        var next=getBoundingBox().move(step);
        if(!level().noCollision(this,next))return false;
        var foot=new net.minecraft.world.phys.AABB(next.getCenter().x-.12,next.minY-.55,next.getCenter().z-.12,next.getCenter().x+.12,next.minY+.05,next.getCenter().z+.12);
        return level().getBlockCollisions(this,foot).iterator().hasNext();
    }
    private void spit(){
        // Each release follows the animated nozzle and the aim committed at wind-up.
        // New cover intercepts the physical shot; it cannot move the shot to the far side.
        var mouth=SpitterMouth.sample(attackMove(),attackAge(0),position(),attackYaw);
        var obstruction=level().clip(new net.minecraft.world.level.ClipContext(mouth.throat(),mouth.lip(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
        if(obstruction.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){BlacklightSpit.splash(this,obstruction.getLocation());return;}
        var spit=new BlacklightSpit(level(),this);
        spit.setPos(mouth.lip());
        var shot=BlockThrowBallistics.solve(spit.position(),aim,Vec3.ZERO,.95,.03);
        if(shot==null)return;
        var delta=shot.velocity();
        spit.shoot(delta.x,delta.y,delta.z,.95F,0);level().addFreshEntity(spit);
        com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.LAUNCH,mouth.lip());
    }
    private boolean supportedBody(){return onGround()||!level().noCollision(this,getBoundingBox().move(0,-.05,0));}
    private boolean safeCharge(Vec3 step){
        if(!supportedBody()||isInWater())return false;
        var next=getBoundingBox().move(step);double pad=.14;
        for(double x:new double[]{next.minX+pad,next.maxX-pad})for(double z:new double[]{next.minZ+pad,next.maxZ-pad}){
            var hit=groundAt(new Vec3(x,next.minY,z),.08,.55);
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return false;
        }return true;
    }
    private void finishBrute(){entityData.set(MOVE,0);entityData.set(RECOIL,-1F);entityData.set(GROUND_RECOVERY,false);committedTarget=null;struck.clear();cooldown=26;bruteObstacle=null;bruteGround=null;getNavigation().stop();}
    private void bruteRecoil(HydraContact.Obstacle obstacle,net.minecraft.world.phys.BlockHitResult ground){
        entityData.set(RECOIL,obstacle.safeAge());entityData.set(GROUND_RECOVERY,ground!=null);driveBlocked=true;bruteObstacle=ground==null?obstacle:null;bruteGround=ground;chargeWallRecoil=attackMove()==MutantMove.BRUTE_CHARGE&&ground==null;setDeltaMovement(0,getDeltaMovement().y,0);
    }
    private void tickBrute(MutantMove move,int age){
        if(recoilStart()>=0){
            // The charge's earlier shoulder poses project farther forward. A supported
            // backwards brace gives those visible hands room to retract from the wall.
            if(chargeWallRecoil&&age-recoilStart()<7){
                var back=direction.scale(-.09);var next=position().add(back);
                if(safeCharge(back)&&level().noCollision(this,getBoundingBox().move(back))&&isWithinRestriction(net.minecraft.core.BlockPos.containing(next))
                        &&com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),position(),next))move(MoverType.SELF,back);
            }
            if(age>=recoilStart()){
                if(bruteObstacle!=null){ContactEffects.obstacle(this,bruteObstacle.point(),bruteObstacle.shape());wallImpacts++;bruteObstacle=null;}
                if(bruteGround!=null){
                    if(TerrainImpactEffects.emit(this,bruteGround,2.8F,true)){groundImpact=bruteGround.getLocation();bruteWave(move,groundImpact);}
                    bruteGround=null;
                }
            }
            if(groundRecovery()?age-recoilStart()>=BruteRecovery.TICKS:attackPoseAge(0)<=0)finishBrute();return;
        }
        if(age==(move==MutantMove.BRUTE_CHARGE?10:move==MutantMove.BRUTE_SLAM?18:14))com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.SWING);
        Vec3 step=move==MutantMove.BRUTE_CHARGE&&move.driving(age)?direction.scale(.34):Vec3.ZERO;
        if(step.lengthSqr()>0&&(!safeCharge(step)||!isWithinRestriction(net.minecraft.core.BlockPos.containing(position().add(step)))
                ||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded((ServerLevel)level(),position(),position().add(step)))){
            entityData.set(RECOIL,(float)age);driveBlocked=true;setDeltaMovement(0,getDeltaMovement().y,0);return;
        }
        var origin=position();var end=origin.add(step);var bounds=getBoundingBox().inflate(2.5,2,2.5).expandTowards(step);
        var walls=HydraContact.terrain(this,bounds);
        var obstacle=HydraContact.obstacle(t->BruteContact.boxes(move,(float)t,origin.lerp(end,t-age),attackYaw,false),age,age+1,walls);
        if(obstacle!=null){
            if(step.lengthSqr()>0)move(MoverType.SELF,step.scale(Math.max(0,Math.min(1,obstacle.safeAge()-age))));
            if(move==MutantMove.BRUTE_SLAM&&age>=19&&obstacle.point().y>=obstacle.shape().maxY-.04){
                var point=obstacle.point();var top=groundAt(new Vec3(point.x,obstacle.shape().maxY,point.z),.05,.08);
                if(top.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&top.getDirection()==net.minecraft.core.Direction.UP){bruteRecoil(obstacle,top);return;}
            }
            bruteRecoil(obstacle,null);return;
        }
        if(move==MutantMove.BRUTE_SLAM&&age>=19&&age<=28){
            var floor=new ArrayList<net.minecraft.world.phys.AABB>();
            for(var shape:level().getBlockCollisions(this,bounds))for(var block:shape.toAabbs())floor.add(block);
            var touch=HydraContact.obstacle(t->BruteContact.boxes(move,(float)t,origin,attackYaw,true),age,age+1,floor);
            if(touch!=null){
                var at=touch.point();var hit=groundAt(new Vec3(at.x,touch.shape().maxY,at.z),.05,.08);
                if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP){bruteRecoil(touch,hit);return;}
            }
        }
        if(step.lengthSqr()>0){
            setDeltaMovement(0,getDeltaMovement().y,0);move(MoverType.SELF,step);
            if(position().distanceToSqr(origin)<step.lengthSqr()*.8){entityData.set(RECOIL,(float)age);driveBlocked=true;return;}
        }
        var broad=getBoundingBox().minmax(getBoundingBox().move(previousAttackPosition.subtract(position()))).inflate(2.5);
        for(var victim:level().getEntitiesOfClass(LivingEntity.class,broad,this::validTarget)){
            if(struck.contains(victim.getUUID()))continue;
            var contact=BruteContact.contact(move,Math.max(0,previousAttackAge),age,previousAttackPosition,position(),attackYaw,victim.getBoundingBox());
            if(contact==null||level().clip(new net.minecraft.world.level.ClipContext(getBoundingBox().getCenter(),contact.point(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            bruteHurt(move,victim,contact,direction);
        }
    }
    private void bruteHurt(MutantMove move,LivingEntity victim,BruteContact.Hit contact,Vec3 force){
        struck.add(victim.getUUID());bruteContact=contact;
        try{if(victim.hurt(damageSources().mobAttack(this),(float)(getAttributeValue(Attributes.ATTACK_DAMAGE)*move.damage)))victim.knockback(move.push,-force.x,-force.z);}finally{bruteContact=null;}
    }
    private void bruteWave(MutantMove move,Vec3 at){
        for(var victim:level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(at,at).inflate(2.8,1.5,2.8),this::validTarget)){
            if(struck.contains(victim.getUUID()))continue;
            var foot=groundAt(victim.position(),.12,.3);
            if(foot.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||foot.getDirection()!=net.minecraft.core.Direction.UP||Math.abs(foot.getLocation().y-at.y)>.55)continue;
            var point=foot.getLocation();if(point.distanceToSqr(at)>2.8*2.8)continue;
            if(level().clip(new net.minecraft.world.level.ClipContext(at.add(0,.12,0),point.add(0,.12,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            bruteHurt(move,victim,new BruteContact.Hit("ground_wave",-1,point,(int)attackAge(0)),point.subtract(at).multiply(1,0,1).normalize());
        }
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"body",3,s->{
        if(ModAttachments.peekData(this,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active())return s.setAndContinue(RawAnimation.begin().thenLoop("consume"));
        if(committedAttack())return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(attackMove().clip));
        return s.setAndContinue(RawAnimation.begin().thenLoop(s.isMoving()?"run":"idle"));
    }));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    private final class AssaultGoal extends Goal {
        AssaultGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){
            // A committed swing owns its original aim even if the target selector changes its choice.
            if(committedAttack())return !restrained()&&isWithinRestriction();
            return validTarget(getTarget())&&!restrained()&&isWithinRestriction()&&isWithinRestriction(getTarget().blockPosition());
        }
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null||committedAttack())return;getLookControl().setLookAt(target,30,30);
            var move=MutantMove.choose(kind.name(),distanceTo(target),sequence);
            if(cooldown==0&&beginAttack(move,target)){sequence++;return;}
            double preferred=kind==Kind.SPITTER?10:kind==Kind.STALKER?1.1:1.55;
            if(distanceToSqr(target)>preferred*preferred||!hasLineOfSight(target))getNavigation().moveTo(target,1.1);
            else getNavigation().stop();
        }
        @Override public void stop(){cancelAttack();}
    }
}
