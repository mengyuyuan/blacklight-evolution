package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/** Reversible selector participants, never a replacement AI or a saved NoAI flag.
 * Source combat goals with higher priority may preempt our pursuit. A Brain which
 * moves out of contact simply aborts the owned scene on its next actual tick. */
public final class NativeTissueCompetitionGoal {
    private static final Map<Mob,Boolean> INSTALLED=new WeakHashMap<>();
    private static final Map<Mob,WeakReference<Mob>> INTENT=new WeakHashMap<>();
    private NativeTissueCompetitionGoal(){}
    public static void intend(Mob actor,Mob prey){
        if(!(actor instanceof BlacklightInfectedHost))INTENT.put(actor,new WeakReference<>(prey));
    }
    public static void forget(Mob actor){INTENT.remove(actor);}
    private static Mob prey(Mob actor){var ref=INTENT.get(actor);return ref==null?null:ref.get();}
    public static void update(Mob mob){
        if(INSTALLED.containsKey(mob)&&mob.tickCount%20!=0)return;
        if(!NativeInfection.active(mob)){clear(mob);return;}
        boolean hold=false,hunt=false;
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals())){
            hold|=wrapped.getGoal() instanceof Hold;hunt|=wrapped.getGoal() instanceof Hunt;
        }
        if(!hold)mob.goalSelector.addGoal(0,new Hold(mob));
        if(!hunt)mob.goalSelector.addGoal(3,new Hunt(mob));
        INSTALLED.put(mob,true);
    }
    public static void clear(Mob mob){
        var target=prey(mob);forget(mob);INSTALLED.remove(mob);
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))
            if(wrapped.getGoal() instanceof Hold||wrapped.getGoal() instanceof Hunt)mob.goalSelector.removeGoal(wrapped.getGoal());
        if(target!=null&&mob.getTarget()==target)mob.setTarget(null);
    }
    public static void reset(){INSTALLED.clear();INTENT.clear();}
    private static final class Hold extends Goal {
        private final Mob mob;
        Hold(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
        @Override public boolean canUse(){return TissueCompetition.controls(mob);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);mob.setJumping(false);}
    }
    private static final class Hunt extends Goal {
        private final Mob mob;
        private int windup=-1,attackAt;
        Hunt(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){
            var target=prey(mob);if(target==null||mob.getTarget()!=target)return false;
            var subject=TissueSubject.of(mob);
            return subject!=null&&subject.inheritance().readyToHunt(subject.age())&&TissueCompetition.canPursue(mob,target)
                    &&mob.distanceToSqr(target)<=Math.pow(TissueCompetitionConfig.RADIUS.get()*1.5,2);
        }
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void stop(){windup=-1;mob.getNavigation().stop();}
        @Override public void tick(){
            if(!canUse())return;var target=prey(mob);
            if(TissueCompetition.tryStart(mob,target)){windup=-1;return;}
            mob.getLookControl().setLookAt(target,30,30);
            boolean contact=mob.getBoundingBox().inflate(.65,.35,.65).intersects(target.getBoundingBox())&&mob.hasLineOfSight(target);
            if(windup>=0){
                mob.getNavigation().stop();
                if(++windup==8&&contact){
                    // Native armed hosts keep their own doHurtTarget implementation.
                    // Only an unarmed host needs an attribute-free contact fallback.
                    if(mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null)mob.doHurtTarget(target);
                    else{
                        double base=com.codex.blacklight.config.InfectionEcologyConfig.value(com.codex.blacklight.config.InfectionEcologyConfig.FALLBACK_DAMAGE);
                        float amount=(float)Math.min(Float.MAX_VALUE/4,base*(1+NativeTissue.combat(mob).damage()));
                        if(Float.isFinite(amount)&&amount>0)target.hurt(TissueAttackSource.mobAttack(mob),amount);
                    }
                }
                if(windup>=14){windup=-1;attackAt=mob.tickCount+20;}
            }else if(contact&&mob.tickCount>=attackAt){windup=0;mob.getNavigation().stop();mob.swing(InteractionHand.MAIN_HAND);}
            else if(mob.tickCount%10==0)mob.getNavigation().moveTo(target,1);
        }
    }
}
