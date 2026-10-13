package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.ConsumeSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.LivingEntity;
import com.codex.blacklight.registry.ModAttachments;

/** Damage still applies; capture, holding and absorption cannot be replaced by a stun animation. */
@Mixin(LivingEntity.class)
public abstract class ConsumeStunMixin {
    @Inject(method="knockback",at=@At("HEAD"),cancellable=true)
    private void blacklight$consumeStun(double strength,double x,double z,CallbackInfo ci) {
        var entity=(LivingEntity)(Object)this;
        if(ConsumeSystem.grappleProtected(entity)||ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL).active())ci.cancel();
    }
}
