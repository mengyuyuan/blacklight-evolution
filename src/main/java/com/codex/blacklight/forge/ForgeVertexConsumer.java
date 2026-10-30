package com.codex.blacklight.forge;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Adapter for the project's 1.21 capture/decorating consumers. Complete cube
 * vertices retain their packed colour, UV, light and normal values. Decorating
 * consumers finish their delegate in setNormal; this adapter's end is a no-op. */
public abstract class ForgeVertexConsumer implements VertexConsumer {
    public abstract VertexConsumer addVertex(float x,float y,float z);
    public abstract VertexConsumer setColor(int r,int g,int b,int a);
    public abstract VertexConsumer setUv(float u,float v);
    public abstract VertexConsumer setUv1(int u,int v);
    public abstract VertexConsumer setUv2(int u,int v);
    public abstract VertexConsumer setNormal(float x,float y,float z);
    @Override public final VertexConsumer vertex(double x,double y,double z){return addVertex((float)x,(float)y,(float)z);}
    @Override public final VertexConsumer color(int r,int g,int b,int a){return setColor(r,g,b,a);}
    @Override public final VertexConsumer uv(float u,float v){return setUv(u,v);}
    @Override public final VertexConsumer overlayCoords(int u,int v){return setUv1(u,v);}
    @Override public final VertexConsumer uv2(int u,int v){return setUv2(u,v);}
    @Override public final VertexConsumer normal(float x,float y,float z){return setNormal(x,y,z);}
    @Override public void endVertex(){}
    @Override public void defaultColor(int r,int g,int b,int a){setColor(r,g,b,a);}
    @Override public void unsetDefaultColor(){}
    @Override public final void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
        int rgba=(channel(a)<<24)|(channel(r)<<16)|(channel(g)<<8)|channel(b);
        addVertex(x,y,z,rgba,u,v,overlay,light,nx,ny,nz);
    }
    public void addVertex(float x,float y,float z,int rgba,float u,float v,int overlay,int light,float nx,float ny,float nz){
        addVertex(x,y,z);setColor((rgba>>>16)&255,(rgba>>>8)&255,rgba&255,(rgba>>>24)&255);
        setUv(u,v);setUv1(overlay&65535,(overlay>>>16)&65535);setUv2(light&65535,(light>>>16)&65535);setNormal(nx,ny,nz);
    }
    private static int channel(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
}
