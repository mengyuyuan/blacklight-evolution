package com.codex.blacklight.entity;

import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.CommonInfectedMotion;
import net.minecraft.world.level.Level;

/** Replenishable food tier; real meals and surplus-cohort development share one safe promotion. */
public final class BlacklightCommonInfected extends Monster {
    private static final EntityDataAccessor<Long> ATTACK_STARTED=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ATTACK_HAND=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> APPEARANCE=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.INT);
    private int meals;
    private boolean promoting;
    public BlacklightCommonInfected(EntityType<? extends BlacklightCommonInfected> type, Level level) { super(type, level); xpReward=3; }
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(ATTACK_STARTED,-1L);entityData.define(ATTACK_HAND,0);entityData.define(APPEARANCE,-1);}
    public long attackStarted(){return com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()?-1:entityData.get(ATTACK_STARTED);}
    public void cancelAttack(){if(!level().isClientSide){entityData.set(ATTACK_STARTED,-1L);setAggressive(false);}}
    public int attackHand(){return entityData.get(ATTACK_HAND);}
    public int appearance(){return entityData.get(APPEARANCE)<0?CommonInfectedMotion.appearance(getUUID().getMostSignificantBits(),getUUID().getLeastSignificantBits()):entityData.get(APPEARANCE);}
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,12).add(Attributes.ATTACK_DAMAGE,2)
                .add(Attributes.ARMOR,0).add(Attributes.MOVEMENT_SPEED,.21).add(Attributes.FOLLOW_RANGE,32);
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new CivilianAttackGoal());
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.70));
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::validTarget));
    }
    public boolean validTarget(LivingEntity target){
        if(target==null||target==this||!target.isAlive()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()&&!com.codex.blacklight.disguise.DisguiseSystem.fooled(this,p);
        return target instanceof Mob&&!BlacklightInfectionSystem.isInfected(target);
    }
    @Override public boolean isAlliedTo(Entity other){return other instanceof LivingEntity living&&!(other instanceof Player)&&BlacklightInfectionSystem.isInfected(living)||super.isAlliedTo(other);}
    private boolean restrained(){return com.codex.blacklight.combat.ImpactControl.stunned(this)
            ||com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
            ||com.codex.blacklight.power.BiomassSystem.claimant(this)!=null;}
    /** Shared availability gate for the existing civilian attack and lawful mechanical pursuit. */
    public boolean combatAvailable(){return !restrained();}
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damage){return net.minecraft.sounds.SoundEvents.PLAYER_HURT;}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return net.minecraft.sounds.SoundEvents.PLAYER_DEATH;}
    private final class CivilianAttackGoal extends Goal{
        private int cooldown;private LivingEntity committed;
        CivilianAttackGoal(){setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public boolean canUse(){return getTarget()!=null&&validTarget(getTarget())&&combatAvailable();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void stop(){entityData.set(ATTACK_STARTED,-1L);setAggressive(false);getNavigation().stop();committed=null;}
        @Override public void tick(){
            var target=getTarget();if(target==null)return;
            if(cooldown>0)cooldown--;
            setAggressive(true);getLookControl().setLookAt(target,25,25);
            long start=attackStarted(),elapsed=start<0?-1:level().getGameTime()-start;
            if(start>=0){
                getNavigation().stop();
                if(target!=committed||hurtTime>0||restrained()){entityData.set(ATTACK_STARTED,-1L);cooldown=10;committed=null;return;}
                if(elapsed==6&&onGround()&&getSensing().hasLineOfSight(target)){
                    var toward=target.position().subtract(position()).multiply(1,0,1).normalize().scale(.18);
                    setDeltaMovement(toward.x,getDeltaMovement().y,toward.z);hurtMarked=true;
                }
                if(elapsed==CommonInfectedMotion.CONTACT_TICK&&validTarget(target)&&getSensing().hasLineOfSight(target)
                        &&getBoundingBox().inflate(.85,.25,.85).intersects(target.getBoundingBox())){
                    swing(entityData.get(ATTACK_HAND)==0?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND);
                    doHurtTarget(target);
                }
                if(elapsed>=CommonInfectedMotion.ATTACK_TICKS){entityData.set(ATTACK_STARTED,-1L);cooldown=8;committed=null;}
            }else if(cooldown==0&&hurtTime==0&&onGround()&&distanceToSqr(target)<5&&getSensing().hasLineOfSight(target)){
                getNavigation().stop();committed=target;entityData.set(ATTACK_HAND,1-attackHand());entityData.set(ATTACK_STARTED,level().getGameTime());
            }else getNavigation().moveTo(target,1.25);
        }
    }
    @Override public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean accepted=super.killedEntity(level,victim);
        if(accepted&&!victim.isAlive()&&victim!=this&&victim.getLastHurtByMob()==this
                &&!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(victim))meals=Math.min(3,meals+1);
        return accepted;
    }
    @Override public void tick() {
        super.tick();
        // Capture sets NoAI, so the stopped goal cannot clear its own pending pose until release.
        if(!level().isClientSide&&(isNoAi()||restrained()))cancelAttack();
        if(!(level() instanceof ServerLevel level)||!isAlive()||meals<3||tickCount%100!=0
                ||com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
                ||com.codex.blacklight.combat.ImpactControl.stunned(this))return;
        promote();
    }
    /** Same-body promotion, never a random replacement species, death or extra scheduled birth. */
    public com.codex.blacklight.entity.BlacklightInfectedHost promote() {
        if(!(level() instanceof ServerLevel level)||promoting||!isAlive()||isRemoved()||isBaby()||isPassenger()||isVehicle()||isLeashed()
                ||com.codex.blacklight.registry.ModAttachments.getData(this,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
                ||com.codex.blacklight.power.BiomassSystem.claimant(this)!=null
                ||com.codex.blacklight.combat.ImpactControl.stunned(this))return null;
        var host=ModEntities.INFECTED_HOST.get().create(level);
        if(host==null)return null;
        host.copyPosition(this);host.inherit(this);host.advanceTissue(6000);
        if(!level.noCollision(host)||!level.getWorldBorder().isWithinBounds(host.getBoundingBox()))return null;
        host.setHealth(host.getMaxHealth()*getHealth()/getMaxHealth());
        host.setCustomName(getCustomName());host.setCustomNameVisible(isCustomNameVisible());
        getTags().forEach(host::addTag);
        if(getPersistentData().getBoolean("blacklight:outbreak_spawn"))host.getPersistentData().putBoolean("blacklight:outbreak_spawn",true);
        promoting=true;
        try{
            if(!level.addFreshEntity(host))return null;
            if(!isAlive()||isRemoved()){host.discard();return null;}
            for(var effect:getActiveEffects())host.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
            for(var slot:EquipmentSlot.values())host.setItemSlot(slot,getItemBySlot(slot).copyAndClear());
            discard();return host;
        }finally{promoting=false;}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag);tag.putInt("BlacklightMeals",meals);tag.putInt("BlacklightCivilianAppearance",appearance()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag);meals=com.codex.blacklight.forge.WingMath.clamp(tag.getInt("BlacklightMeals"),0,3);entityData.set(APPEARANCE,tag.contains("BlacklightCivilianAppearance")?com.codex.blacklight.forge.WingMath.clamp(tag.getInt("BlacklightCivilianAppearance"),0,3):-1); }
}
