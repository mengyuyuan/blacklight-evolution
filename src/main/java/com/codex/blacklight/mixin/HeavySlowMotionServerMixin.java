package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.HeavySlowMotion;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MinecraftServer.class)
public abstract class HeavySlowMotionServerMixin {
    // Keep the tick deadline, overload correction and delayed-task budget in the same units.
    @ModifyConstant(method="runServer",constant=@Constant(longValue=50L),require=4,expect=4)
    private long blacklight$heavyTickInterval(long original){
        return HeavySlowMotion.serverMillis((MinecraftServer)(Object)this,original);
    }
}
