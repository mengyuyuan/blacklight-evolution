package com.codex.blacklight.visualqa;

import com.codex.blacklight.client.StandaloneVisualSmoke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Photographer only, absent from production builds and native-camera cases. */
@Mixin(GameRenderer.class)
public abstract class VisualObserverMixin {
    @WrapOperation(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    private void blacklight$observe(Camera camera,BlockGetter level,Entity actor,boolean detached,boolean reverse,float partial,Operation<Void> original){
        original.call(camera,level,actor,detached,reverse,partial);
        StandaloneVisualSmoke.observerCamera(camera,partial);
    }
}
