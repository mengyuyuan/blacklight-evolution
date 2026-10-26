package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Viral physiology survives switching back to ordinary survival controls. */
@EventBusSubscriber(modid="blacklight")
public final class InfectedPhysiology {
    private InfectedPhysiology(){}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof Player p)||p.level().isClientSide||event.isCanceled()
                ||event.getAmount()<=0||!p.getData(ModAttachments.PLAYER_STATE).isInfected()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                ||com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(event.getSource()))return;
        var source=event.getSource();
        if(source.is(DamageTypeTags.IS_FALL)){event.setCanceled(true);return;}
        float factor=source.is(DamageTypeTags.IS_FIRE)?.2F:
                source.is(DamageTypeTags.IS_EXPLOSION)?.8F:!p.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()?.8F:1;
        event.setAmount(event.getAmount()*factor);
    }
    @SubscribeEvent
    public static void mining(PlayerEvent.BreakSpeed event){
        var p=event.getEntity();var state=p.getData(ModAttachments.PLAYER_STATE);
        if(state.isInfected()&&!state.isPrototypeMode()&&!event.isCanceled())
            event.setNewSpeed(event.getNewSpeed()*1.5F);
    }
}
