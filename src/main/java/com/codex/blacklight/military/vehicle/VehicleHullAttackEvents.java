package com.codex.blacklight.military.vehicle;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Installs only on the existing military/infected families, once per real entity selector. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleHullAttackEvents {
    private VehicleHullAttackEvents(){}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Mob actor)||!VehicleHullAttackGoal.supports(actor.getClass()))return;
        if(actor.goalSelector.getAvailableGoals().stream().noneMatch(wrapped->wrapped.getGoal() instanceof VehicleHullAttackGoal))
            actor.goalSelector.addGoal(1,new VehicleHullAttackGoal(actor));
    }
}
