package com.codex.blacklight.infection;

import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.mixin.LivingDeathAccess;
import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Server-owned live captures. One prey, one owner and one accepted settlement.
 * Claims are transient: loading an unfinished scene releases it, never replays a reward. */
@EventBusSubscriber(modid="blacklight")
public final class TissueCompetition {
    public static final int COVER_TICKS=28, END_TICKS=100;
    private static final String SETTLED="BlacklightCannibalSettled";
    private static final Map<Mob,Session> ACTIVE=new IdentityHashMap<>();
    private static final Map<Mob,Session> CLAIMS=new IdentityHashMap<>();
    private static final Set<Session> EXECUTING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Level,Budget> BUDGETS=new WeakHashMap<>();
    private static final Map<Mob,Long> RETRY=new WeakHashMap<>();
    private static final Map<Mob,Long> SEARCH_AT=new WeakHashMap<>();
    private static final Map<Mob,Long> RECOVERED_UNTIL=new WeakHashMap<>();
    private static final class Budget { long tick=Long.MIN_VALUE;int searches; }
    private record Session(UUID operation,Mob actor,Mob prey,long start,int duration){}
    private TissueCompetition(){}
    public static boolean participant(Mob host){
        var subject=TissueSubject.of(host);
        return subject!=null&&subject.competitionEligible()&&subject.age()>=TissueGrowth.ANCIENT_TICKS;
    }
    public static boolean contender(Mob actor,LivingEntity target){
        return target instanceof Mob prey&&prey!=actor&&participant(actor)&&participant(prey)&&ready(actor)
                &&TissueCompetitionConfig.ENABLED.get()&&actor.level()==prey.level();
    }
    public static boolean controls(Entity entity){return entity!=null&&!entity.level().isClientSide&&(ACTIVE.containsKey(entity)||CLAIMS.containsKey(entity));}
    public static Mob claimant(Entity prey){if(prey==null||prey.level().isClientSide)return null;var session=CLAIMS.get(prey);return session==null?null:session.actor();}
    public static int target(Mob actor){var session=ACTIVE.get(actor);return session==null?-1:session.prey().getId();}
    public static boolean canPursue(Mob actor,Mob prey){
        return contender(actor,prey)&&free(actor)&&free(prey)&&!controls(actor)&&!controls(prey)
                &&!prey.getPersistentData().getBoolean(SETTLED);
    }
    private static boolean free(Mob host){
        return ModAttachments.available(host)&&host.isAlive()&&!host.isNoAi()&&!host.isPassenger()&&!host.isVehicle()&&!host.isLeashed()
                &&!host.isInvulnerable()&&!ModAttachments.peekData(host,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()
                &&BiomassSystem.claimant(host)==null&&!ConsumeSystem.grappleProtected(host)
                &&TissueGrapples.owner(host)==null&&!com.codex.blacklight.combat.ImpactControl.stunned(host);
    }
    private static boolean contact(Mob actor,Mob prey){
        return actor.getBoundingBox().inflate(.75,.35,.75).intersects(prey.getBoundingBox())&&actor.hasLineOfSight(prey);
    }
    /** Only a ready, unclaimed predator in actual contact may preserve its own last bite. */
    private static boolean canSecureMeal(Mob actor,Mob prey){
        return contender(actor,prey)&&ready(actor)&&free(actor)&&free(prey)
                &&!controls(actor)&&!controls(prey)&&!prey.getPersistentData().getBoolean(SETTLED)
                &&RETRY.getOrDefault(actor,0L)<=actor.level().getGameTime()&&contact(actor,prey);
    }
    public static float finalDamage(LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float amount){
        if(!(target instanceof Mob prey)||prey.level().isClientSide)return amount;
        if(source.getEntity() instanceof Mob actor&&canSecureMeal(actor,prey)){
            float floor=(float)(prey.getMaxHealth()*TissueCompetitionConfig.weakenedFraction()*.8);
            amount=Math.min(amount,Math.max(0,prey.getHealth()-floor));
        }
        if(RECOVERED_UNTIL.getOrDefault(prey,0L)>prey.level().getGameTime()
                &&source.getEntity() instanceof net.minecraft.world.entity.Mob
                &&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))amount*=TissueCompetitionConfig.RECOVERY_DAMAGE.get();
        return amount;
    }
    /** Called after the original hit's stagger/cancel accounting, so that hit cannot cancel its own capture. */
    public static void captureAfterContact(Mob prey,net.minecraft.world.damagesource.DamageSource source){
        if(prey!=null&&source!=null&&source.getEntity() instanceof Mob actor
                &&canSecureMeal(actor,prey)&&TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth()))tryStart(actor,prey);
    }
    /** No teleporting, riding, saved NoAI, or bypass of a player's existing claim. */
    public static boolean tryStart(Mob actor,Mob prey){
        if(actor==null||prey==null||!(actor.level() instanceof ServerLevel)||actor.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||!contender(actor,prey)||!free(actor)||!free(prey)
                ||controls(actor)||controls(prey)||prey.getPersistentData().getBoolean(SETTLED)
                ||!TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||!contact(actor,prey)
                ||!TissueInheritance.sizeFits(actor.getBbWidth(),actor.getBbHeight(),prey.getBbWidth(),prey.getBbHeight())
                ||RETRY.getOrDefault(actor,0L)>actor.level().getGameTime()
                ||ACTIVE.keySet().stream().filter(h->h.level()==actor.level()).count()>=TissueCompetitionConfig.SESSIONS.get())return false;
        var subject=TissueSubject.of(actor);
        boolean pharynx=subject.power(TissueCore.PHARYNX)>0&&subject.spend(TissueOrganCycle.cycle(TissueCore.PHARYNX).cost());
        var session=new Session(UUID.randomUUID(),actor,prey,actor.level().getGameTime(),pharynx?TissueOrganCycle.cycle(TissueCore.PHARYNX).end():END_TICKS);
        ACTIVE.put(actor,session);CLAIMS.put(prey,session);
        TissueGrapples.release(actor);TissueGrapples.release(prey);
        scene(actor,prey.getId(),session.start(),false,session.duration());scene(prey,actor.getId(),session.start(),true,session.duration());
        arrestMotion(session);prey.setTarget(null);
        return true;
    }
    /** True while this tick's locomotion/ordinary attacks belong to the scene. */
    public static boolean tick(Mob host){
        if(host==null||host.level().isClientSide)return false;
        var session=ACTIVE.get(host);
        if(session!=null){advance(session);return ACTIVE.containsKey(host);}
        var claimed=CLAIMS.get(host);
        if(claimed!=null){
            if(!contender(claimed.actor(),host)||!free(claimed.actor())||!free(host))release(claimed.actor());
            return CLAIMS.containsKey(host);
        }
        if(!participant(host)||!ready(host)||host.isNoAi()||!TissueCompetitionConfig.ENABLED.get())return false;
        if(host.getTarget() instanceof Mob prey&&contender(host,prey)){
            if(tryStart(host,prey))return true;
        }
        var current=host.getTarget();
        // A native host's ongoing source-mod fight keeps priority over a new hunt.
        if(!(host instanceof BlacklightInfectedHost)&&current!=null&&!contender(host,current))return false;
        if(current instanceof Mob m&&participant(m)||!free(host)
                ||current!=null&&(host.hurtTime>0||current instanceof net.minecraft.world.entity.player.Player&&TissueSubject.of(host).evolved()))return false;
        boolean pressured=current instanceof net.minecraft.world.entity.player.Player&&host.distanceToSqr(current)<24*24;
        var budget=BUDGETS.computeIfAbsent(host.level(),level->new Budget());long now=host.level().getGameTime();
        long due=SEARCH_AT.computeIfAbsent(host,h->now+Math.floorMod(h.getId(),30));if(now<due)return false;
        if(budget.tick!=now){budget.tick=now;budget.searches=0;}
        if(budget.searches>=TissueCompetitionConfig.SEARCHES.get()){
            // Retry on a different tick instead of starving late-ticked creatures forever.
            SEARCH_AT.put(host,now+1+Math.floorMod(host.getId()+now,5));return false;
        }
        budget.searches++;SEARCH_AT.put(host,now+30+Math.floorMod(host.getId()+now,11));
        // One bounded nearby query; never force-load chunks or scan the world's entity list.
        int radius=TissueCompetitionConfig.RADIUS.get();
        var target=host.level().getEntitiesOfClass(Mob.class,host.getBoundingBox().inflate(radius),
                prey->contender(host,prey)&&!controls(prey)&&free(prey)&&host.hasLineOfSight(prey)
                        &&TissueInheritance.sizeFits(host.getBbWidth(),host.getBbHeight(),prey.getBbWidth(),prey.getBbHeight())
                        &&!prey.getPersistentData().getBoolean(SETTLED)
                        &&(TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||!pressured&&dominates(host,prey)))
                .stream().min(Comparator.<Mob>comparingDouble(h->h.getHealth()/h.getMaxHealth())
                        .thenComparingDouble(host::distanceToSqr)).orElse(null);
        if(target!=null){host.setTarget(target);NativeTissueCompetitionGoal.intend(host,target);}
        return false;
    }
    private static double potential(Mob host){
        var subject=TissueSubject.of(host);
        double total=TissueInheritance.nativeStrength(subject.age());
        for(var organ:subject.inheritance().organs().values())total+=Math.log1p(organ.integrated())*10;
        return total;
    }
    private static boolean dominates(Mob actor,Mob prey){
        double self=potential(actor),other=potential(prey);
        // A stable tie-break allows equally old mature populations to compete too.
        // It does not reroll on every AI tick or make both creatures choose each other at birth.
        return self>other*1.05||self>=other*.95&&actor.getUUID().compareTo(prey.getUUID())<0;
    }
    private static void arrestMotion(Session session){
        // The scene owns motion, so ordinary setters correctly reject foreign
        // impulses. Its own arrest must use the same scoped permit as root motion.
        try(var allowed=com.codex.blacklight.combat.ConsumeControl.motion(session.actor(),session.prey())){
            for(var host:List.of(session.actor(),session.prey())){
                host.getNavigation().stop();host.setXxa(0);host.setZza(0);host.setJumping(false);
                host.setDeltaMovement(new Vec3(0,Math.min(0,host.getDeltaMovement().y),0));
            }
        }
    }
    private static void advance(Session session){
        var actor=session.actor();var prey=session.prey();long now=actor.level().getGameTime();
        if(!TissueCompetitionConfig.ENABLED.get()||!contender(actor,prey)||!free(actor)||!free(prey)||!contact(actor,prey)
                ||!TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||now<session.start()||now-session.start()>session.duration()+1){
            release(actor);return;
        }
        arrestMotion(session);
        actor.getLookControl().setLookAt(prey,25,25);
        if(now-session.start()<session.duration())return;
        var donor=TissueSubject.of(prey).donor();
        // Mark before callbacks. A cancelled death rolls the mark back and grants nothing.
        prey.getPersistentData().putBoolean(SETTLED,true);prey.getPersistentData().putUUID("BlacklightCannibalOperation",session.operation());
        prey.invulnerableTime=0;
        boolean accepted=execute(session);
        var recipient=TissueSubject.of(actor);
        if(accepted&&((LivingDeathAccess)(LivingEntity)prey).blacklight$deathAccepted()&&!prey.isAlive()&&prey.getKillCredit()==actor
                &&recipient!=null&&actor.level()==prey.level()&&ACTIVE.get(actor)==session){
            if(TissueCompetitionConfig.INHERITANCE.get())recipient.inherit(session.operation(),donor);
            actor.heal((float)(actor.getMaxHealth()*TissueCompetitionConfig.HEAL.get()/com.codex.blacklight.power.CombatScale.units(actor)));
            RECOVERED_UNTIL.put(actor,now+TissueCompetitionConfig.RECOVERY_TICKS.get());
            prey.setInvisible(true);
        }else{
            prey.getPersistentData().remove(SETTLED);prey.getPersistentData().remove("BlacklightCannibalOperation");
        }
        release(actor);
    }
    public static void damaged(Mob host,float actual){
        if(host!=null&&!host.level().isClientSide&&Float.isFinite(actual)&&actual>0&&actual>=host.getMaxHealth()*TissueCompetitionConfig.INTERRUPT.get()){
            var session=ACTIVE.get(host);if(session==null)session=CLAIMS.get(host);
            // The final accepted lethal receipt is the settlement itself. Other
            // real hits, including reentrant damage to the consumer, still abort.
            if(session!=null&&!(session.prey()==host&&EXECUTING.contains(session)&&!host.isAlive()))release(session.actor());
        }
    }
    private static boolean execute(Session session){
        boolean completed=false;EXECUTING.add(session);
        try{
            var prey=session.prey();
            boolean accepted=prey.hurt(TissueAttackSource.mobAttack(session.actor()),(float)Math.min(Float.MAX_VALUE,Math.max(10000D,prey.getMaxHealth()*20D)));
            completed=true;return accepted;
        }finally{
            EXECUTING.remove(session);
            if(!completed){
                var data=session.prey().getPersistentData();
                if(data.hasUUID("BlacklightCannibalOperation")&&data.getUUID("BlacklightCannibalOperation").equals(session.operation())){
                    data.remove(SETTLED);data.remove("BlacklightCannibalOperation");
                }
                release(session.actor());
            }
        }
    }
    public static void release(Mob actor){
        var session=ACTIVE.remove(actor);if(session==null)return;CLAIMS.remove(session.prey());
        RETRY.put(actor,actor.level().getGameTime()+100);
        for(var host:List.of(actor,session.prey()))scene(host,-1,-1,false,END_TICKS);
        NativeTissueCompetitionGoal.forget(actor);
        if(actor.getTarget()==session.prey())actor.setTarget(null);
    }
    private static boolean ready(Mob mob){
        var subject=TissueSubject.of(mob);return subject!=null&&subject.inheritance().readyToHunt(subject.age());
    }
    private static void scene(Mob mob,int target,long start,boolean prey,int duration){
        if(!ModAttachments.available(mob))return;
        if(mob instanceof BlacklightInfectedHost h)h.setCannibalVisual(target,start,prey,duration);
        else NativeTissue.scene(mob,target,start,prey,duration);
    }
    /** Loading an existing instance must release the old claim as well. */
    public static void reloaded(Entity entity){
        if(entity.level().isClientSide)return;
        interrupt(entity);
        if(entity instanceof Mob mob&&!(mob instanceof BlacklightInfectedHost)){
            NativeTissueCompetitionGoal.forget(mob);NativeTissueAbilityGoal.clear(mob);NativeTissueDigestion.forget(mob);NativeTissueVitals.forget(mob);NativeTissueDefense.forget(mob);
            if(ModAttachments.available(mob)&&NativeTissue.snapshot(mob).contains("CannibalTarget"))scene(mob,-1,-1,false,END_TICKS);
        }
    }
    public static void interrupt(Entity entity){
        var session=ACTIVE.get(entity);if(session==null)session=CLAIMS.get(entity);
        if(session!=null)release(session.actor());
    }
    public static void forget(Mob mob){
        interrupt(mob);RETRY.remove(mob);SEARCH_AT.remove(mob);RECOVERED_UNTIL.remove(mob);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(event.getLevel().isClientSide)return;
        interrupt(event.getEntity());
        if(event.getEntity() instanceof Mob mob){forget(mob);NativeTissueAbilityGoal.clear(mob);NativeTissueCompetitionGoal.clear(mob);NativeTissueDigestion.forget(mob);NativeTissueVitals.forget(mob);NativeTissueDefense.forget(mob);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.clear();CLAIMS.clear();EXECUTING.clear();BUDGETS.clear();RETRY.clear();SEARCH_AT.clear();RECOVERED_UNTIL.clear();NativeTissueCompetitionGoal.reset();}
}
