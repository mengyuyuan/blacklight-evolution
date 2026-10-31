package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.combat.HunterConsume;
import com.codex.blacklight.combat.CreatureAttack;
import com.codex.blacklight.combat.HunterBalance;
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
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;

/** A separate species, with an original full-body rig and server-timed heavy swipe. */
public final class BlacklightHunter extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CONSUMER = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CONSUME_TICKS = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> REMAINS_STARTED = SynchedEntityData.defineId(BlacklightHunter.class, EntityDataSerializers.LONG);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean previousNoAi;
    private int attackCooldown;
    private LivingEntity swipeTarget;
    private int attackSequence,evadeCooldown;
    private Vec3 committedDirection=Vec3.ZERO;
    private float committedYaw;
    private long nextLightStagger = Long.MIN_VALUE;

    public BlacklightHunter(EntityType<? extends BlacklightHunter> type, Level level) { super(type, level); xpReward = 12; }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HunterBalance.HEALTH).add(Attributes.MOVEMENT_SPEED, HunterBalance.SPEED)
                .add(Attributes.ARMOR,HunterBalance.ARMOR).add(Attributes.ARMOR_TOUGHNESS,HunterBalance.TOUGHNESS)
                .add(Attributes.ATTACK_DAMAGE, HunterBalance.DAMAGE).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, HunterBalance.KNOCKBACK_RESISTANCE).add(Attributes.STEP_HEIGHT, 1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(ATTACK, 0); b.define(CONSUMER, -1); b.define(CONSUME_TICKS, 0);
        b.define(REMAINS_STARTED, -1L);
        b.define(MOVE,0);b.define(STARTED,0L);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SwipeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 20, true, false,
                e -> (e instanceof net.minecraft.world.entity.animal.Animal || e instanceof net.minecraft.world.entity.npc.AbstractVillager)
                        && !BlacklightInfectionSystem.isInfected(e)));
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide || isBeingConsumed() || !isAlive()) return;
        if(com.codex.blacklight.combat.ImpactControl.stunned(this)||isNoAi()){cancelAttack();return;}
        if (attackCooldown > 0) attackCooldown--;
        if(evadeCooldown>0)evadeCooldown--;
        if(com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(this)){
            if(committedAttack())cancelAttack();
            return;
        }
        int attack = entityData.get(ATTACK);
        if (attack > 0) {
            getNavigation().stop();
            var move=attackMove();int t=move.ticks-attack;
            setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;
            if(move==CreatureAttack.LEAP&&t==8&&onGround())
                setDeltaMovement(committedDirection.scale(.68).add(0,.5,0));
            else if(move==CreatureAttack.DODGE&&t>=3&&t<=12&&onGround())
                setDeltaMovement(committedDirection.scale(.38*Math.sin(Math.PI*(t-2)/11)).add(0,getDeltaMovement().y,0));
            else if(onGround())setDeltaMovement(0,getDeltaMovement().y,0);
            if(move.hitsAt(t))strike(move);
            entityData.set(ATTACK, attack - 1);
            if (attack == 1) {swipeTarget = null;entityData.set(MOVE,0);attackCooldown=move.recovery;}
        }
    }
    public boolean isBeingConsumed() { return entityData.get(CONSUMER) >= 0; }
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
    private void cancelAttack(){entityData.set(ATTACK,0);entityData.set(MOVE,0);swipeTarget=null;attackCooldown=Math.max(attackCooldown,12);}
    public boolean beginAttack(CreatureAttack move,LivingEntity target){
        if(level().isClientSide||move==CreatureAttack.NONE||committedAttack()||isNoAi()||isBeingConsumed()||!isAlive()
                ||target==null||!target.isAlive()||target.level()!=level()||!hasLineOfSight(target))return false;
        swipeTarget=target;var delta=target.position().subtract(position()).multiply(1,0,1);
        committedDirection=delta.lengthSqr()<.001?getLookAngle().multiply(1,0,1).normalize():delta.normalize();
        committedYaw=(float)(Math.toDegrees(Math.atan2(-committedDirection.x,committedDirection.z)));
        if(move==CreatureAttack.DODGE)committedDirection=new Vec3(committedDirection.z,0,-committedDirection.x);
        setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;getNavigation().stop();
        entityData.set(MOVE,move.ordinal());entityData.set(STARTED,level().getGameTime());entityData.set(ATTACK,move.ticks);
        playSound(SoundEvents.RAVAGER_ATTACK,.7F,move==CreatureAttack.SLAM?.7F:1.2F);return true;
    }
    private void strike(CreatureAttack move){
        if(swipeTarget==null||!swipeTarget.isAlive()||swipeTarget.level()!=level())return;
        double reach=move.reach+swipeTarget.getBbWidth()*.5;
        if(distanceToSqr(swipeTarget)>reach*reach||!hasLineOfSight(swipeTarget)
                ||!CreatureAttack.inArc(swipeTarget.getX()-getX(),swipeTarget.getZ()-getZ(),committedYaw,.15))return;
        // Honor vanilla immunity frames and other mods' damage cancellation; never reset victim immunity.
        if(doHurtTarget(swipeTarget)&&move==CreatureAttack.SLAM)swipeTarget.knockback(.7,getX()-swipeTarget.getX(),getZ()-swipeTarget.getZ());
        // Accepted-hit tissue geometry is broadcast by InfectedAttackEffects;
        // a missed/cancelled swipe must not draw a white successful-hit symbol.
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
        // The transient grab must never persist as a permanently disabled AI after restart.
        if (isBeingConsumed()) tag.putBoolean("NoAI", previousNoAi);
        if (isConsumeRemains()) tag.putLong("BlacklightConsumeRemains",entityData.get(REMAINS_STARTED));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
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
        if(tag.contains("BlacklightConsumeRemains",net.minecraft.nbt.Tag.TAG_LONG)) {
            entityData.set(REMAINS_STARTED,Math.max(0,tag.getLong("BlacklightConsumeRemains")));
            setHealth(0);setNoAi(true);
        }
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "body", 3, s -> {
            if(!isBeingConsumed()&&entityData.get(ATTACK)>0)return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(attackMove().clip));
            String clip=isBeingConsumed()?"consume":s.isMoving()?(getDeltaMovement().horizontalDistanceSqr()>.014?"run":"walk"):"idle";
            return s.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    private final class SwipeGoal extends Goal {
        SwipeGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return !isBeingConsumed() && getTarget() != null && getTarget().isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            LivingEntity target = getTarget(); if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (entityData.get(ATTACK) > 0) return;
            getNavigation().moveTo(target, 1.15);
            double distance=distanceTo(target);
            if (attackCooldown == 0 && distance<10 && hasLineOfSight(target)) {
                boolean evade=evadeCooldown==0&&target instanceof Player p&&p.swinging&&distance<4&&random.nextFloat()<.35F;
                var move=evade?CreatureAttack.DODGE:CreatureAttack.choose(distance,attackSequence);
                if((distance<3.5||move==CreatureAttack.LEAP||evade)&&beginAttack(move,target)){
                    attackSequence++;if(evade)evadeCooldown=100;
                }
            }
        }
        @Override public void stop() { getNavigation().stop(); }
    }
}
