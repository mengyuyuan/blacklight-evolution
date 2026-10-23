package com.codex.blacklight.combat;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
/** Bounded startup grace; recovery, held charges, void and commands remain damageable. */
@EventBusSubscriber(modid="blacklight")
public final class CombatActionProtection {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingAttackEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        if(!ModAttachments.available(p))return;
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        if(state.isInfected()&&state.isPrototypeMode()&&WeaponCombatSystem.groundSpikeGrace(p))event.setCanceled(true);
    }
    private CombatActionProtection(){}
}
