package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** One controller, two selector modes. Body actions acquire MOVE/LOOK; a stationary
 * organ may cooperate with an equal-priority native melee without stopping its
 * navigation, steering, velocity or body animation. Higher emergency control wins. */
public final class NativeTissueAbilityGoal extends Goal {
    private enum Mode { NONE,BODY,SUPPLEMENT }
    private final Mob mob;
    private final TissueAbilities actions;
    private Mode mode=Mode.NONE;
    private NativeTissueAbilityGoal(Mob mob){this.mob=mob;actions=new TissueAbilities(mob);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public static void maintain(Mob mob){
        if(mob instanceof BlacklightInfectedHost)return;
        NativeTissueAbilityGoal found=null;
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))if(wrapped.getGoal() instanceof NativeTissueAbilityGoal goal){found=goal;break;}
        if(!TissueActions.eligible(mob)){clear(mob);return;}
        if(found==null){
            for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))if(wrapped.getGoal() instanceof Supplement)mob.goalSelector.removeGoal(wrapped.getGoal());
            found=new NativeTissueAbilityGoal(mob);mob.goalSelector.addGoal(1,found);
        }
        boolean supplement=false;
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))if(wrapped.getGoal() instanceof Supplement extra){
            if(extra.owner==found)supplement=true;else mob.goalSelector.removeGoal(extra);
        }
        if(!supplement)mob.goalSelector.addGoal(4,new Supplement(found));
        if(TissueActions.core(mob)!=null&&(mob.isNoAi()||mob.isPassenger()||TissueCompetition.controls(mob)
                ||com.codex.blacklight.combat.ImpactControl.stunned(mob)
                ||ModAttachments.peekData(mob,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()))found.release();
    }
    private void release(){mode=Mode.NONE;actions.interrupt();}
    public static void clear(Mob mob){
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals())){
            if(wrapped.getGoal() instanceof NativeTissueAbilityGoal goal){goal.release();mob.goalSelector.removeGoal(goal);}
            else if(wrapped.getGoal() instanceof Supplement extra){extra.owner.release();mob.goalSelector.removeGoal(extra);}
        }
    }
    public static void damaged(Mob mob,float actual){
        if(!Float.isFinite(actual)||actual<mob.getMaxHealth()*.1F)return;
        for(var wrapped:List.copyOf(mob.goalSelector.getAvailableGoals()))if(wrapped.getGoal() instanceof NativeTissueAbilityGoal goal)goal.release();
    }
    public static boolean active(Mob mob){
        for(var wrapped:mob.goalSelector.getAvailableGoals())if(wrapped.isRunning()){
            if(wrapped.getGoal() instanceof NativeTissueAbilityGoal goal&&goal.mode==Mode.BODY)return true;
            if(wrapped.getGoal() instanceof Supplement extra&&extra.owner.mode==Mode.SUPPLEMENT)return true;
        }return false;
    }
    private boolean ready(){var subject=TissueSubject.of(mob);return mode==Mode.NONE&&subject!=null&&subject.age()>=TissueGrowth.MATURITY_TICKS&&!TissueCompetition.controls(mob);}
    private boolean viable(){return TissueActions.eligible(mob)&&!mob.isNoAi()&&!TissueCompetition.controls(mob)&&TissueActions.core(mob)!=null;}
    private boolean emergency(){
        for(var wrapped:mob.goalSelector.getAvailableGoals()){
            var goal=wrapped.getGoal();if(!wrapped.isRunning()||goal==this||goal instanceof Supplement)continue;
            if((wrapped.getPriority()<1||!goal.isInterruptable())&&!goal.getFlags().isEmpty())return true;
        }return false;
    }
    @Override public boolean canUse(){
        if(!ready()||emergency()||!actions.tryStart(NativeInfectionCombat.target(mob),true))return false;
        mode=Mode.BODY;return true;
    }
    @Override public boolean canContinueToUse(){return mode==Mode.BODY&&viable();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){actions.tick();}
    @Override public void stop(){if(mode==Mode.BODY)release();}
    private static final class Supplement extends Goal {
        final NativeTissueAbilityGoal owner;
        Supplement(NativeTissueAbilityGoal owner){this.owner=owner;setFlags(EnumSet.noneOf(Flag.class));}
        @Override public boolean canUse(){
            if(!owner.ready()||owner.emergency()||!owner.actions.tryStart(NativeInfectionCombat.target(owner.mob),false))return false;
            owner.mode=Mode.SUPPLEMENT;return true;
        }
        @Override public boolean canContinueToUse(){
            var target=NativeInfectionCombat.target(owner.mob);
            return owner.mode==Mode.SUPPLEMENT&&owner.viable()&&!owner.emergency()
                    &&target!=null&&target.getId()==TissueActions.target(owner.mob);
        }
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){if(canContinueToUse())owner.actions.tick();else if(owner.mode==Mode.SUPPLEMENT)owner.release();}
        @Override public void stop(){if(owner.mode==Mode.SUPPLEMENT)owner.release();}
    }
}
