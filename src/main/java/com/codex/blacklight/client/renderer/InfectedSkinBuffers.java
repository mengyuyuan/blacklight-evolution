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
        return new Skin(delegate.getBuffer(InfectedMorphology.skinType(type)),InfectedMorphology.glowSink(type));
    }
    private record Skin(VertexConsumer out,VertexConsumer glow) implements VertexConsumer,ConsumeAnatomy.Consumer {
        public void endVertex(){if(out!=null)out.endVertex();if(glow!=null)glow.endVertex();}
        public void defaultColor(int r,int g,int b,int a){if(out!=null)out.defaultColor(r,g,b,a);}
        public void unsetDefaultColor(){if(out!=null)out.unsetDefaultColor();}
        @Override public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);if(glow!=null)glow.vertex(x,y,z);return this;}
        // Decay is local geometry, not an opaque dark-red coat over every species.
        // Keep sheep colours, cow spots, fish variants and village clothing readable.
        @Override public VertexConsumer color(int r,int g,int b,int a){out.color(r,g,b,a);if(glow!=null)glow.color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){out.uv(u,v);if(glow!=null)glow.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);if(glow!=null)glow.normal(x,y,z);return this;}
        @Override public void anatomy(int id){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(id);}
        @Override public void anatomy(float[] weights){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(weights);}
        @Override public void anatomy(ModelPart p){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(p);}
    }
}
