package com.codex.blacklight.mixin;
import com.codex.blacklight.power.BiomassSystem;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** No saved NoAI flag: interrupted absorption immediately releases the creature. */
@Mixin(Mob.class)
public abstract class BiomassPreyMixin {
    @Inject(method="serverAiStep",at=@At("HEAD"),cancellable=true)
    private void blacklight$heldByBiomass(CallbackInfo ci){
        Mob mob=(Mob)(Object)this;
        if(com.codex.blacklight.infection.TissueGrapples.owner(mob)!=null){mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);ci.cancel();return;}
        if(BiomassSystem.claimant(mob)!=null){mob.getNavigation().stop();mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);ci.cancel();}
    }
}
