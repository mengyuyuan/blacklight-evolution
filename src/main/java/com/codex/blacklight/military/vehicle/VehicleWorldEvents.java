package com.codex.blacklight.military.vehicle;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** One production coordinator after actual entities tick. No player-client movement ownership. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleWorldEvents {
 private VehicleWorldEvents(){}
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;if(event.level instanceof ServerLevel level){VehicleGarageService.tick(level);VehiclePatrolSystem.tick(level);VehicleFleetSystem.tick(level);}}
}
