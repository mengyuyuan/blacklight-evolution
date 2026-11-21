package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.combat.HunterConsume;
import com.codex.blacklight.combat.CreatureAttack;
import com.codex.blacklight.combat.HunterContactPose;
import com.codex.blacklight.combat.HunterBalance;
import com.codex.blacklight.combat.HunterCounterMotion;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;

/** A separate species, with an original full-body rig and server-timed heavy swipe. */
public final class BlacklightHunter extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> COUNTER_RELEASE = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CONSUMER = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CONSUME_TICKS = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> REMAINS_STARTED = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.LONG);
    /** Infected Hunter (Supreme Parasite injected): 0 none, 1 infected, 2 locked at its last point of health. */
    private static final EntityDataAccessor<Integer> PARASITE = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean previousNoAi;
    private int attackCooldown;
    private LivingEntity swipeTarget;
    private int attackSequence,evadeCooldown;
    private Vec3 committedDirection=Vec3.ZERO;
    private float committedYaw;
    private long nextLightStagger = Long.MIN_VALUE;
    private final boolean[] connectedWindows=new boolean[5];
    private float previousAttackAge=-1;
    private Vec3 previousAttackPosition=Vec3.ZERO;
    private Vec3 damageContact;
    private Vec3 groundImpact;
    private long attackEpoch;

    public BlacklightHunter(EntityType<? extends BlacklightHunter> type, Level level) { super(type, level); xpReward = 12; }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HunterBalance.HEALTH).add(Attributes.MOVEMENT_SPEED, HunterBalance.SPEED)
                .add(Attributes.ARMOR,HunterBalance.ARMOR).add(Attributes.ARMOR_TOUGHNESS,HunterBalance.TOUGHNESS)
                .add(Attributes.ATTACK_DAMAGE, HunterBalance.DAMAGE).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, HunterBalance.KNOCKBACK_RESISTANCE).add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(), .4);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(ATTACK, 0); entityData.define(CONSUMER, -1); entityData.define(CONSUME_TICKS, 0);
        entityData.define(REMAINS_STARTED, -1L);
        entityData.define(PARASITE,0);
        entityData.define(MOVE,0);entityData.define(STARTED,0L);entityData.define(COUNTER_RELEASE,false);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SwipeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::validCombatTarget));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, MilitarySoldier.class, 10, true, false, this::validCombatTarget));
        targetSelector.addGoal(4, new HuntPreyGoal());
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || isBeingConsumed() || !isAlive()) return;
        if(parasiteLocked()){
            // Endless stagger: refresh the heavy hit pose and pin it in place.
            getNavigation().stop();setTarget(null);setDeltaMovement(0,getDeltaMovement().y,0);if(committedAttack())cancelAttack();
            var hit=com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.HIT_REACTION);long now=level().getGameTime();
            if(hit.duration()<=0||now-hit.started()>=hit.duration()-2){com.codex.blacklight.registry.ModAttachments.setData(this,com.codex.blacklight.registry.ModAttachments.HIT_REACTION,new com.codex.blacklight.combat.HitReaction(now,30,com.codex.blacklight.combat.HitReaction.HEAVY,getYRot()));com.codex.blacklight.registry.ModAttachments.syncData(this,com.codex.blacklight.registry.ModAttachments.HIT_REACTION);}
            if(tickCount%20==0&&level() instanceof net.minecraft.server.level.ServerLevel s)s.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.75F,.05F,.55F),1.6F),getX(),getY()+1.2,getZ(),8,.4,.6,.4,.02);
            return;
        }
        if(com.codex.blacklight.combat.ImpactControl.stunned(this)||isNoAi()){cancelAttack();return;}
        if (attackCooldown > 0) attackCooldown--;
        if(evadeCooldown>0)evadeCooldown--;
        if(com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(this)){
            if(committedAttack())cancelAttack();
            return;
        }
        int attack = entityData.get(ATTACK);
        if (attack > 0) {
            if(!validCombatTarget(swipeTarget)){cancelAttack();return;}
            getNavigation().stop();
            var move=attackMove();float age=attackAge(0);int t=(int)age;
            long epoch=attackEpoch;
            if(HunterCounterMotion.uses(move,counterBarrage())&&age<=previousAttackAge)return;
            setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;
            if(move==CreatureAttack.LEAP&&t==8&&onGround())
                setDeltaMovement(committedDirection.scale(.68).add(0,.5,0));
            else if(move==CreatureAttack.DODGE&&t>=3&&t<=12&&onGround())
                setDeltaMovement(committedDirection.scale(.38*Math.sin(Math.PI*(t-2)/11)).add(0,getDeltaMovement().y,0));
            else if(onGround())setDeltaMovement(0,getDeltaMovement().y,0);
            if(HunterCounterMotion.uses(move,counterBarrage())&&!advanceCounterRoot(move,age))return;
            if(epoch!=attackEpoch||!isAlive()||isBeingConsumed()||isRemoved())return;
            com.codex.blacklight.infection.HunterSoundTimeline.between(move,previousAttackAge,age,this::playAttackCue);
            if(epoch!=attackEpoch||!isAlive()||isBeingConsumed()||isRemoved())return;
            var windows=move.windows();
            for(int i=0;i<windows.length;i++){
                var window=windows[i];
                if(!com.codex.blacklight.infection.HunterSoundTimeline.owns(move)&&previousAttackAge<window.from()*20&&age>=window.from()*20)
                    com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.SWING);
                if(!connectedWindows[i]&&window.overlaps(previousAttackAge,age))strike(move,window,i,age);
                if(epoch!=attackEpoch||!isAlive()||isBeingConsumed()||isRemoved())return;
            }
            if(move==CreatureAttack.SLAM&&groundImpact==null&&age>=14&&age<=17)slamGround(age);
            previousAttackAge=age;previousAttackPosition=position();
            entityData.set(ATTACK,Math.max(0,move.ticks-t));
            if(t>=move.ticks){
                if(move==CreatureAttack.COUNTER&&releaseCounter())return;
                attackEpoch++;
                swipeTarget=null;entityData.set(MOVE,0);entityData.set(COUNTER_RELEASE,false);attackCooldown=move.recovery;
            }
        }
    }
    public boolean isBeingConsumed() { return entityData.get(CONSUMER) >= 0; }
    public boolean parasite(){return entityData.get(PARASITE)>0;}
    /** Last point of health: endless stagger, health locked, waiting to be consumed. */
    public boolean parasiteLocked(){return entityData.get(PARASITE)==2;}
    public void infectParasite(){
        if(parasite())return;entityData.set(PARASITE,1);setCustomName(net.minecraft.network.chat.Component.translatable("entity.blacklight.infected_hunter"));
        setPersistenceRequired();
        var health=getAttribute(Attributes.MAX_HEALTH);if(health!=null){float f=getHealth()/getMaxHealth();health.setBaseValue(health.getBaseValue()*1.5);setHealth(getMaxHealth()*f);}
        var damage=getAttribute(Attributes.ATTACK_DAMAGE);if(damage!=null)damage.setBaseValue(damage.getBaseValue()*1.25);
        if(level() instanceof net.minecraft.server.level.ServerLevel s)s.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.75F,.05F,.55F),2F),getX(),getY()+1.5,getZ(),60,.6,1,.6,.05);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        // A locked Infected Hunter only leaves through consumption (or /kill).
        if(parasiteLocked()&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !com.codex.blacklight.combat.ConsumeContactDamage.owns(this,source))return false;
        return super.hurt(source,amount);
    }
    @Override public void setHealth(float health){
        if(parasite()&&!isConsumeRemains()&&health<1&&!level().isClientSide&&!isBeingConsumed()&&isAlive()){
            super.setHealth(1);
            if(!parasiteLocked()){entityData.set(PARASITE,2);cancelAttack();getNavigation().stop();setTarget(null);playSound(net.minecraft.sounds.SoundEvents.RAVAGER_STUNNED,1.5F,.7F);}
            return;
        }
        super.setHealth(health);
    }
    public boolean committedAttack(){return entityData.get(ATTACK)>0;}
    /** Light hits still hurt; only repeated AI interruption is resisted. Heavy controls bypass this. */
    public boolean acceptLightStagger() {
        long now=level().getGameTime();
        if(now<nextLightStagger)return false;
        nextLightStagger=now+HunterBalance.LIGHT_STAGGER_INTERVAL;
        return true;
    }
    public int attackTicksRemaining(){return entityData.get(ATTACK);}
    public CreatureAttack attackMove(){return CreatureAttack.byId(entityData.get(MOVE));}
    public float attackAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial);}
    public boolean counterBarrage(){return entityData.get(COUNTER_RELEASE);}
    private void cancelAttack(){attackEpoch++;entityData.set(ATTACK,0);entityData.set(MOVE,0);entityData.set(COUNTER_RELEASE,false);swipeTarget=null;attackCooldown=Math.max(attackCooldown,12);previousAttackAge=-1;previousAttackPosition=position();}
    private boolean advanceCounterRoot(CreatureAttack move,float age){
        // Never replay unobserved source travel or sweep claws across a teleport.
        // Loaded attacks are canceled below, rather than stored as root debt.
        if(previousAttackAge<0||age-previousAttackAge>1.01F||previousAttackPosition.distanceToSqr(position())>64){cancelAttack();return false;}
        var step=HunterCounterMotion.step(move,counterBarrage(),previousAttackAge,age,committedYaw);
        var swept=getBoundingBox().minmax(getBoundingBox().move(step));var border=level().getWorldBorder();
        if(!isWithinRestriction(net.minecraft.core.BlockPos.containing(position().add(step)))
                ||!com.codex.blacklight.military.vehicle.VehicleKinematics.loaded((net.minecraft.server.level.ServerLevel)level(),swept)
                ||swept.minX<border.getMinX()||swept.maxX>border.getMaxX()||swept.minZ<border.getMinZ()||swept.maxZ>border.getMaxZ()){
            cancelAttack();return false;
        }
        // Vanilla actual collision shapes and step handling decide accepted
        // travel. The next source delta starts from this resulting position,
        // never from an accumulated desired endpoint behind the obstacle.
        if(step.lengthSqr()>1e-12){
            long epoch=attackEpoch;boolean grounded=onGround();move(MoverType.SELF,step);
            if(epoch!=attackEpoch||!isAlive()||isRemoved()||isBeingConsumed())return false;
            // A second, horizontal-only Entity.move clears vanilla's ground
            // flag because requestedY=0. Restore it only when the accepted
            // footprint still touches a real collision surface below it.
            if(grounded){
                boolean supported=!level().noCollision(this,getBoundingBox().move(0,-.01,0));
                if(epoch!=attackEpoch||!isAlive()||isRemoved()||isBeingConsumed())return false;
                setOnGround(supported);
            }
        }
        return true;
    }
    private boolean validCombatTarget(LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.level()==level()&&canAttack(target)&&!isAlliedTo(target)
                &&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))
                &&(target instanceof Player||!BlacklightInfectionSystem.isInfected(target))
                &&!com.codex.blacklight.combat.ConsumeControl.protectedEntity(target);
    }
    private boolean releaseCounter(){
        // Continue only the original commitment: no new target, no snap-turn, no deferred task.
        if(!validCombatTarget(swipeTarget)||!hasLineOfSight(swipeTarget)||!onGround()||isBeingConsumed()||isNoAi()
                ||parasiteLocked()||com.codex.blacklight.combat.ImpactControl.stunned(this)
                ||swipeTarget.getVehicle() instanceof MilitaryVehicle||swipeTarget.getVehicle() instanceof BlackwatchHelicopter
                ||!HunterContactPose.canReach(CreatureAttack.BARRAGE,position(),committedYaw,swipeTarget.getBoundingBox(),true))return false;
        int entry=CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK;
        attackEpoch++;
        entityData.set(MOVE,CreatureAttack.BARRAGE.ordinal());entityData.set(STARTED,level().getGameTime()-entry);
        entityData.set(ATTACK,CreatureAttack.BARRAGE.ticks-entry);entityData.set(COUNTER_RELEASE,true);
        // The opening and the five release contacts are distinct source windows. Vanilla victim
        // immunity is retained, and the new stage is not evaluated again on this transition tick.
        java.util.Arrays.fill(connectedWindows,false);previousAttackAge=entry;previousAttackPosition=position();
        // Entry is consumed here, so next tick cannot replay it or resurrect the opening grunt.
        com.codex.blacklight.infection.HunterSoundTimeline.between(CreatureAttack.BARRAGE,entry-1,entry,this::playAttackCue);
        getNavigation().stop();return true;
    }
    private void playAttackCue(com.codex.blacklight.infection.HunterSoundTimeline.Cue cue){
        com.codex.blacklight.registry.ModInfectedSounds.play(this,cue.sound(),getBoundingBox().getCenter(),cue.gain(),cue.minPitch(),cue.maxPitch());
    }
    public boolean beginAttack(CreatureAttack move,LivingEntity target){
        if(level().isClientSide||move==CreatureAttack.NONE||committedAttack()||attackCooldown>0||isNoAi()||isBeingConsumed()||!isAlive()
                ||parasiteLocked()||com.codex.blacklight.combat.ImpactControl.stunned(this)
                ||!validCombatTarget(target)||!hasLineOfSight(target)
                ||distanceTo(target)>(move==CreatureAttack.LEAP?10:move==CreatureAttack.DODGE?4:3.5))return false;
        swipeTarget=target;var delta=target.position().subtract(position()).multiply(1,0,1);
        attackEpoch++;
        committedDirection=delta.lengthSqr()<.001?getLookAngle().multiply(1,0,1).normalize():delta.normalize();
        committedYaw=(float)(Math.toDegrees(Math.atan2(-committedDirection.x,committedDirection.z)));
        if(move==CreatureAttack.DODGE)committedDirection=new Vec3(committedDirection.z,0,-committedDirection.x);
        setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;getNavigation().stop();
        entityData.set(MOVE,move.ordinal());entityData.set(STARTED,level().getGameTime());entityData.set(ATTACK,move.ticks);entityData.set(COUNTER_RELEASE,false);
        if(HunterCounterMotion.uses(move,false))setDeltaMovement(0,getDeltaMovement().y,0);
        java.util.Arrays.fill(connectedWindows,false);previousAttackAge=-1;previousAttackPosition=position();groundImpact=null;
        if(com.codex.blacklight.infection.HunterSoundTimeline.owns(move)){
            com.codex.blacklight.infection.HunterSoundTimeline.between(move,-1,0,this::playAttackCue);previousAttackAge=0;
        }else{
            com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.TENSION);
            playSound(SoundEvents.RAVAGER_ATTACK,.7F,move==CreatureAttack.SLAM?.7F:1.2F);
        }return true;
    }
    /** Only populated during the synchronous accepted-damage event, never cached on the victim. */
    public Vec3 damageContact(){return damageContact;}
    public Vec3 groundImpactPoint(){return groundImpact;}
    private void strike(CreatureAttack move,CreatureAttack.Window window,int index,float age){
        if(swipeTarget==null||!swipeTarget.isAlive()||swipeTarget.level()!=level())return;
        if(!hasLineOfSight(swipeTarget))return;
        var contact=HunterContactPose.contact(move,window,previousAttackAge,age,previousAttackPosition,position(),committedYaw,swipeTarget.getBoundingBox());
        if(contact==null)return;
        // Honor vanilla immunity frames and other mods' damage cancellation; never reset victim immunity.
        damageContact=contact;
        long epoch=attackEpoch;LivingEntity victim=swipeTarget;
        try{if(doHurtTarget(victim)&&epoch==attackEpoch&&swipeTarget==victim&&isAlive()&&!isRemoved()){
            connectedWindows[index]=true;
            if(move==CreatureAttack.SLAM)victim.knockback(.7,getX()-victim.getX(),getZ()-victim.getZ());
        }}finally{damageContact=null;}
        // Accepted-hit tissue geometry is broadcast by InfectedAttackEffects;
        // a missed/cancelled swipe must not draw a white successful-hit symbol.
    }
    private void slamGround(float age){
        if(!(level() instanceof net.minecraft.server.level.ServerLevel level))return;
        net.minecraft.world.phys.BlockHitResult hit=null;
        float from=Math.max(14,previousAttackAge);
        // The lowest point can fall between two 20 Hz ticks. Sweep the palm as
        // well as the claw tips; a tip-only ray misses the fist contacting flat ground.
        search:for(int step=0;step<=4;step++){
            float t=from+(age-from)*step/4;
            var hand=HunterContactPose.hand(CreatureAttack.SLAM,t,false,previousAttackPosition.lerp(position(),age==previousAttackAge?1:(t-previousAttackAge)/(age-previousAttackAge)),committedYaw);
            for(int point=0;point<=2;point++){
                var center=hand.palm().lerp(hand.tip(),point*.5);
                var candidate=level.clip(new net.minecraft.world.level.ClipContext(center.add(0,hand.radius(),0),center.add(0,-hand.radius(),0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                if(candidate.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&candidate.getDirection()==net.minecraft.core.Direction.UP){hit=candidate;break search;}
            }
        }
        if(hit==null)return;
        groundImpact=hit.getLocation();var state=level.getBlockState(hit.getBlockPos());
        com.codex.blacklight.combat.TerrainImpactEffects.emit(this,hit,2.4F,true);
        // The FIG has a separate low-damage ground radius. MC adapts its scale to the
        // authored hand and terrain; the primary fist and ground pulse cannot double-hit.
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(groundImpact,groundImpact).inflate(2,.8,2),v->v!=this&&v.isAlive()&&canAttack(v))){
            if((victim==swipeTarget&&connectedWindows[0])||(!(victim instanceof Player)&&BlacklightInfectionSystem.isInfected(victim))||!hasLineOfSight(victim))continue;
            var at=HunterContactPose.nearest(victim.getBoundingBox(),groundImpact);
            if(at.distanceToSqr(groundImpact)>4)continue;
            damageContact=at;try{
                if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE)/6)){
                    if(victim==swipeTarget)connectedWindows[0]=true;
                    victim.knockback(.35,groundImpact.x-victim.getX(),groundImpact.z-victim.getZ());
                }
            }finally{damageContact=null;}
        }
    }
    public int consumerId() { return entityData.get(CONSUMER); }
    public int consumeTicks() { return entityData.get(CONSUME_TICKS); }
    public void setConsumeTicks(int ticks) { entityData.set(CONSUME_TICKS, ticks); }
    public boolean claim(Player player) {
        if (isBeingConsumed() || !isAlive()) return false;
        previousNoAi = isNoAi(); setNoAi(true); getNavigation().stop(); setTarget(null);
        cancelAttack();
        entityData.set(CONSUMER, player.getId()); entityData.set(CONSUME_TICKS, 0); return true;
    }
    public void release() {
        if (!isBeingConsumed()) return;
        setNoAi(previousNoAi); entityData.set(CONSUMER, -1); entityData.set(CONSUME_TICKS, 0);
    }
    public boolean isConsumeRemains() { return entityData.get(REMAINS_STARTED)>=0; }
    public float consumeRemainsAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(REMAINS_STARTED)+partial); }
    public void beginConsumeRemains() {
        if(level().isClientSide||isConsumeRemains())return;
        release();setNoAi(true);getNavigation().stop();setTarget(null);
        cancelAttack();
        entityData.set(REMAINS_STARTED,level().getGameTime());
        // Consumption has its own reward path. Do not call die() and drop loot/XP again.
        setHealth(0);setDeltaMovement(0,0,0);deathTime=0;
    }
    @Override protected void tickDeath() {
        if(!isConsumeRemains()){super.tickDeath();return;}
        // The receiver clip owns the collapse; suppress the generic sideways death roll.
        deathTime=0;
        if(!level().isClientSide&&consumeRemainsAge(0)>=HunterConsume.REMAINS_TICKS)discard();
    }
    @Override public boolean isPushable() { return !isConsumeRemains()&&super.isPushable(); }
    @Override public boolean isPickable() { return !isConsumeRemains()&&super.isPickable(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BlacklightHunterBalanceVersion",HunterBalance.VERSION);
        tag.putInt("BlacklightParasite",entityData.get(PARASITE));
        // The transient grab must never persist as a permanently disabled AI after restart.
        if (isBeingConsumed()) tag.putBoolean("NoAI", previousNoAi);
        if (isConsumeRemains()) tag.putLong("BlacklightConsumeRemains",entityData.get(REMAINS_STARTED));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        // Attack/target/root progress is deliberately transient. Loading into
        // this same object must not resume a stale counter or its release.
        cancelAttack();
        super.readAdditionalSaveData(tag);
        restoreLegacyBrawlerAttributes(tag);
        // Upgrade only the old stock attributes. Preserve custom mobs and the current health fraction.
        double oldHealth=getAttribute(Attributes.MAX_HEALTH).getBaseValue(),oldDamage=getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
        boolean stock=oldHealth==48&&oldDamage==7||oldHealth==96&&oldDamage==14;
        if(tag.getInt("BlacklightHunterBalanceVersion")<HunterBalance.VERSION&&stock) {
            float maximum=getMaxHealth(),health=getHealth();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(HunterBalance.HEALTH);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(HunterBalance.DAMAGE);
            if(getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()==.29)getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HunterBalance.SPEED);
            if(getAttribute(Attributes.ARMOR).getBaseValue()==0)getAttribute(Attributes.ARMOR).setBaseValue(HunterBalance.ARMOR);
            if(getAttribute(Attributes.ARMOR_TOUGHNESS).getBaseValue()==0)getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(HunterBalance.TOUGHNESS);
            if(getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==.55||getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==.65)getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(HunterBalance.KNOCKBACK_RESISTANCE);
            if(getAttribute(Attributes.FOLLOW_RANGE).getBaseValue()==24)getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32);
            com.codex.blacklight.power.CombatScale.preserveHealthFraction(this,maximum,health);
        }
        entityData.set(PARASITE,net.minecraft.util.Mth.clamp(tag.getInt("BlacklightParasite"),0,2));
        if(tag.contains("BlacklightConsumeRemains",net.minecraft.nbt.Tag.TAG_LONG)) {
            entityData.set(REMAINS_STARTED,Math.max(0,tag.getLong("BlacklightConsumeRemains")));
            setHealth(0);setNoAi(true);
        }
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return com.codex.blacklight.registry.ModInfectedSounds.event(com.codex.blacklight.infection.InfectedSoundCue.HUNTER_HURT); }
    /** Saved duplicate Brawlers keep their identity and injuries while joining the original species. */
    private void restoreLegacyBrawlerAttributes(CompoundTag tag) {
        if(tag.contains("BlacklightHunterBalanceVersion")||!(tag.contains("EncounterPhase")||"blacklight:brawler".equals(tag.getString("id"))))return;
        float maximum=getMaxHealth(),health=getHealth();
        if(getAttribute(Attributes.MAX_HEALTH).getBaseValue()==100)getAttribute(Attributes.MAX_HEALTH).setBaseValue(HunterBalance.HEALTH);
        if(getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()==9)getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(HunterBalance.DAMAGE);
        if(getAttribute(Attributes.ARMOR).getBaseValue()==0)getAttribute(Attributes.ARMOR).setBaseValue(HunterBalance.ARMOR);
        if(getAttribute(Attributes.ARMOR_TOUGHNESS).getBaseValue()==0)getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(HunterBalance.TOUGHNESS);
        if(getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==.4)getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(HunterBalance.KNOCKBACK_RESISTANCE);
        if(getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).getBaseValue()==0)getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).setBaseValue(.4);
        com.codex.blacklight.power.CombatScale.preserveHealthFraction(this,maximum,health);
    }
    @Override protected SoundEvent getDeathSound() { return com.codex.blacklight.registry.ModInfectedSounds.event(com.codex.blacklight.infection.InfectedSoundCue.HUNTER_DEATH); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "body", 3, s -> {
            if(!isBeingConsumed()&&entityData.get(ATTACK)>0)return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(attackMove().clip));
            String clip=isBeingConsumed()?"consume":s.isMoving()?(getDeltaMovement().horizontalDistanceSqr()>.014?"run":"walk"):"idle";
            return s.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    private final class HuntPreyGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private long nextSearchTick;

        HuntPreyGoal() {
            super(BlacklightHunter.this, LivingEntity.class, 0, true, false,
                    candidate -> (candidate instanceof net.minecraft.world.entity.animal.Animal
                            || candidate instanceof net.minecraft.world.entity.npc.AbstractVillager)
                            && BlacklightHunter.this.validCombatTarget(candidate));
        }

        @Override public boolean canUse() {
            long now = level().getGameTime();
            if (now < nextSearchTick) return false;
            // Recheck exposed prey, including passengers who have reboarded, once per second.
            nextSearchTick = now + 20;
            return super.canUse();
        }
    }

    private final class SwipeGoal extends Goal {
        private LivingEntity quarry;
        private Vec3 lastSeen;
        private long seenAt, nextPath;

        SwipeGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return !isBeingConsumed() && validCombatTarget(getTarget()); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            LivingEntity target = getTarget(); if (target == null) return;
            if (entityData.get(ATTACK) > 0) return;
            if (target != quarry) {
                quarry = target;
                lastSeen = null;
                nextPath = 0;
            }
            long now = level().getGameTime();
            boolean visible = getSensing().hasLineOfSight(target);
            if (visible) {
                lastSeen = target.position();
                seenAt = now;
                getLookControl().setLookAt(target, 30, 30);
            } else if (lastSeen == null || now - seenAt >= 60 || position().distanceToSqr(lastSeen) < 1) {
                setTarget(null);
                getNavigation().stop();
                return;
            }
            if (now >= nextPath) {
                getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, 1.15);
                nextPath = now + 6;
            }
            double distance=distanceTo(target);
            if (attackCooldown == 0 && distance<10 && visible) {
                boolean evade=evadeCooldown==0&&target instanceof Player p&&p.swinging&&distance<4&&random.nextFloat()<.35F;
                var move=evade?CreatureAttack.DODGE:CreatureAttack.choose(distance,attackSequence);
                float facing=(float)Math.toDegrees(Math.atan2(getX()-target.getX(),target.getZ()-getZ()));
                boolean reachable=move==CreatureAttack.LEAP||evade||HunterContactPose.canReach(move,position(),facing,target.getBoundingBox());
                if(reachable&&(distance<3.5||move==CreatureAttack.LEAP||evade)&&beginAttack(move,target)){
                    attackSequence++;if(evade)evadeCooldown=100;
                }
            }
        }
        @Override public void stop() {
            quarry = null;
            lastSeen = null;
            getNavigation().stop();
        }
    }
}
