package com.codex.blacklight.combat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid="blacklight")
public final class CrowdKnockback {
    private static final Map<ServerPlayer,CrowdPressure> PRESSURE=new WeakHashMap<>();
    private CrowdKnockback(){}
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void knockback(LivingKnockBackEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||p.isCreative()||p.isSpectator())return;
        float scale=PRESSURE.computeIfAbsent(p,k->new CrowdPressure()).accept(p.level().getGameTime(),WeaponCombatSystem.busy(p));
        if(scale<=0)event.setCanceled(true);else event.setStrength(event.getStrength()*scale);
    }
}