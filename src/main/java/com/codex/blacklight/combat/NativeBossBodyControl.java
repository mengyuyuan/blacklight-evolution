package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Greene and Goliath's live skin and collision belong to their shared native
 * pose. Generic impact physics cannot replace it; death and confirmed paired
 * consumption retain their existing ownership handoff. */
public final class NativeBossBodyControl {
    public static boolean ownsPose(LivingEntity entity) {
        if(!entity.isAlive()||entity.isRemoved()||!(entity instanceof EncounterMob mob)
                ||mob.kind()!=EncounterKind.ELIZABETH_GREENE&&mob.kind()!=EncounterKind.GOLIATH)return false;
        var visual=ModAttachments.peekData(entity,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        boolean pairedStage=ConsumeVisual.acquiring(visual.stage())||ConsumeVisual.holding(visual.stage())
                ||ConsumeVisual.consumeStage(visual.stage())
                ||visual.stage()==ConsumeVisual.THROW;
        if(visual.active()&&pairedStage&&entity.level().getEntity(visual.consumerId()) instanceof LivingEntity consumer
                &&consumer.isAlive()&&!consumer.isRemoved())return false;
        // This checks the live absorber's victim ID and contact/attachment, not
        // an arbitrary historical effect or a low-health eligibility window.
        return BiomassSystem.claimant(entity)==null;
    }
    private NativeBossBodyControl(){}
}
