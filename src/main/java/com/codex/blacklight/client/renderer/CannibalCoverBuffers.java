package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueCompetition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Covers the posed host surface itself. No pillars fitted to a bounding box.
 * Shared species models stay untouched and all observers sample the server clock. */
final class CannibalCoverBuffers implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final Matrix4f inverse;
    private final float progress,height;
    private final long seed;
    CannibalCoverBuffers(MultiBufferSource delegate,BlacklightInfectedHost host,PoseStack stack,float partial){
        this.delegate=delegate;inverse=new Matrix4f(stack.last().pose()).invert();height=host.getBbHeight();
        progress=com.codex.blacklight.forge.WingMath.clamp((host.level().getGameTime()-host.cannibalStarted()+partial)/TissueCompetition.COVER_TICKS,0,1);
        seed=host.getUUID().getLeastSignificantBits();
    }
    @Override public VertexConsumer getBuffer(RenderType type){
        return type.isOutline()?delegate.getBuffer(type):new Cover(delegate.getBuffer(type));
    }
    private final class Cover extends com.codex.blacklight.forge.ForgeVertexConsumer implements ConsumeAnatomy.Consumer {
        private final VertexConsumer out;private float coverage,vein;
        private final Vector3f point=new Vector3f();
        Cover(VertexConsumer out){this.out=out;}
        @Override public VertexConsumer addVertex(float x,float y,float z){
            inverse.transformPosition(point.set(x,y,z));
            float organic=(float)(Math.sin(point.x*13+point.z*9+(seed&31))*.06);
            coverage=com.codex.blacklight.forge.WingMath.clamp((progress*1.25F-point.y/Math.max(.2F,height)+organic)*5,0,1);
            vein=(float)Math.pow(Math.max(0,Math.sin(point.x*28+Math.sin(point.y*12)*2+point.z*17)),14);
            out.vertex(x,y,z);return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){
            float red=.09F+.35F*vein,green=.065F+.025F*vein,blue=.075F+.035F*vein;
            out.color((int)(r*(1-coverage)+255*red*coverage),(int)(g*(1-coverage)+255*green*coverage),
                    (int)(b*(1-coverage)+255*blue*coverage),a);return this;
        }
        @Override public VertexConsumer setUv(float u,float v){out.uv(u,v);return this;}
        @Override public VertexConsumer setUv1(int u,int v){out.overlayCoords(u,v);return this;}
        @Override public VertexConsumer setUv2(int u,int v){out.uv2(u,v);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){out.normal(x,y,z).endVertex();return this;}
        @Override public void anatomy(int id){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(id);}
        @Override public void anatomy(float[] weights){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(weights);}
        @Override public void anatomy(net.minecraft.client.model.geom.ModelPart p){if(out instanceof ConsumeAnatomy.Consumer c)c.anatomy(p);}
    }
}
