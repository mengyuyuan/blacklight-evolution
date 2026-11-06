package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.BlacklightPlayerRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerRenderer HEAD hooks can replace its complete model before the Pre
 * event, including The Boys/MCA selectors. Restore only Blacklight's renderer
 * at the last point before vanilla visibility and the body draw are prepared. */
@Mixin(PlayerRenderer.class)
public abstract class NativePlayerModelOwnershipMixin {
    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V"))
    private void blacklight$keepNativeRig(CallbackInfo callback) {
        if((Object)this instanceof BlacklightPlayerRenderer renderer)renderer.restoreNativeModel();
    }
}
