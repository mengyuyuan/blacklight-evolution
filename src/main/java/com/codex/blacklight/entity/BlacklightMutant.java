package com.codex.blacklight.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;

/** Distinct species with visible wind-up; neither hit nor spit is applied at acquisition. */
public final class BlacklightMutant extends Monster implements GeoEntity {
    public enum Kind { STALKER,SPITTER,BRUTE }
    private static final EntityDataAccessor<Integer> ATTACK=SynchedEntityData.defineId(BlacklightMutant.class,EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final Kind kind;
    private int cooldown;
    private Vec3 aim=Vec3.ZERO;
    public BlacklightMutant(EntityType<? extends BlacklightMutant> type,Level level,Kind kind){super(type,level);this.kind=kind;xpReward=kind==Kind.BRUTE?18:8;}
    public Kind kind(){return kind;}
    public String asset(){return "blacklight_"+kind.name().toLowerCase(java.util.Locale.ROOT);}
    public static AttributeSupplier.Builder attributes(Kind k){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,k==Kind.BRUTE?80:k==Kind.SPITTER?28:24).add(Attributes.ATTACK_DAMAGE,k==Kind.BRUTE?10:5).add(Attributes.MOVEMENT_SPEED,k==Kind.STALKER?.34:.24).add(Attributes.FOLLOW_RANGE,28).add(Attributes.KNOCKBACK_RESISTANCE,k==Kind.BRUTE?.7:.1).add(Attributes.STEP_HEIGHT,1);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(ATTACK,0);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new MoveTowardsRestrictionGoal(this,1.1));goalSelector.addGoal(2,new AssaultGoal());goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7));goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,12));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        if(com.codex.blacklight.combat.ImpactControl.stunned(this)){entityData.set(ATTACK,0);return;}
        if(getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()){entityData.set(ATTACK,0);return;}
        if(cooldown>0)cooldown--;
        if(com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(this)){entityData.set(ATTACK,0);return;}
        int t=entityData.get(ATTACK);if(t<=0)return;
        getNavigation().stop();
        if(kind==Kind.STALKER&&t<=12&&t>=8)move(MoverType.SELF,aim.subtract(position()).multiply(1,0,1).normalize().scale(.34));
        if(t==10){
            if(kind==Kind.SPITTER){
                var spit=new BlacklightSpit(level(),this);var d=aim.subtract(spit.position());spit.shoot(d.x,d.y,d.z,.85F,1);level().addFreshEntity(spit);
            }else{
                for(var victim:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(kind==Kind.BRUTE?1.2:.65),v->v instanceof Player&&v.isAlive()&&!v.isSpectator()))
                    if(hasLineOfSight(victim)){doHurtTarget(victim);if(kind==Kind.BRUTE)victim.knockback(1, getX()-victim.getX(),getZ()-victim.getZ());}
            }
            playSound(net.minecraft.sounds.SoundEvents.RAVAGER_ATTACK,.75F,kind==Kind.STALKER?1.5F:.7F);
        }
        entityData.set(ATTACK,t-1);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"body",3,s->s.setAndContinue(RawAnimation.begin().thenLoop(getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()?"consume":entityData.get(ATTACK)>0?"attack":s.isMoving()?"run":"idle"))));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    private final class AssaultGoal extends Goal {
        AssaultGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return getTarget()!=null&&getTarget().isAlive()&&isWithinRestriction()&&isWithinRestriction(getTarget().blockPosition());}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null)return;getLookControl().setLookAt(target,30,30);if(entityData.get(ATTACK)>0)return;
            double range=kind==Kind.SPITTER?12:kind==Kind.STALKER?4:2.8;
            if(distanceToSqr(target)>range*range||!hasLineOfSight(target))getNavigation().moveTo(target,1.1);else getNavigation().stop();
            if(cooldown==0&&distanceToSqr(target)<=range*range&&hasLineOfSight(target)){
                aim=target.getBoundingBox().getCenter();entityData.set(ATTACK,24);cooldown=kind==Kind.SPITTER?58:kind==Kind.BRUTE?40:30;
                playSound(net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,.3F,kind==Kind.SPITTER?1.6F:.9F);
            }
        }
        @Override public void stop(){getNavigation().stop();}
    }
}
