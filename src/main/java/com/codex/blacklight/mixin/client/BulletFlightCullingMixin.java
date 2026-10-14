package com.codex.blacklight.mixin.client;

import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla tests the current tick's box against an interpolated camera. A
 * local actor moving thousands of blocks/tick can be culled before rendering
 * its perfectly nearby interpolated pose. Scope the exemption to that actor. */
@Mixin(EntityRenderer.class)
public abstract class BulletFlightCullingMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void blacklight$keepLocalDive(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        var mc=Minecraft.getInstance();
        if(entity!=mc.player||mc.options.getCameraType().isFirstPerson())return;
        var visual=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.DEVASTATOR_VISUAL);
        if(visual.kind()!=3||visual.phase()!=DevastatorSystem.FLIGHT)return;
        var point=entity.getPosition(mc.getFrameTime());
        if(point.distanceToSqr(x,y,z)<128*128)result.setReturnValue(true);
    }
}
