package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Geometry access belongs to a rendering framework, never to an entity ID list.
 * Bindings are frame-local; a scope must not modify the source model or animation. */
public interface NativeVisualAdapter {
    ResourceLocation id();
    Binding bind(Mob entity,EntityRenderer<?> renderer,PoseStack stack,float partial);
    interface Scope extends AutoCloseable {Scope NONE=()->{};@Override void close();}
    record Binding(EntityModel<?> model,ResourceLocation texture,Scope scope){
        public Binding{if(scope==null)scope=Scope.NONE;}
    }
}
