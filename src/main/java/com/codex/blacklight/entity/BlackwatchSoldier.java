package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.disguise.DisguiseSystem;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.EnumSet;

/** A visible burst / reload cycle gives the player room to close, grab and consume. */
public final class BlackwatchSoldier extends Monster {
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(BlackwatchSoldier.class,EntityDataSerializers.INT);
    public BlackwatchSoldier(EntityType<? extends BlackwatchSoldier> type,Level level){super(type,level);xpReward=10;}
    public boolean heavy(){return getType()==com.codex.blacklight.registry.ModEntities.BLACKWATCH_HEAVY.get();}
    public int phase(){return entityData.get(PHASE);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(PHASE,0);}
    public static AttributeSupplier.Builder attributes(boolean heavy){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,heavy?60:24).add(Attributes.MOVEMENT_SPEED,heavy?.24:.29).add(Attributes.ARMOR,heavy?10:4).add(Attributes.ATTACK_DAMAGE,heavy?4:2).add(Attributes.FOLLOW_RANGE,28).add(Attributes.KNOCKBACK_RESISTANCE,heavy?.4:.1);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new FireTeamGoal());
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.75));
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this,BlackwatchSoldier.class).setAlertOthers());
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::enemy));
    }
    private boolean enemy(LivingEntity target){
        if(!target.isAlive()||target==this||isAlliedTo(target)||!BlacklightInfectionSystem.isInfected(target))return false;
        return !(target instanceof Player p)||!p.isCreative()&&!p.isSpectator()&&!DisguiseSystem.fooled(this,p);
    }
    @Override public boolean isAlliedTo(Entity other){return other instanceof BlackwatchSoldier&&!BlacklightInfectionSystem.isInfected((LivingEntity)other)&&!BlacklightInfectionSystem.isInfected(this)||super.isAlliedTo(other);}
    @Override public boolean removeWhenFarAway(double distance){return !isPersistenceRequired();}
    private final class FireTeamGoal extends Goal {
        private int cycle,lost;
        FireTeamGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return getTarget()!=null&&getTarget().isAlive()&&!com.codex.blacklight.registry.ModAttachments.peekData(BlackwatchSoldier.this,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active();}
        @Override public void start(){cycle=0;lost=0;}
        @Override public void stop(){entityData.set(PHASE,0);getNavigation().stop();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null)return;
            if(target instanceof Player p&&(p.isCreative()||p.isSpectator()||DisguiseSystem.fooled(BlackwatchSoldier.this,p))){setTarget(null);return;}
            if(isAlliedTo(target)){setTarget(null);return;}
            double distance=distanceToSqr(target);boolean sight=getSensing().hasLineOfSight(target);
            getLookControl().setLookAt(target,30,30);
            if(!sight||distance>22*22){entityData.set(PHASE,0);cycle=0;getNavigation().moveTo(target,1);if(++lost>100)setTarget(null);return;}
            lost=0;
            if(distance<5*5){getNavigation().stop();getMoveControl().strafe(-.35F,0);}
            else if(distance>14*14)getNavigation().moveTo(target,.8);else getNavigation().stop();
            int length=heavy()?76:64;
            if(cycle<12)entityData.set(PHASE,1);
            else if(cycle<=28){entityData.set(PHASE,2);if(cycle==12||cycle==20||cycle==28)fire(target);}
            else entityData.set(PHASE,3);
            cycle=(cycle+1)%length;
        }
    }
    private void fire(LivingEntity target){
        if(!(level() instanceof ServerLevel level))return;
        Vec3 start=getEyePosition().add(0,-.18,0);
        Vec3 aim=target.getBoundingBox().getCenter().subtract(start).normalize();
        // A modest spread, a telegraphed burst and actual cover checks; never damage through walls.
        aim=aim.add((random.nextDouble()-.5)*.035,(random.nextDouble()-.5)*.025,(random.nextDouble()-.5)*.035).normalize();
        Vec3 end=start.add(aim.scale(28));
        var wall=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(level,this,start,end,new AABB(start,end).inflate(.3),e->e!=this&&e instanceof LivingEntity living&&living.isAlive()&&e.isPickable());
        if(hit!=null){end=hit.getLocation();if(hit.getEntity() instanceof LivingEntity living&&!isAlliedTo(living)&&!(living instanceof Player p&&(p.isCreative()||p.isSpectator())))living.hurt(damageSources().mobProjectile(this,this),heavy()?2.2F:1.6F);}
        double length=start.distanceTo(end);Vec3 delta=end.subtract(start).normalize();
        for(double t=.5;t<length;t+=1.25){var point=start.add(delta.scale(t));level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,point.x,point.y,point.z,1,0,0,0,0);}
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,start.x,start.y,start.z,2,.035,.035,.035,.005);
        level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,net.minecraft.sounds.SoundSource.HOSTILE,.55F,heavy()?.75F:1.25F);
    }
}
