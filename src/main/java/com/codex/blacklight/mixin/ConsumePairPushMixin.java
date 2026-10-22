package com.codex.blacklight.mixin;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.combat.ConsumeVisual;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A choreographed pair cannot push each other out of their shared animation scene. */
@Mixin(Entity.class)
public abstract class ConsumePairPushMixin {
    @Inject(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
    private void blacklight$pairedPush(Entity other,CallbackInfo ci){
        Entity self=(Entity)(Object)this;
        if(com.codex.blacklight.combat.ConsumeControl.protectedEntity(self)
                ||com.codex.blacklight.combat.ConsumeControl.protectedEntity(other)){ci.cancel();return;}
        if(self instanceof LivingEntity living&&ModAttachments.peekData(living, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).consumerId()==other.getId()
                ||other instanceof LivingEntity livingOther&&ModAttachments.peekData(livingOther, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).consumerId()==self.getId())ci.cancel();
    }
}
