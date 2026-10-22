package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.ConsumeControl;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spore pulls write velocity directly, including while the player is holding prey. */
@Pseudo
@Mixin(targets="com.Harbinger.Spore.Sentities.AI.PullGoal",remap=false)
public abstract class OptionalSporePullMixin {
    @Shadow @Final protected Mob mob;
    @Inject(method={"start","m_8056_"},at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void blacklight$keepConsumeClaim(CallbackInfo ci){
        if(mob.getTarget()!=null&&ConsumeControl.protectedEntity(mob.getTarget()))ci.cancel();
    }
}
