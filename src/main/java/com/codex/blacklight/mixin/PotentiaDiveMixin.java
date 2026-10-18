package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.BulletdiveDamage;
import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional Potentia bridge; retain eligibility, stamina, damage events and death. */
@Pseudo
@Mixin(targets="com.potentia.entity.EntityTitan",remap=false)
public abstract class PotentiaDiveMixin {
    // Explicit development/production names: this optional class is not on the compile classpath.
    @Redirect(method={"hurt","m_6469_"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(FF)F"),require=0,expect=1)
    private float blacklight$heightScaledImpact(float amount,float cap,DamageSource source,float incoming) {
        return source.is(BulletdiveDamage.TYPE) && Float.isFinite(amount) && amount>0
                && source.getEntity() instanceof Player player && PrototypeMetabolism.active(player)
                ? amount : Math.min(amount,cap);
    }
}
