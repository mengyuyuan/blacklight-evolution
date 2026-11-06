package com.codex.blacklight.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A missing renderer (bad registration in any mod) must skip one entity, not crash the client. Logs each type once. */
@Mixin(EntityRenderDispatcher.class)
public abstract class MissingRendererGuardMixin {
    private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> blacklight$reported=java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void blacklight$skipMissing(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        if(((EntityRenderDispatcher)(Object)this).getRenderer(entity)!=null)return;
        if(blacklight$reported.add(entity.getType()))
            com.mojang.logging.LogUtils.getLogger().error("Blacklight: no renderer for {} ({}), skipping it",
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),entity.getClass().getName());
        result.setReturnValue(false);
    }
}
