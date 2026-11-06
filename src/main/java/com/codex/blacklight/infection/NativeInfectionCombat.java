package com.codex.blacklight.infection;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** A cooperative goal for otherwise defenseless native hosts. No AI/controller,
 * target selector or foreign attribute is replaced. The selector arbitrates movement. */
public final class NativeInfectionCombat {
    private NativeInfectionCombat(){}
    public static boolean eligible(Mob mob){
        if(!ModAttachments.available(mob)||!InfectionEcologyConfig.value(InfectionEcologyConfig.NATIVE_FALLBACK))return false;
        boolean requested=InfectionEcologyConfig.matches(mob,InfectionEcologyConfig.value(InfectionEcologyConfig.FALLBACK_ENTITIES));
        if(!requested&&(!(mob instanceof Animal)||mob instanceof Enemy||mob instanceof NeutralMob
                ||mob instanceof RangedAttackMob||mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null
                ||!mob.targetSelector.getAvailableGoals().isEmpty()))return false;
        return NativeInfection.active(mob);
    }
    public static void update(Mob mob){
        boolean allowed=eligible(mob);boolean installed=false;
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))if(wrapped.getGoal() instanceof Attack goal){
            if(!allowed)mob.goalSelector.removeGoal(goal);else installed=true;
        }
        if(allowed&&!installed)mob.goalSelector.addGoal(3,new Attack(mob));
    }
    public static void clear(Mob mob){
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))
            if(wrapped.getGoal() instanceof Attack goal)mob.goalSelector.removeGoal(goal);
    }
    public static LivingEntity target(Mob mob){
        if(mob.getTarget()!=null)return mob.getTarget();
        for(var wrapped:mob.goalSelector.getAvailableGoals())if(wrapped.isRunning()&&wrapped.getGoal() instanceof Attack goal&&validTarget(mob,goal.prey))return goal.prey;
        return null;
    }
    public static boolean validTarget(Mob source,LivingEntity target){
        if(target==null||target==source||!target.isAlive()||target.isRemoved()||target.level()!=source.level()
                ||source.isAlliedTo(target)||!source.canAttack(target))return false;
        if(source instanceof OwnableEntity own&&own.getOwnerUUID()!=null){
            if(own.getOwnerUUID().equals(target.getUUID()))return false;
            if(target instanceof OwnableEntity other&&own.getOwnerUUID().equals(other.getOwnerUUID()))return false;
        }
        if(target instanceof Player p)return !p.isCreative()&&!p.isSpectator()
                &&!com.codex.blacklight.disguise.DisguiseSystem.fooled(source,p);
        return target instanceof Mob&&!BlacklightInfectionSystem.isInfected(target);
    }
    private static boolean available(Mob mob){
        return ModAttachments.available(mob)&&mob.isAlive()&&!mob.isNoAi()&&NativeInfection.active(mob)
                &&mob.level().getDifficulty()!=Difficulty.PEACEFUL&&mob.getTarget()==null
                &&!com.codex.blacklight.combat.ImpactControl.stunned(mob)
                &&!ModAttachments.peekData(mob,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()
                &&com.codex.blacklight.power.BiomassSystem.claimant(mob)==null;
    }
    private static final class Attack extends Goal {
        final Mob mob;LivingEntity prey;int searchAt,attackAt,windup=-1;
        Attack(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){
            if(!available(mob)||mob.tickCount<searchAt)return false;searchAt=mob.tickCount+20;
            double range=InfectionEcologyConfig.value(InfectionEcologyConfig.FALLBACK_RADIUS);
            prey=mob.level().getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(range),p->validTarget(mob,p)&&mob.hasLineOfSight(p))
                    .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            return prey!=null;
        }
        @Override public boolean canContinueToUse(){
            double range=InfectionEcologyConfig.value(InfectionEcologyConfig.FALLBACK_RADIUS)*1.5;
            return available(mob)&&validTarget(mob,prey)&&mob.distanceToSqr(prey)<=range*range;
        }
        @Override public void stop(){prey=null;windup=-1;mob.getNavigation().stop();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        private boolean inContact(){return mob.getBoundingBox().inflate(.65,.35,.65).intersects(prey.getBoundingBox())&&mob.hasLineOfSight(prey);}
        @Override public void tick(){
            if(!canContinueToUse())return;
            mob.getLookControl().setLookAt(prey,30,30);
            if(windup>=0){
                mob.getNavigation().stop();
                if(++windup==8&&inContact()){
                    double base=InfectionEcologyConfig.value(InfectionEcologyConfig.FALLBACK_DAMAGE);
                    var growth=NativeTissue.combat(mob);
                    float amount=(float)Math.min(Float.MAX_VALUE/4,base*(1+growth.damage()));
                    if(amount>0&&Float.isFinite(amount))prey.hurt(TissueAttackSource.mobAttack(mob),amount);
                }
                if(windup>=14){windup=-1;attackAt=mob.tickCount+20;}
            }else if(inContact()&&mob.tickCount>=attackAt){
                windup=0;mob.getNavigation().stop();mob.swing(InteractionHand.MAIN_HAND);
            }else if(mob.tickCount%10==0){
                mob.getNavigation().moveTo(prey,InfectionEcologyConfig.value(InfectionEcologyConfig.FALLBACK_SPEED));
            }
        }
    }
}
