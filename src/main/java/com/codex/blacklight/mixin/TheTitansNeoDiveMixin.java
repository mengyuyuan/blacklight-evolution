package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.BulletdiveDamage;
import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Optional TheTitansNeo 1.0.8 bridge: retain its own damage and death pipeline. */
@Pseudo
@Mixin(targets="net.byAqua3.thetitansneo.entity.titan.EntityTitan",remap=false)
public abstract class TheTitansNeoDiveMixin {
    // Both the comparison and assignment use 1000. No HP writes, repeated hits,
    // immunity cancellation or changes to other weapons are necessary.
    @ModifyConstant(method="hurt",constant=@Constant(floatValue=1000F),require=0,expect=2)
    private float blacklight$uncapDive(float limit,DamageSource source,float amount) {
        return source.is(BulletdiveDamage.TYPE) && Float.isFinite(amount) && amount>0
                && source.getEntity() instanceof Player player && PrototypeMetabolism.active(player)
                ? Float.MAX_VALUE : limit;
    }
}
