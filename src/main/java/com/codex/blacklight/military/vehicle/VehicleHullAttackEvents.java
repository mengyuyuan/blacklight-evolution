package com.codex.blacklight.military.vehicle;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

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
