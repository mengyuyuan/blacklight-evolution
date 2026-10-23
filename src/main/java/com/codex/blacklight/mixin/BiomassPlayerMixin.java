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
    @Inject(method="getDimensions",at=@At("HEAD"),cancellable=true)
    private void blacklight$liquidSize(Pose pose,CallbackInfoReturnable<EntityDimensions> ci){
        if(BiomassSystem.active((Player)(Object)this))ci.setReturnValue(EntityDimensions.scalable(com.codex.blacklight.power.BiomassEvolution.width((Player)(Object)this),com.codex.blacklight.power.BiomassEvolution.height((Player)(Object)this)));
    }
    @Inject(method="getStandingEyeHeight",at=@At("HEAD"),cancellable=true)
    private void blacklight$liquidEye(Pose pose,EntityDimensions size,CallbackInfoReturnable<Float> ci){if(BiomassSystem.active((Player)(Object)this))ci.setReturnValue(com.codex.blacklight.power.BiomassEvolution.height((Player)(Object)this)*.77F);}
    @Inject(method="getSpeed",at=@At("HEAD"),cancellable=true)
    private void blacklight$liquidSpeed(CallbackInfoReturnable<Float> ci){
        var state=BiomassSystem.state((Player)(Object)this);
        if(state.active())ci.setReturnValue(state.immobilized()?0F:com.codex.blacklight.power.ConsumeProgression.crawlSpeed((Player)(Object)this));
    }
}
