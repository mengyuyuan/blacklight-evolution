package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** Preserve final-damage accounting for Greene's source mission floor. The
 * actuallyHurt final-write mixin remains the invariant if a later hook
 * changes damage; child redirect and administrative damage never enter here. */
@Mod.EventBusSubscriber(modid="blacklight")
public final class GreeneHealthEvents {
    private GreeneHealthEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void beforeHealthDamage(LivingDamageEvent event){
        if(!(event.getEntity() instanceof EncounterMob owner)||owner.level().isClientSide
                ||owner.kind()!=EncounterKind.ELIZABETH_GREENE||!owner.greeneOwnDamageActive()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        float floor=owner.greeneMinimumHealth(),health=owner.getHealth();
        // Forge47 LivingDamageEvent already contains the post-absorption
        // amount, unlike Neo21 LivingDamageEvent.Pre.
        if(!Float.isFinite(floor)||floor<=0||floor>health)return;
        event.setAmount(GreeneBattleHealth.limitFinalDamage(health,event.getAmount(),floor));
    }
}
