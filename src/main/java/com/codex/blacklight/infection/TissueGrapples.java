package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Short server-authoritative tethers. No riding, teleport, saved NoAI or target modifications. */
@EventBusSubscriber(modid="blacklight")
public final class TissueGrapples {
    private record Hold(WeakReference<BlacklightInfectedHost> owner,long until){}
    private static final Map<LivingEntity,Hold> HOLDS=new WeakHashMap<>();
    private TissueGrapples(){}
    public static boolean grab(BlacklightInfectedHost host,LivingEntity target,int duration) {
        if(host.level().isClientSide||host==target||!host.isAlive()||!target.isAlive()
                ||Math.max(host.organPower(TissueCore.TONGUE),Math.max(host.organPower(TissueCore.GRASP),Math.max(host.organPower(TissueCore.CHELA),host.organPower(TissueCore.HYDROSTAT))))<=0
                ||TissueCompetition.controls(target)||TissueCompetition.controls(host)||ConsumeSystem.grappleProtected(target)||target.isPassenger()||target.isVehicle()
                ||target.getData(ModAttachments.CONSUME_VISUAL).active()
                ||target instanceof Player p&&(p.isCreative()||p.isSpectator())
                ||target.getBbWidth()>host.getBbWidth()*2||target.getBbHeight()>host.getBbHeight()*2
                ||!host.hasLineOfSight(target))return false;
        var claimant=owner(target);if(claimant!=null&&claimant!=host)return false;
        HOLDS.put(target,new Hold(new WeakReference<>(host),host.level().getGameTime()+Math.clamp(duration,1,20)));
        return true;
    }
    public static BlacklightInfectedHost owner(LivingEntity target) {
        if(target.level().isClientSide)return null;
        var hold=HOLDS.get(target);if(hold==null)return null;
        var host=hold.owner.get();
        if(host==null||host.isRemoved()||!host.isAlive()||target.isRemoved()||!target.isAlive()
                ||host.level()!=target.level()||host.level().getGameTime()>=hold.until
                ||!host.organAbilities().gripActive()&&(host.tissueStrikeElapsed(0)<TissueOrgans.HIT||host.tissueStrikeElapsed(0)>=TissueOrgans.DURATION)
                ||host.isNoAi()||host.getData(ModAttachments.CONSUME_VISUAL).active()
                ||target.getData(ModAttachments.CONSUME_VISUAL).active()
                ||target instanceof Player player&&(player.isCreative()||player.isSpectator())
                ||target.isPassenger()||target.isVehicle()
                ||target.distanceTo(host)>host.getBbWidth()+Math.max(host.tissueOrganReach(),host.organActionCore()==TissueCore.TONGUE?TissueOrganCycle.reach(TissueCore.TONGUE):0)+2||!host.hasLineOfSight(target)) {
            HOLDS.remove(target);return null;
        }
        return host;
    }
    public static void release(BlacklightInfectedHost host){HOLDS.entrySet().removeIf(e->e.getValue().owner.get()==host);}
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof LivingEntity target)||target.level().isClientSide)return;
        var host=owner(target);if(host==null)return;
        Vec3 delta=target.position().subtract(host.position()),horizontal=new Vec3(delta.x,0,delta.z).normalize();
        double gap=(host.getBbWidth()+target.getBbWidth())*.5+.35;
        Vec3 anchor=host.position().add(horizontal.scale(gap)).add(0,Math.min(.8,host.getBbHeight()*.18),0);
        Vec3 pull=anchor.subtract(target.position()).scale(.28);
        if(pull.lengthSqr()>.24*.24)pull=pull.normalize().scale(.24);
        target.setDeltaMovement(target.getDeltaMovement().scale(.35).add(pull));target.hurtMarked=true;
    }
    @SubscribeEvent public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if(event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof LivingEntity entity)HOLDS.remove(entity);
        if(event.getEntity() instanceof BlacklightInfectedHost host)release(host);
    }
    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppingEvent event){HOLDS.clear();}
}
