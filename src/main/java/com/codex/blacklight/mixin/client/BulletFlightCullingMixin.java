package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.ClientCarryPlacement;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Account for interpolated flight and consume poses outside the network culling box. */
@Mixin(EntityRenderer.class)
public abstract class BulletFlightCullingMixin {
    // A thrown receiver can be several blocks ahead of the network box that vanilla tested.
    @Inject(method="shouldRender",at=@At("RETURN"),cancellable=true)
    private void blacklight$visibleConsume(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValueZ() || !(entity instanceof LivingEntity victim)
                || !entity.shouldRender(x,y,z))return;
        var scene=ModAttachments.peekData(victim,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        if(scene.stage()!=ConsumeVisual.CONSUME)return;
        var mc=Minecraft.getInstance();
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        var drawn=victim.getPosition(partial).add(ClientCarryPlacement.consumeOffset(victim,partial));
        var bounds=victim.getBoundingBox().move(drawn.subtract(victim.position())).inflate(victim.getBbHeight()*.5);
        if(frustum.isVisible(bounds))result.setReturnValue(true);
    }

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
