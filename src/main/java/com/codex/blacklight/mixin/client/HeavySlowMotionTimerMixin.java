package com.codex.blacklight.mixin.client;

import com.codex.blacklight.combat.HeavySlowMotion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Timer.class)
public abstract class HeavySlowMotionTimerMixin {
    @ModifyExpressionValue(method="advanceTime",at=@At(value="FIELD",target="Lnet/minecraft/client/Timer;msPerTick:F"))
    private float blacklight$heavyTickInterval(float original){
        return HeavySlowMotion.clientMillis(Minecraft.getInstance().getSingleplayerServer(),original);
    }
}
