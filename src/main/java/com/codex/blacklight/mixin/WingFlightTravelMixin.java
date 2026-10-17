package com.codex.blacklight.mixin;
import com.codex.blacklight.movement.WingFlightSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class WingFlightTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void blacklight$wingTravel(Vec3 input,CallbackInfo ci){
        Player p=(Player)(Object)this;
        if(!WingFlightSystem.ownsTravel(p))return;
        if(!p.level().isClientSide){
            // The packet handler applies swept collision/movement. Post-tick computes
            // the shared controller once; vanilla gravity/drag must not damp it again.
            p.resetFallDistance();ci.cancel();return;
        }
        if(!p.isControlledByLocalInstance())return;
        p.setDeltaMovement(WingFlightSystem.nextVelocity(p));
        p.move(MoverType.SELF,p.getDeltaMovement());p.resetFallDistance();
        p.calculateEntityAnimation(false);ci.cancel();
    }
}
