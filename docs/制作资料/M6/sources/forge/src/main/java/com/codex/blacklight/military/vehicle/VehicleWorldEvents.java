package com.codex.blacklight.military.vehicle;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
/** One production coordinator after actual entities tick. No player-client movement ownership. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleWorldEvents {
 private VehicleWorldEvents(){}
 @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level){VehicleGarageService.tick(level);VehicleFleetSystem.tick(level);}}
}
