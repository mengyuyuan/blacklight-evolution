
package com.codex.passives.gametest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
@EventBusSubscriber(modid="adaptive_passives")
public final class CancellationFixture {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hit(LivingIncomingDamageEvent e) { if(e.getEntity().getPersistentData().getBoolean("passive_test_cancel")) e.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if(e.getEntity().getPersistentData().getBoolean("passive_test_cancel_death")) e.setCanceled(true); }
}
