package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.camera.PrototypeChaseCamera;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;

/** Apply the independent follow camera after vanilla has established its view. */
@Mixin(Camera.class)
public abstract class PrototypeChaseCameraMixin {
    @WrapMethod(method="setup")
    private void blacklight$finishCamera(BlockGetter level,Entity entity,boolean detached,
            boolean reverse,float partial,Operation<Void> original) {
        boolean cinematic=com.codex.blacklight.client.camera.HydraCinematicCamera.update();
        boolean executing=com.codex.blacklight.client.camera.GoddessExecutionCamera.update(entity);
        original.call(level,entity,cinematic||executing||detached,cinematic||executing?false:reverse,partial);
        if(executing){com.codex.blacklight.client.camera.GoddessExecutionCamera.apply((Camera)(Object)this,partial);return;}
        if(cinematic){com.codex.blacklight.client.camera.HydraCinematicCamera.apply((Camera)(Object)this);return;}
        if(com.codex.blacklight.client.vehicle.VehicleCamera.apply((Camera)(Object)this,partial))return;
        PrototypeChaseCamera.apply((Camera)(Object)this,partial);
        com.codex.blacklight.client.ClientBiomass.camera((Camera)(Object)this,partial);
        com.codex.blacklight.client.BlockThrowCamera.apply((Camera)(Object)this,partial);
        com.codex.blacklight.client.animation.GoddessPressurePose.eyes((Camera)(Object)this,partial);
    }
}
