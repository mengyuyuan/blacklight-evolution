package com.codex.blacklight.combat;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
/** Bounded startup grace; recovery, held charges, void and commands remain damageable. */
@EventBusSubscriber(modid="blacklight")
public final class CombatActionProtection {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var state=p.getData(ModAttachments.PLAYER_STATE);
        if(state.isInfected()&&state.isPrototypeMode()&&WeaponCombatSystem.groundSpikeGrace(p))event.setCanceled(true);
    }
    private CombatActionProtection(){}
}
