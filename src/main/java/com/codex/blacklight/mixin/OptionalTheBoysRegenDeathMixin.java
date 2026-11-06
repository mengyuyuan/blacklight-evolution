package com.codex.blacklight.mixin;

import com.codex.blacklight.power.TheBoysBodyCompatibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.benniao.theboysmod.server.PlayerRegenService",remap=false)
public abstract class OptionalTheBoysRegenDeathMixin {
    @Inject(method="enterDeath(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/damagesource/DamageSource;)Z",
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void blacklight$keepOwnRegeneration(ServerPlayer player,DamageSource source,CallbackInfoReturnable<Boolean> callback) {
        if(TheBoysBodyCompatibility.owns(player))callback.setReturnValue(false);
    }
}
