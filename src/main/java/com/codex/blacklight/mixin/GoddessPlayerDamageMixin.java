package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.GoddessDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class GoddessPlayerDamageMixin {
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void blacklight$goddessBudget(Player player,float next,Operation<Void> original,@Local(argsOnly=true) DamageSource source){
        original.call(player,GoddessDamage.health(player,source,next));
    }
}
