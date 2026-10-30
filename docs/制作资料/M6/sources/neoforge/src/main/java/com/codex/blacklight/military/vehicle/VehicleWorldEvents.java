package com.codex.blacklight.military.vehicle;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
/** One production coordinator after actual entities tick. No player-client movement ownership. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleWorldEvents {
 private VehicleWorldEvents(){}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level){VehicleGarageService.tick(level);VehicleFleetSystem.tick(level);}}
}
