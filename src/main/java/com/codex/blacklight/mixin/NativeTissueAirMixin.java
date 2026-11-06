package com.codex.blacklight.mixin;

import com.codex.blacklight.infection.NativeTissueVitals;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class NativeTissueAirMixin {
    @Inject(method="getMaxAirSupply",at=@At("RETURN"),cancellable=true)
    private void blacklight$nativeLungCapacity(CallbackInfoReturnable<Integer> cir){
        // The ownership tag is also a construction guard: do not ask a newly
        // constructing foreign mob for effects, health or capabilities.
        if((Object)this instanceof Mob mob&&mob.level()!=null&&!mob.level().isClientSide&&mob.getPersistentData().contains("BlacklightNativeInfection",10)
                &&NativeTissueVitals.eligible(mob)){
            var p=NativeTissueVitals.profile(mob);if(p.lung()>0||p.airflow()>0)cir.setReturnValue(p.maxAir(cir.getReturnValue()));
        }
    }
}
