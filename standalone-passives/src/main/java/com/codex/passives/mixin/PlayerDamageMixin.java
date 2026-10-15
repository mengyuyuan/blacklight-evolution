package com.codex.passives.mixin;
import com.codex.passives.PassiveApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ForgeHooks.class, remap=false)
public abstract class PlayerDamageMixin {
    @Inject(method="onLivingDamage", at=@At("RETURN"), cancellable=true)
    private static void adaptivePassives$damage(LivingEntity entity, DamageSource source, float damage, CallbackInfoReturnable<Float> ci) {
        if (entity instanceof ServerPlayer p) ci.setReturnValue(PassiveApi.damage(p, source, ci.getReturnValue()));
    }
}
