package com.codex.blacklight.mixin;

import com.codex.blacklight.power.BiomassSystem;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class BiomassPlayerMixin {
    /** Fatal recovery is a last chance to seek prey, not another low-health combat body. */
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void blacklight$deathSlimeProtection(net.minecraft.world.damagesource.DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        var state=BiomassSystem.state((Player)(Object)this);
        if(state.active()&&state.fatal()&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            ci.setReturnValue(false);
    }

    @Inject(method="getDefaultDimensions",at=@At("HEAD"),cancellable=true)
    private void blacklight$liquidSize(Pose pose,CallbackInfoReturnable<EntityDimensions> ci){
        if(BiomassSystem.active((Player)(Object)this))ci.setReturnValue(EntityDimensions.scalable(com.codex.blacklight.power.BiomassEvolution.width((Player)(Object)this),com.codex.blacklight.power.BiomassEvolution.height((Player)(Object)this)).withEyeHeight(com.codex.blacklight.power.BiomassEvolution.height((Player)(Object)this)*.77F));
    }
    @Inject(method="getSpeed",at=@At("HEAD"),cancellable=true)
    private void blacklight$liquidSpeed(CallbackInfoReturnable<Float> ci){
        var state=BiomassSystem.state((Player)(Object)this);
        if(state.active())ci.setReturnValue(state.immobilized()?0F:com.codex.blacklight.power.ConsumeProgression.crawlSpeed((Player)(Object)this));
    }
}
