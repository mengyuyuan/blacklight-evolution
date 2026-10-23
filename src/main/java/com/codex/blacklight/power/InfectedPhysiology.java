package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Viral physiology survives switching back to ordinary survival controls. */
@EventBusSubscriber(modid="blacklight")
public final class InfectedPhysiology {
    private InfectedPhysiology(){}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void incoming(LivingHurtEvent event){
        if(!(event.getEntity() instanceof Player p)||p.level().isClientSide||event.isCanceled()
                ||event.getAmount()<=0||!ModAttachments.getData(p,ModAttachments.PLAYER_STATE).isInfected()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var source=event.getSource();
        if(source.is(DamageTypeTags.IS_FALL)){event.setCanceled(true);return;}
        float factor=source.is(DamageTypeTags.IS_FIRE)?.2F:
                source.is(DamageTypeTags.IS_EXPLOSION)?.8F:!ModAttachments.getData(p,ModAttachments.PLAYER_STATE).isPrototypeMode()?.8F:1;
        event.setAmount(event.getAmount()*factor);
    }
    @SubscribeEvent
    public static void mining(PlayerEvent.BreakSpeed event){
        var p=event.getEntity();var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        if(state.isInfected()&&!state.isPrototypeMode()&&!event.isCanceled())
            event.setNewSpeed(event.getNewSpeed()*1.5F);
    }
}
