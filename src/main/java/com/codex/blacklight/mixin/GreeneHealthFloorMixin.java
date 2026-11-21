package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Guard only vanilla's actual-damage health write, before death handling.
 * An administrative setHealth/kill callback is not intercepted or undone. */
@Mixin(LivingEntity.class)
public abstract class GreeneHealthFloorMixin {
    @ModifyArg(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),index=0)
    private float blacklight$greeneHealthFloor(float nextHealth){
        nextHealth = com.codex.blacklight.combat.ConsumeContactDamage.healthFloor((LivingEntity)(Object)this, nextHealth);
        if((Object)this instanceof EncounterMob owner&&owner.greeneOwnDamageActive()&&owner.isAlive()&&Float.isFinite(nextHealth))
            return Math.max(nextHealth,owner.greeneMinimumHealth());
        return nextHealth;
    }
}
