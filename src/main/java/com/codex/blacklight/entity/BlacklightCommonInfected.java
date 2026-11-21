package com.codex.blacklight.entity;

import com.codex.blacklight.combat.GreeneMolt;

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
public final class BlacklightCommonInfected extends Monster implements GreeneMolt.Captive {
    private static final EntityDataAccessor<Long> ATTACK_STARTED=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ATTACK_HAND=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> APPEARANCE=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> LEAP_PUNCH=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> LEAP_LANDED=SynchedEntityData.defineId(BlacklightCommonInfected.class,EntityDataSerializers.LONG);
    private int meals;
    private long attackGeneration;
    private boolean promoting,leapFlight,leapExitTravel;
    private final com.codex.blacklight.infection.CommonInfectedLeap.RootExit leapExit=new com.codex.blacklight.infection.CommonInfectedLeap.RootExit();
    public BlacklightCommonInfected(EntityType<? extends BlacklightCommonInfected> type, Level level) { super(type, level); xpReward=3; }
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MOLT_CAPTIVE=
            SynchedEntityData.defineId(BlacklightCommonInfected.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    @Override public void setMoltCaptive(boolean value){entityData.set(MOLT_CAPTIVE,value);}
    private boolean moltCaptive(){return entityData.get(MOLT_CAPTIVE);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){
        return !moltCaptive() && super.shouldRenderAtSqrDistance(distance);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(MOLT_CAPTIVE,false);builder.define(ATTACK_STARTED,-1L);builder.define(ATTACK_HAND,0);builder.define(APPEARANCE,-1);builder.define(LEAP_PUNCH,-1L);builder.define(LEAP_LANDED,-1L);}
    public long attackStarted(){
        if(isRemoved())return -1;
        return getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()?-1:entityData.get(ATTACK_STARTED);
    }
    public void cancelAttack(){endAttack(false);}
    private void endAttack(boolean releaseRoot){
        if(level().isClientSide)return;
        attackGeneration++;
        if(releaseRoot&&leapFlight)beginLeapExit();else if(!releaseRoot)leapExit.clear();
        leapFlight=false;entityData.set(ATTACK_STARTED,-1L);entityData.set(LEAP_PUNCH,-1L);entityData.set(LEAP_LANDED,-1L);setAggressive(false);
    }
    private boolean mayBlendLeapExit(){return combatAvailable()&&hurtTime==0&&!isPassenger()&&!isInWaterOrBubble()&&!isInLava()&&!horizontalCollision;}
    private void beginLeapExit(){
        if(!mayBlendLeapExit()){leapExit.clear();return;}
        leapExit.begin(level().getGameTime(),getDeltaMovement());
        // The frozen driver owns its old horizontal velocity. Do not also hand
        // that same momentum to ordinary travel and count it a second time.
        setDeltaMovement(0,getDeltaMovement().y,0);hurtMarked=true;
    }
    public double leapExitWeight(){return leapExit.weight(level().getGameTime());}
    public float leapPunchAge(float partial){long start=entityData.get(LEAP_PUNCH);return start<0?-1:level().getGameTime()-start+partial;}
    public float leapLandAge(float partial){long start=entityData.get(LEAP_LANDED);return start<0?-1:level().getGameTime()-start+partial;}
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
        targetSelector.addGoal(1,new HurtByTargetGoal(this) {
            @Override public boolean canUse() {
                return combatAvailable() && validTarget(getLastHurtByMob()) && super.canUse();
            }
            @Override protected void alertOther(Mob other, LivingEntity attacker) {
                if (other instanceof BlacklightCommonInfected ally && ally.combatAvailable()
                        && ally.validTarget(attacker) && distanceToSqr(ally) <= 100
                        && ally.hasLineOfSight(attacker)) super.alertOther(ally, attacker);
            }
        }.setAlertOthers());
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::validTarget));
    }
    public boolean validTarget(LivingEntity target){
        if(target==null||target==this||!target.isAlive()||isAlliedTo(target))return false;
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()&&!com.codex.blacklight.disguise.DisguiseSystem.fooled(this,p);
        return target instanceof Mob&&!BlacklightInfectionSystem.isInfected(target);
    }
    @Override public boolean isAlliedTo(Entity other){return other instanceof LivingEntity living&&!(other instanceof Player)&&BlacklightInfectionSystem.isInfected(living)||super.isAlliedTo(other);}
    private boolean restrained(){return com.codex.blacklight.combat.ImpactControl.stunned(this)
            ||getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
            ||com.codex.blacklight.power.BiomassSystem.claimant(this)!=null;}
    /** Shared availability gate for the existing civilian attack and lawful mechanical pursuit. */
    public boolean combatAvailable(){return isAlive()&&!isNoAi()&&!restrained()&&level().getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL;}
    /** JumpTo owns flight; its native root driver fades over .333 seconds on
     * ordinary release. Capture/physics ownership changes cancel it immediately. */
    @Override public void travel(net.minecraft.world.phys.Vec3 input){
        if(!level().isClientSide&&leapExitWeight()>0&&!mayBlendLeapExit())leapExit.clear();
        if(leapFlight&&!level().isClientSide){
            if(!combatAvailable()||hurtTime>0||isPassenger()||isInWaterOrBubble()||isInLava()||attackStarted()<0){
                cancelAttack();
            }else{
                // The goal has already applied tracking then source gravity once.
                // Calling super here would add air drag, movement input and gravity again.
                move(MoverType.SELF,getDeltaMovement());calculateEntityAnimation(false);return;
            }
        }
        leapExitTravel=!level().isClientSide&&leapExitWeight()>0;
        try{super.travel(input);}finally{leapExitTravel=false;}
    }
    @Override public void move(MoverType type,net.minecraft.world.phys.Vec3 movement){
        if(!leapExitTravel||type!=MoverType.SELF){super.move(type,movement);return;}
        // Replace this one ordinary travel step, never move a second time. Keep
        // its unblended velocity for next tick's walking acceleration/drag.
        var step=leapExit.blend(movement,level().getGameTime());
        if(!com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(level(),getBoundingBox().expandTowards(step).inflate(1))){
            leapExit.clear();step=new net.minecraft.world.phys.Vec3(0,movement.y,0);
        }
        super.move(type,step);
        if(horizontalCollision)leapExit.clear();
    }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damage){return net.minecraft.sounds.SoundEvents.PLAYER_HURT;}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return com.codex.blacklight.registry.ModInfectedSounds.event(com.codex.blacklight.infection.InfectedSoundCue.COMMON_DEATH);}
    private final class CivilianAttackGoal extends Goal{
        private int cooldown,sequence,leapCooldown;private LivingEntity committed;private float committedYaw;
        private boolean launched,leapHit,leapTracking;private float previousLeapYaw;private net.minecraft.world.phys.Vec3 previousLeap;
        CivilianAttackGoal(){setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public boolean canUse(){return validTarget(attackStarted()>=0?committed:getTarget())&&combatAvailable();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void stop(){endAttack(true);getNavigation().stop();committed=null;launched=leapTracking=false;previousLeap=null;}
        @Override public void tick(){
            long start=attackStarted(),elapsed=start<0?-1:level().getGameTime()-start;
            var target=start>=0?committed:getTarget();if(target==null)return;
            if(cooldown>0)cooldown--;
            if(leapCooldown>0)leapCooldown--;
            setAggressive(true);getLookControl().setLookAt(target,25,25);
            if(start<0&&leapExitWeight()>0){getNavigation().moveTo(target,1.25);return;}
            if(start>=0){
                getNavigation().stop();setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;
                if(!validTarget(target)||hurtTime>0||restrained()){cancelAttack();cooldown=10;committed=null;return;}
                if(attackHand()==com.codex.blacklight.infection.CommonInfectedLeap.HAND){tickLeap(target,elapsed);return;}
                if(elapsed==(attackHand()==2?8:6)&&onGround()&&hasLineOfSight(target)){
                    double yaw=Math.toRadians(committedYaw),space=Math.max(0,distanceTo(target)-(getBbWidth()+target.getBbWidth())*.5-.12);
                    double speed=Math.min(space,attackHand()==2?.28:.18);
                    var toward=new net.minecraft.world.phys.Vec3(-Math.sin(yaw)*speed,0,Math.cos(yaw)*speed);
                    setDeltaMovement(0,getDeltaMovement().y,0);move(MoverType.SELF,toward);hurtMarked=true;
                }
                if(elapsed==CommonInfectedMotion.contactTick(attackHand())&&validTarget(target)&&hasLineOfSight(target)
                        &&getBoundingBox().inflate(.85,.25,.85).intersects(target.getBoundingBox())
                        &&com.codex.blacklight.combat.CreatureAttack.inArc(target.getX()-getX(),target.getZ()-getZ(),committedYaw,.15)){
                    swing(attackHand()==1?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND);
                    if(doHurtTarget(target)&&attackHand()==2)target.knockback(.45,getX()-target.getX(),getZ()-target.getZ());
                }
                if(elapsed>=CommonInfectedMotion.duration(attackHand())){entityData.set(ATTACK_STARTED,-1L);cooldown=attackHand()==2?14:8;committed=null;}
            }else if(cooldown==0&&hurtTime==0&&onGround()&&distanceToSqr(target)<5&&getSensing().hasLineOfSight(target)){
                getNavigation().stop();committed=target;
                committedYaw=(float)Math.toDegrees(Math.atan2(getX()-target.getX(),target.getZ()-getZ()));
                attackGeneration++;entityData.set(ATTACK_HAND,Math.floorMod(sequence++,3));entityData.set(ATTACK_STARTED,level().getGameTime());
            }else if(cooldown==0&&leapCooldown==0&&hurtTime==0&&onGround()
                    &&com.codex.blacklight.infection.CommonInfectedLeap.range(target.position().subtract(position()).horizontalDistance(),target.getY()-getY())
                    &&hasLineOfSight(target)){
                // Failed geometry checks also consume the retry cooldown.
                leapCooldown=com.codex.blacklight.infection.CommonInfectedLeap.COOLDOWN;
                float yaw=(float)Math.toDegrees(Math.atan2(getX()-target.getX(),target.getZ()-getZ()));
                if(clearLeap(target,launchTo(target))){
                    getNavigation().stop();leapExit.clear();committed=target;committedYaw=yaw;launched=leapHit=leapTracking=false;previousLeap=null;
                    entityData.set(LEAP_PUNCH,-1L);entityData.set(LEAP_LANDED,-1L);
                    attackGeneration++;entityData.set(ATTACK_HAND,com.codex.blacklight.infection.CommonInfectedLeap.HAND);entityData.set(ATTACK_STARTED,level().getGameTime());
                }else getNavigation().moveTo(target,1.25);
            }else getNavigation().moveTo(target,1.25);
        }
        private net.minecraft.world.phys.Vec3 targetVelocity(LivingEntity target){
            var velocity=target.getDeltaMovement();
            // Grounded living entities retain a negative gravity residue in deltaMovement.
            return target.onGround()?new net.minecraft.world.phys.Vec3(velocity.x,0,velocity.z):velocity;
        }
        private net.minecraft.world.phys.Vec3 launchTo(LivingEntity target){
            // Native target-position query submode 2 is adapted to receiving AABB centers.
            // Constant target acceleration is a prediction, not a simulation of target drag.
            double acceleration=0;
            if(!target.onGround()&&!target.isNoGravity()&&!target.isInWaterOrBubble()&&!target.isInLava()&&!target.isFallFlying()){
                double gravity=target.getGravity();
                if(target.getDeltaMovement().y<=0&&target.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING))gravity=Math.min(gravity,.01);
                acceleration=-gravity;
                var levitation=target.getEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
                if(levitation!=null)acceleration=(.05*(levitation.getAmplifier()+1)-target.getDeltaMovement().y)*.2;
            }
            return com.codex.blacklight.infection.CommonInfectedLeap.launch(
                    target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()),targetVelocity(target),acceleration);
        }
        private boolean clearLeap(LivingEntity target,net.minecraft.world.phys.Vec3 velocity){
            if(isBaby()||isInWaterOrBubble()||isInLava()||isPassenger()||com.codex.blacklight.combat.ConsumeControl.protectedEntity(target)||target.getVehicle() instanceof MilitaryVehicle
                    ||target.getVehicle() instanceof BlackwatchHelicopter||!hasLineOfSight(target))return false;
            var previous=net.minecraft.world.phys.Vec3.ZERO;
            var path=com.codex.blacklight.infection.CommonInfectedLeap.clearance(velocity,target.getY()-getY());
            if(path.isEmpty())return false;
            for(var offset:path){
                var box=getBoundingBox().move(previous).expandTowards(offset.subtract(previous));
                // Block collision lookup also inspects the one-block border around the swept body.
                // getChunkNow checks every covered column without requesting/loading new terrain.
                if(!com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(level(),box.inflate(1))
                        ||!level().noCollision(BlacklightCommonInfected.this,box))return false;
                previous=offset;
            }
            return true;
        }
        private boolean advanceFlight(LivingEntity target){
            var oldVelocity=getDeltaMovement();
            var next=com.codex.blacklight.infection.CommonInfectedLeap.advance(oldVelocity,
                    target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()),targetVelocity(target),leapTracking);
            leapTracking=next.active();var velocity=next.velocity();
            var sweep=getBoundingBox().expandTowards(velocity);
            // Real move() owns descending ground contact. The preflight check rejects
            // side/ceiling cover and never requests a missing chunk, including its border.
            var obstacle=getBoundingBox().expandTowards(velocity.x,Math.max(0,velocity.y),velocity.z);
            if(!com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(level(),sweep.inflate(1))){
                setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);hurtMarked=true;return false;
            }
            if(!level().noCollision(BlacklightCommonInfected.this,obstacle))return false;
            setDeltaMovement(velocity);hurtMarked=true;
            if(velocity.horizontalDistanceSqr()>1e-8)committedYaw=(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z));
            setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;
            return true;
        }
        private void finishLeap(){
            endAttack(true);cooldown=14;committed=null;launched=leapTracking=false;previousLeap=null;
        }
        private boolean ownsLeap(long generation){
            return attackGeneration==generation&&attackStarted()>=0
                    &&attackHand()==com.codex.blacklight.infection.CommonInfectedLeap.HAND
                    &&combatAvailable()&&hurtTime==0&&!isPassenger()&&!isInWaterOrBubble()&&!isInLava();
        }
        private void tickLeap(LivingEntity target,long elapsed){
            long generation=attackGeneration;
            if(elapsed<com.codex.blacklight.infection.CommonInfectedLeap.AIM_TICKS){
                float desired=(float)Math.toDegrees(Math.atan2(getX()-target.getX(),target.getZ()-getZ()));
                committedYaw=net.minecraft.util.Mth.approachDegrees(committedYaw,desired,36);
            }
            setYRot(committedYaw);yBodyRot=committedYaw;yHeadRot=committedYaw;
            if(elapsed>=com.codex.blacklight.infection.CommonInfectedLeap.TIMEOUT){finishLeap();return;}
            if(!launched){
                setDeltaMovement(0,getDeltaMovement().y,0);
                if(elapsed<com.codex.blacklight.infection.CommonInfectedLeap.LAUNCH_TICK)return;
                double distance=target.position().subtract(position()).horizontalDistance();
                var velocity=launchTo(target);
                if(!onGround()||distance<1.5||distance>6||!clearLeap(target,velocity)){finishLeap();return;}
                launched=leapTracking=leapFlight=true;previousLeap=position();previousLeapYaw=committedYaw;setDeltaMovement(velocity);
                if(!advanceFlight(target)){setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);finishLeap();return;}
                setOnGround(false);
                // infected2 FIG 13416 is the successful release entry, not each airborne tick.
                com.codex.blacklight.registry.ModInfectedSounds.play(BlacklightCommonInfected.this,com.codex.blacklight.infection.InfectedSoundCue.COMMON_ATTACK,getEyePosition(),1,1,1);return;
            }
            if(horizontalCollision||isInWaterOrBubble()||isInLava()||isPassenger()){finishLeap();return;}
            long now=level().getGameTime(),landed=entityData.get(LEAP_LANDED),punch=entityData.get(LEAP_PUNCH);
            if(landed>=0){
                if(now-landed>=com.codex.blacklight.infection.CommonInfectedLeap.LAND_TICKS)finishLeap();
                return;
            }
            // Cover, invalid consumption/vehicle targets and unknown terrain are MC
            // safety gates, not claimed as recovered native JumpTo conditions.
            if(!hasLineOfSight(target)||com.codex.blacklight.combat.ConsumeControl.protectedEntity(target)
                    ||target.getVehicle() instanceof MilitaryVehicle||target.getVehicle() instanceof BlackwatchHelicopter){finishLeap();return;}
            if(!onGround()&&!advanceFlight(target)){finishLeap();return;}
            if(punch<0&&elapsed>=com.codex.blacklight.infection.CommonInfectedLeap.LAUNCH_TICK+com.codex.blacklight.infection.CommonInfectedLeap.PROBE_DELAY
                    &&hasLineOfSight(target)&&com.codex.blacklight.infection.CommonInfectedLeap.approaches(position(),committedYaw,target.getBoundingBox())){
                entityData.set(LEAP_PUNCH,now);punch=now;
            }
            if(!leapHit&&punch>=0&&com.codex.blacklight.infection.CommonInfectedLeap.damageWindow(now-punch)
                    &&!com.codex.blacklight.combat.ConsumeControl.protectedEntity(target)&&hasLineOfSight(target)){
                var before=previousLeap==null?position():previousLeap;
                if(com.codex.blacklight.infection.CommonInfectedLeap.touches(before,position(),previousLeapYaw,committedYaw,elapsed,now-punch,target.getBoundingBox())){
                    // Probe hits never deal damage. The right-wrist window gets one lawful attempt.
                    leapHit=true;swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    if(!ownsLeap(generation))return;
                    boolean accepted=doHurtTarget(target);
                    // A synchronous hurt/loot callback can cancel, load this same
                    // actor, or start another action. The old stack owns no new state.
                    if(!ownsLeap(generation))return;
                    if(accepted)target.knockback(.4,getX()-target.getX(),getZ()-target.getZ());
                }
            }
            if(!ownsLeap(generation))return;
            previousLeap=position();previousLeapYaw=committedYaw;
            if(onGround()&&elapsed>com.codex.blacklight.infection.CommonInfectedLeap.LAUNCH_TICK+1){
                entityData.set(LEAP_LANDED,now);beginLeapExit();leapTracking=leapFlight=false;
                // Two distinct source tracks (15348/15440): voice and foot contact.
                com.codex.blacklight.registry.ModInfectedSounds.play(BlacklightCommonInfected.this,com.codex.blacklight.infection.InfectedSoundCue.COMMON_ATTACK,getEyePosition(),.6869F,1,1);
                com.codex.blacklight.registry.ModInfectedSounds.play(BlacklightCommonInfected.this,com.codex.blacklight.infection.InfectedSoundCue.COMMON_LAND,position(),.6869F,1,1);
            }
        }
    }
    @Override public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean accepted=super.killedEntity(level,victim);
        if(accepted&&!victim.isAlive()&&victim!=this&&victim.getLastHurtByMob()==this
                &&!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(victim))meals=Math.min(3,meals+1);
        return accepted;
    }
    @Override public void tick() {
        super.tick();if(moltCaptive())return;
        // The final vanilla death tick can remove this body during super.tick().
        if(isRemoved())return;
        // Capture sets NoAI, so the stopped goal cannot clear its own pending pose until release.
        if(!level().isClientSide&&(isNoAi()||restrained()))cancelAttack();
        if(!(level() instanceof ServerLevel level)||!isAlive()||meals<3||tickCount%100!=0
                ||getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
                ||com.codex.blacklight.combat.ImpactControl.stunned(this))return;
        promote();
    }
    /** Same-body promotion, never a random replacement species, death or extra scheduled birth. */
    public com.codex.blacklight.entity.BlacklightInfectedHost promote() {
        if(!(level() instanceof ServerLevel level)||promoting||!isAlive()||isRemoved()||isBaby()||isPassenger()||isVehicle()||isLeashed()
                ||getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
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
            com.codex.blacklight.combat.QuillHarvest.transferTumor(this,host,level);
            discard();return host;
        }finally{promoting=false;}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag);if(moltCaptive())tag.putBoolean("GreeneMoltCaptive",true);tag.putInt("BlacklightMeals",meals);tag.putInt("BlacklightCivilianAppearance",appearance()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag);setMoltCaptive(tag.getBoolean("GreeneMoltCaptive"));cancelAttack();leapExitTravel=false;meals=Math.clamp(tag.getInt("BlacklightMeals"),0,3);entityData.set(APPEARANCE,tag.contains("BlacklightCivilianAppearance")?Math.clamp(tag.getInt("BlacklightCivilianAppearance"),0,3):-1); }
}
