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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-owned live captures. One prey, one owner and one accepted settlement.
 * Claims are transient: loading an unfinished scene releases it, never replays a reward. */
@EventBusSubscriber(modid="blacklight")
public final class TissueCompetition {
    public static final int COVER_TICKS=28, END_TICKS=100;
    private static final String SETTLED="BlacklightCannibalSettled";
    private static final Map<BlacklightInfectedHost,Session> ACTIVE=new IdentityHashMap<>();
    private static final Map<BlacklightInfectedHost,Session> CLAIMS=new IdentityHashMap<>();
    private static final Map<Level,Budget> BUDGETS=new WeakHashMap<>();
    private static final Map<BlacklightInfectedHost,Long> RETRY=new WeakHashMap<>();
    private static final Map<BlacklightInfectedHost,Long> SEARCH_AT=new WeakHashMap<>();
    private static final class Budget { long tick=Long.MIN_VALUE;int searches; }
    private record Session(UUID operation,BlacklightInfectedHost actor,BlacklightInfectedHost prey,long start,int duration){}
    private TissueCompetition(){}
    public static boolean participant(BlacklightInfectedHost host){
        return host.isAlive()&&!host.isRemoved()&&!host.hasMultipartBody()&&!host.devourerIdentity()
                &&!DevourerFeedingSystem.isDevourer(host)&&host.tissueAge()>=TissueGrowth.ANCIENT_TICKS;
    }
    public static boolean contender(BlacklightInfectedHost actor,LivingEntity target){
        return target instanceof BlacklightInfectedHost prey&&prey!=actor&&participant(actor)&&participant(prey)&&actor.tissueInheritance().readyToHunt(actor.tissueAge())
                &&TissueCompetitionConfig.ENABLED.get()&&actor.level()==prey.level();
    }
    public static boolean controls(Entity entity){return entity!=null&&!entity.level().isClientSide&&(ACTIVE.containsKey(entity)||CLAIMS.containsKey(entity));}
    public static BlacklightInfectedHost claimant(Entity prey){if(prey==null||prey.level().isClientSide)return null;var session=CLAIMS.get(prey);return session==null?null:session.actor();}
    public static int target(BlacklightInfectedHost actor){var session=ACTIVE.get(actor);return session==null?-1:session.prey().getId();}
    private static boolean free(BlacklightInfectedHost host){
        return !host.isNoAi()&&!host.isPassenger()&&!host.isVehicle()&&!host.isLeashed()
                &&!host.isInvulnerable()&&!host.getData(ModAttachments.CONSUME_VISUAL).active()
                &&BiomassSystem.claimant(host)==null&&!ConsumeSystem.grappleProtected(host)
                &&TissueGrapples.owner(host)==null&&!com.codex.blacklight.combat.ImpactControl.stunned(host);
    }
    private static boolean contact(BlacklightInfectedHost actor,BlacklightInfectedHost prey){
        return actor.getBoundingBox().inflate(.75,.35,.75).intersects(prey.getBoundingBox())&&actor.hasLineOfSight(prey);
    }
    /** No teleporting, riding, saved NoAI, or bypass of a player's existing claim. */
    public static boolean tryStart(BlacklightInfectedHost actor,BlacklightInfectedHost prey){
        if(!(actor.level() instanceof ServerLevel)||actor.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||!contender(actor,prey)||!free(actor)||!free(prey)
                ||controls(actor)||controls(prey)||prey.getPersistentData().getBoolean(SETTLED)
                ||!TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||!contact(actor,prey)
                ||!TissueInheritance.sizeFits(actor.getBbWidth(),actor.getBbHeight(),prey.getBbWidth(),prey.getBbHeight())
                ||RETRY.getOrDefault(actor,0L)>actor.level().getGameTime()
                ||ACTIVE.keySet().stream().filter(h->h.level()==actor.level()).count()>=TissueCompetitionConfig.SESSIONS.get())return false;
        boolean pharynx=actor.organPower(TissueCore.PHARYNX)>0&&actor.spendTissueNutrition(TissueOrganCycle.cycle(TissueCore.PHARYNX).cost());
        var session=new Session(UUID.randomUUID(),actor,prey,actor.level().getGameTime(),pharynx?TissueOrganCycle.cycle(TissueCore.PHARYNX).end():END_TICKS);
        ACTIVE.put(actor,session);CLAIMS.put(prey,session);
        TissueGrapples.release(actor);TissueGrapples.release(prey);
        actor.setCannibalVisual(prey.getId(),session.start(),false,session.duration());prey.setCannibalVisual(actor.getId(),session.start(),true,session.duration());
        actor.getNavigation().stop();prey.getNavigation().stop();prey.setTarget(null);
        return true;
    }
    /** True while this tick's locomotion/ordinary attacks belong to the scene. */
    public static boolean tick(BlacklightInfectedHost host){
        if(host.level().isClientSide)return false;
        var session=ACTIVE.get(host);
        if(session!=null){advance(session);return ACTIVE.containsKey(host);}
        if(CLAIMS.containsKey(host))return true;
        if(!participant(host)||!host.tissueInheritance().readyToHunt(host.tissueAge())||host.isNoAi()||!TissueCompetitionConfig.ENABLED.get())return false;
        if(host.getTarget() instanceof BlacklightInfectedHost prey&&contender(host,prey)){
            if(tryStart(host,prey))return true;
        }
        var current=host.getTarget();
        if(current instanceof BlacklightInfectedHost||!free(host)
                ||current!=null&&(host.hurtTime>0||current instanceof net.minecraft.world.entity.player.Player
                &&(host.isEvolved()||host.distanceToSqr(current)<24*24)))return false;
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
        var target=host.level().getEntitiesOfClass(BlacklightInfectedHost.class,host.getBoundingBox().inflate(radius),
                prey->contender(host,prey)&&!controls(prey)&&free(prey)&&host.hasLineOfSight(prey)
                        &&TissueInheritance.sizeFits(host.getBbWidth(),host.getBbHeight(),prey.getBbWidth(),prey.getBbHeight())
                        &&!prey.getPersistentData().getBoolean(SETTLED)
                        &&(TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||dominates(host,prey)))
                .stream().min(Comparator.<BlacklightInfectedHost>comparingDouble(h->h.getHealth()/h.getMaxHealth())
                        .thenComparingDouble(host::distanceToSqr)).orElse(null);
        if(target!=null)host.setTarget(target);return false;
    }
    private static double potential(BlacklightInfectedHost host){
        double total=TissueInheritance.nativeStrength(host.tissueAge());
        for(var organ:host.tissueInheritance().organs().values())total+=Math.log1p(organ.integrated())*10;
        return total;
    }
    private static boolean dominates(BlacklightInfectedHost actor,BlacklightInfectedHost prey){
        double self=potential(actor),other=potential(prey);
        // A stable tie-break allows equally old mature populations to compete too.
        // It does not reroll on every AI tick or make both creatures choose each other at birth.
        return self>other*1.05||self>=other*.95&&actor.getUUID().compareTo(prey.getUUID())<0;
    }
    private static void advance(Session session){
        var actor=session.actor();var prey=session.prey();long now=actor.level().getGameTime();
        if(!TissueCompetitionConfig.ENABLED.get()||!contender(actor,prey)||!free(actor)||!free(prey)||!contact(actor,prey)
                ||!TissueInheritance.weakened(prey.getHealth(),prey.getMaxHealth())||now<session.start()||now-session.start()>session.duration()+1){
            release(actor);return;
        }
        for(var host:List.of(actor,prey)){
            host.getNavigation().stop();host.setXxa(0);host.setZza(0);
            host.setDeltaMovement(new Vec3(0,Math.min(0,host.getDeltaMovement().y),0));
        }
        actor.getLookControl().setLookAt(prey,25,25);
        if(now-session.start()<session.duration())return;
        var donor=prey.tissueInheritance();long donorSeed=TissueOrgans.seed(prey.getUUID()),age=prey.tissueAge();int donorPlan=prey.nativeTissuePlan();
        // Mark before callbacks. A cancelled death rolls the mark back and grants nothing.
        prey.getPersistentData().putBoolean(SETTLED,true);prey.getPersistentData().putUUID("BlacklightCannibalOperation",session.operation());
        prey.invulnerableTime=0;
        boolean accepted=prey.hurt(prey.damageSources().mobAttack(actor),(float)Math.min(Float.MAX_VALUE,Math.max(10000D,prey.getMaxHealth()*20D)));
        if(accepted&&((LivingDeathAccess)(LivingEntity)prey).blacklight$deathAccepted()&&!prey.isAlive()&&prey.getKillCredit()==actor){
            if(TissueCompetitionConfig.INHERITANCE.get())actor.inheritTissue(session.operation(),donor,donorSeed,age,donorPlan,prey.knownNativeTissueCore(),prey.speciesId(),prey.hostData().toString());
            prey.setInvisible(true);
        }else{
            prey.getPersistentData().remove(SETTLED);prey.getPersistentData().remove("BlacklightCannibalOperation");
        }
        release(actor);
    }
    public static void damaged(BlacklightInfectedHost host,float actual){
        if(actual>=host.getMaxHealth()*.10F){var session=ACTIVE.get(host);if(session==null)session=CLAIMS.get(host);if(session!=null)release(session.actor());}
    }
    public static void release(BlacklightInfectedHost actor){
        var session=ACTIVE.remove(actor);if(session==null)return;CLAIMS.remove(session.prey());
        RETRY.put(actor,actor.level().getGameTime()+100);
        for(var host:List.of(actor,session.prey()))if(!host.isRemoved())host.setCannibalVisual(-1,-1,false);
        if(actor.getTarget()==session.prey())actor.setTarget(null);
    }
    public static void interrupt(Entity entity){
        var session=ACTIVE.get(entity);if(session==null)session=CLAIMS.get(entity);
        if(session!=null)release(session.actor());
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(event.getLevel().isClientSide)return;
        interrupt(event.getEntity());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.clear();CLAIMS.clear();BUDGETS.clear();RETRY.clear();SEARCH_AT.clear();}
}
