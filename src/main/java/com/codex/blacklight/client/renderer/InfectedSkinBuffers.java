package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import java.util.*;

/** Retain species pixels beneath authored block anatomy; no random surface spikes. */
final class InfectedSkinBuffers implements MultiBufferSource {
    private final MultiBufferSource delegate;
    InfectedSkinBuffers(MultiBufferSource delegate){this.delegate=delegate;}
    @Override public VertexConsumer getBuffer(RenderType type){
        String name=type.toString().toLowerCase(Locale.ROOT);
        if(type.isOutline()||name.contains("eyes")||name.contains("glint")||name.contains("shadow")||name.contains("infected_morphology"))return delegate.getBuffer(type);
        // Asking for another render type can flush BufferSource's current builder.
        // Never cache a consumer that may have been ended by a copied model layer.
        return new Skin(delegate.getBuffer(InfectedMorphology.skinType(type)));
    }
    private record Skin(VertexConsumer out) implements VertexConsumer,ConsumeAnatomy.Consumer {
        @Override public VertexConsumer addVertex(float x,float y,float z){out.addVertex(x,y,z);return this;}
        // Decay is local geometry, not an opaque dark-red coat over every species.
        // Keep sheep colours, cow spots, fish variants and village clothing readable.
        @Override public VertexConsumer setColor(int r,int g,int b,int a){out.setColor(r,g,b,a);return this;}
        @Override public VertexConsumer setUv(float u,float v){out.setUv(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){out.setUv1(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){out.setUv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){out.setNormal(x,y,z);return this;}
        @Override public void anatomy(int id){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(id);}
        @Override public void anatomy(float[] weights){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(weights);}
        @Override public void anatomy(ModelPart p){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(p);}
    }
}
