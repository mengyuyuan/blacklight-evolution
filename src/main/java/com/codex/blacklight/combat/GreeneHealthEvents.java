package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Preserve final-damage accounting for Greene's source mission floor. The
 * actuallyHurt final-write mixin remains the invariant if a later hook
 * changes damage; child redirect and administrative damage never enter here. */
@EventBusSubscriber(modid="blacklight")
public final class GreeneHealthEvents {
    private GreeneHealthEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void beforeHealthDamage(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof EncounterMob owner)||owner.level().isClientSide
                ||owner.kind()!=EncounterKind.ELIZABETH_GREENE||!owner.greeneOwnDamageActive()
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        // CombatScale also listens to Pre/LOWEST: another Pre/LOWEST listener
        // cannot establish a final ordering. Neo runs this modifier after ALL
        // Pre listeners, immediately before subtracting the returned absorption.
        event.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,absorption)->{
            float floor=owner.greeneMinimumHealth(),health=owner.getHealth(),damage=container.getNewDamage();
            // Native higher-floor writes can raise old health. Retain the
            // damage event and let the actuallyHurt write guard apply that case.
            if(!owner.greeneOwnDamageActive()||!Float.isFinite(floor)||floor<=0||floor>health
                    ||!Float.isFinite(damage)||damage<=0)return absorption;
            float absorbed=Float.isFinite(absorption)?Math.max(0,Math.min(absorption,damage)):0;
            container.setNewDamage(GreeneBattleHealth.limitBeforeAbsorption(health,damage,absorbed,floor));
            return absorbed;
        });
    }
}
