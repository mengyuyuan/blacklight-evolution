
package com.codex.passives.gametest;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
@EventBusSubscriber(modid="adaptive_passives")
public final class CancellationFixture {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hit(LivingAttackEvent e) { if(e.getEntity().getPersistentData().getBoolean("passive_test_cancel")) e.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if(e.getEntity().getPersistentData().getBoolean("passive_test_cancel_death")) e.setCanceled(true); }
}
