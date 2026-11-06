package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.NativePlayerRenderScope;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Boys: New World's separate regrowing body cannot be overlaid on a native
 * Blacklight body. Ordinary superhero players retain their limb renderer. */
@Pseudo
@Mixin(targets="net.benniao.theboysmod.client.renderer.FleshRegenEntityRenderer",remap=false)
public abstract class OptionalTheBoysBodyRenderMixin {
    @Inject(method={
            "renderFleshRegenOnPlayer(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            "renderFleshRegenArmsOnPlayer(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            "renderFleshRegenLegsOnPlayer(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"},
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void blacklight$keepOwnRegrowingBody(Player player,PoseStack stack,MultiBufferSource buffers,
            int light,float partial,CallbackInfo callback) {
        if(NativePlayerRenderScope.owns(player))callback.cancel();
    }
}
