package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.AdaptiveRagdoll;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CrushDeathParticlesMixin {
    @Inject(method="handleEntityEvent",at=@At("HEAD"),cancellable=true)
    private void blacklight$localDeathEffect(byte event,CallbackInfo ci){
        if(event==60 && AdaptiveRagdoll.replacesDeathPoof((LivingEntity)(Object)this))ci.cancel();
    }
}
