package com.codex.blacklight.mixin;

import com.codex.blacklight.power.TheBoysBodyCompatibility;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.benniao.theboysmod.server.ragdoll.FakeDeathService",remap=false)
public abstract class OptionalTheBoysFakeDeathMixin {
    @Inject(method="onDeath(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V",
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void blacklight$keepOwnDeathTransition(LivingDeathEvent event,CallbackInfo callback) {
        // Do not cancel/uncancel the death event here: let Blacklight and other
        // mods receive it, without The Boys converting it into its fake death.
        if(TheBoysBodyCompatibility.owns(event.getEntity()))callback.cancel();
    }
}
