package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.camera.PrototypeChaseCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Transfer vanilla mouse deltas to Blacklight's independent orbit controller.
 *
 * <p>Epic Fight wraps the same {@code LocalPlayer.turn} call and, when its camera declines the delta, calls
 * {@code player.turn} itself instead of {@code original.call}. At equal priority it applies after this mixin, so it
 * becomes the outer wrapper and the delta never reaches this handler: the head follows the mouse while the body
 * stays on the orbit yaw. The higher priority keeps this wrapper outside. Whenever Blacklight does not own the view,
 * including Epic Fight's own third-person and lock-on cameras, {@code original.call} still hands the delta inward.
 */
@Mixin(value=MouseHandler.class,priority=1100)
public abstract class PrototypeTargetLockMixin {
    @WrapOperation(method="turnPlayer",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void blacklight$sourceMouse(LocalPlayer player,double x,double y,Operation<Void> original){
        if(!PrototypeChaseCamera.mouse(x,y))original.call(player,x,y);
    }
}
