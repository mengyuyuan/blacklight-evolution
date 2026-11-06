package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.mixin.LivingDeathAccess;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Death events are only candidates. Observe again after the full hurt/die override
 * has returned; a cancelled death, restored HP, source cure/unload or conversion
 * cannot pay a meal. The bounded queue holds weak entities and value data only. */
@EventBusSubscriber(modid="blacklight")
public final class NativeTissueMeals {
    private static final int LIMIT=512;
    private static final String PAID="BlacklightTissueMealSettled";
    private record Candidate(UUID victim,WeakReference<Mob> prey,WeakReference<Mob> actor,int meal){}
    private static final ArrayDeque<Candidate> PENDING=new ArrayDeque<>();
    private static final Set<UUID> QUEUED=new HashSet<>();
    private NativeTissueMeals(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof Mob prey)||!(prey.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!(event.getSource().getEntity() instanceof Mob actor)||actor instanceof BlacklightInfectedHost
                ||!ModAttachments.available(actor)||!actor.isAlive()||actor.level()!=level||!NativeInfection.active(actor)
                ||BlacklightInfectionSystem.isInfected(prey)||prey.getPersistentData().getBoolean(PAID)
                ||PENDING.size()>=LIMIT||QUEUED.contains(prey.getUUID()))return;
        int meal=TissueMetabolism.meal(CombatScale.originalMaximum(prey),NativeTissue.knownCore(actor));
        if(meal<=0)return;
        QUEUED.add(prey.getUUID());PENDING.addLast(new Candidate(prey.getUUID(),new WeakReference<>(prey),new WeakReference<>(actor),meal));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        int count=Math.min(LIMIT,PENDING.size());
        for(int i=0;i<count;i++){
            var candidate=PENDING.removeFirst();QUEUED.remove(candidate.victim());
            var prey=candidate.prey().get();var actor=candidate.actor().get();
            if(prey==null||actor==null||!ModAttachments.available(actor)||!actor.isAlive()||!NativeInfection.active(actor)
                    ||prey.level()!=actor.level()||prey.isAlive()||prey.getHealth()>0||prey.getKillCredit()!=actor
                    ||!((LivingDeathAccess)prey).blacklight$deathAccepted()||prey.getPersistentData().getBoolean(PAID))continue;
            // Mark before any publication callback. Converted-host killedEntity uses
            // the same marker and cannot pay an already-consumed corpse a second time.
            prey.getPersistentData().putBoolean(PAID,true);NativeTissueDigestion.meal(actor,candidate.meal());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PENDING.clear();QUEUED.clear();}
}
