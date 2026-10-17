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

/** Vanilla tests the current tick's box against an interpolated camera. Fast
 * dives and powered wing flight can cull a perfectly nearby interpolated pose.
 * Scope the exemption to the local third-person actor and a bounded distance. */
@Mixin(EntityRenderer.class)
public abstract class BulletFlightCullingMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void blacklight$keepLocalDive(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        var mc=Minecraft.getInstance();
        if(entity!=mc.player||mc.options.getCameraType().isFirstPerson())return;
        var visual=mc.player.getData(ModAttachments.DEVASTATOR_VISUAL);
        boolean dive=visual.kind()==3&&visual.phase()==DevastatorSystem.FLIGHT;
        if(!dive&&!com.codex.blacklight.movement.WingFlightSystem.flying(mc.player))return;
        var point=entity.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));
        if(point.distanceToSqr(x,y,z)<128*128)result.setReturnValue(true);
    }
}
