package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.ConsumeProgression;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** A brief opening earned by accepted combat damage, never by standing near a directly consumable animal. */
@EventBusSubscriber(modid="blacklight")
public final class ConsumeOpportunity {
    private static final Map<Mob,Long> RETRY=new WeakHashMap<>();
    private ConsumeOpportunity(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damaged(com.codex.blacklight.combat.ForgeDamageEvent.Post event){
        if(event.getNewDamage()<=0||!(event.getEntity() instanceof Mob mob)||mob.level().isClientSide
                ||!(event.getSource().getEntity() instanceof ServerPlayer player))return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        if(!state.isInfected()||!state.isPrototypeMode()||!ConsumeSystem.eligible(mob)||!ConsumeSystem.healthReady(player,mob))return;
        double threshold=mob.getMaxHealth()*ConsumeProgression.healthLimit(ConsumeProgression.progress(player));
        if(mob.getHealth()>threshold)return;
        long now=mob.level().getGameTime();if(now<RETRY.getOrDefault(mob,Long.MIN_VALUE))return;
        if(ImpactControl.consumeOpening(mob,player.getYRot()))RETRY.put(mob,now+200);
    }
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(e.getEntity() instanceof Mob mob&&!mob.level().isClientSide)RETRY.remove(mob);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){RETRY.clear();}
}
