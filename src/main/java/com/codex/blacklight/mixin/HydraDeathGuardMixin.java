package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.BlacklightHydra;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both cached vanilla versions read dead once, immediately after the loader's
 * cancelable death hook and before score/loot. Keep a reloaded Hydra generation
 * out of an older death transaction; no change to other living entities. */
@Mixin(LivingEntity.class)
public abstract class HydraDeathGuardMixin {
    @Inject(method="die",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;dead:Z",opcode=Opcodes.GETFIELD,ordinal=0),cancellable=true)
    private void blacklight$guardHydraDeath(DamageSource source,CallbackInfo ci){
        if((Object)this instanceof BlacklightHydra hydra&&!hydra.acceptNativeDeathTransaction(source))ci.cancel();
    }
}
