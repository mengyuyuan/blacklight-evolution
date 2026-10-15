package com.codex.passives.mixin;
import com.codex.passives.PassiveApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
    @Redirect(method="actuallyHurt", at=@At(value="INVOKE", target="Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F", ordinal=3, remap=false))
    private float adaptivePassives$damage(DamageContainer container) {
        // When Blacklight is installed its bridge owns this call site (see mixin plugin).
        if ((Object)this instanceof ServerPlayer p) {
            float damage = PassiveApi.damage(p, container.getSource(), container.getNewDamage());
            container.setNewDamage(damage); return damage;
        }
        return container.getNewDamage();
    }
}
