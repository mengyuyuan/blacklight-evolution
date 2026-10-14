package com.codex.blacklight.mixin;

import com.codex.blacklight.power.AdaptivePassiveSystem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The fourth read is the final HP damage, after all absorption callbacks and golden-heart consumption. */
@Mixin(Player.class)
public abstract class AdaptivePlayerDamageMixin {
    @Redirect(method="actuallyHurt", at=@At(value="INVOKE",
            target="Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F", ordinal=3, remap=false))
    private float blacklight$adaptiveFinalDamage(DamageContainer container) {
        float result = AdaptivePassiveSystem.finalDamage((Player)(Object)this, container.getSource(), container.getNewDamage());
        container.setNewDamage(result);
        return result;
    }
}
