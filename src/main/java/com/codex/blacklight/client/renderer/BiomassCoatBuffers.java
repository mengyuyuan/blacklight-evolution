package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Black liquid coats the real posed creature mesh, then reshapes it beneath that coating. */
public final class BiomassCoatBuffers implements MultiBufferSource {
    private final MultiBufferSource target;
    private final Matrix4f frame,inverse;
    private final float coverage,morph;
    private final BiomassSurfaceBounds surface=new BiomassSurfaceBounds();
    private final double time;
    private final Map<RenderType,Collector> streams=new LinkedHashMap<>();
    public BiomassCoatBuffers(LivingEntity mob,PoseStack stack,MultiBufferSource target,float coverage,float morph,double time){
        this.target=target;this.coverage=coverage;this.morph=morph;this.time=time;
        frame=new Matrix4f(stack.last().pose());inverse=new Matrix4f(frame).invert();
    }
    public VertexConsumer getBuffer(RenderType type){
        // Names, leash lines and debug geometry are not part of the creature's skin.
        if(type.mode()!=VertexFormat.Mode.QUADS||type.format()!=DefaultVertexFormat.NEW_ENTITY)return target.getBuffer(type);
        return streams.computeIfAbsent(type,Collector::new);
    }
    public void finish(){
        // Collect every body layer before calculating a front: a head/arm may be in a different stream.
        for(var c:streams.values()){
            c.flush();
            for(var v:c.vertices)surface.include(v.p.x,v.p.y,v.p.z);
        }
        int light=0;
        for(var c:streams.values())if(!c.vertices.isEmpty()){light=c.vertices.get(0).light;break;}
        if(morph>=.9999F)for(var c:streams.values())c.vertices.clear();
        else {
            // Keep each material contiguous; alternating skin/coat per polygon flushes the GPU buffer.
            for(var c:streams.values())c.finish(false);
            for(var c:streams.values())c.finish(true);
        }
        envelope(light);
    }
    /** Fills gaps between disconnected limbs as they fuse, so species share one closed handoff surface. */
    private void envelope(int light){
        if(surface.empty()||morph<=.4F)return;
        float size=BiomassSurfaceBounds.ease((morph-.4F)/.6F);
        var out=target.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int ring=0;ring<12;ring++)for(int slice=0;slice<16;slice++){
            boolean vein=(ring*3+slice)%17==0;
            int r=vein?66:24,g=vein?24:29,b=vein?29:31;
            for(int corner=0;corner<4;corner++){
                double latitude=Math.PI*(ring+(corner>=2?1:0))/12;
                double longitude=Math.PI*2*(slice+(corner==1||corner==2?1:0))/16;
                float x=(float)(Math.sin(latitude)*Math.cos(longitude));
                float y=(float)Math.cos(latitude),z=(float)(Math.sin(latitude)*Math.sin(longitude));
                var p=frame.transformPosition(new Vector3f(.30F*x*size,.675F+.675F*y*size,.30F*z*size));
                var normal=frame.transformDirection(new Vector3f(x/.30F,y/.675F,z/.30F)).normalize();
                out.vertex(p.x,p.y,p.z).color(r,g,b,255).uv(.5F,.5F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
    private record V(Vector3f p,int r,int g,int b,int a,float u,float v,int overlay,int light,float nx,float ny,float nz){
        V mix(V o,float t){return new V(new Vector3f(p).lerp(o.p,t),(int)(r+(o.r-r)*t),(int)(g+(o.g-g)*t),(int)(b+(o.b-b)*t),(int)(a+(o.a-a)*t),u+(o.u-u)*t,v+(o.v-v)*t,overlay,light,nx,ny,nz);}
    }
    private void quad(RenderType type,V a,V b,V c,V d,boolean coatedPass){
        var center=new Vector3f(a.p).add(b.p).add(c.p).add(d.p).mul(.25F);
        float noise=(float)(Math.sin(center.x*19+time*.18)*Math.sin(center.z*17+time*.13)*.09);
        boolean coated=coverage>=.999F||coverage>0&&surface.heightFraction(center.y)+noise<coverage*1.2F-.05F;
        if(coated!=coatedPass)return;
        var out=target.getBuffer(coated?RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE):type);
        for(var v:new V[]{a,b,c,d}){
            var p=new Vector3f(v.p);if(morph>0){surface.reshape(p,morph);}
            frame.transformPosition(p);
            int r=v.r,g=v.g,blue=v.b;
            if(coated){boolean vein=Math.floorMod((int)Math.floor(center.x*12)+(int)Math.floor(center.y*9)+(int)Math.floor(center.z*12),13)==0;r=vein?82:24;g=vein?27:29;blue=vein?32:31;}
            out.vertex(p.x,p.y,p.z).color(r,g,blue,v.a).uv(coated?.5F:v.u,coated?.5F:v.v).overlayCoords(v.overlay).uv2(v.light).normal(v.nx,v.ny,v.nz).endVertex();
        }
    }
    private final class Collector implements VertexConsumer {
        final RenderType type;final List<V> vertices=new ArrayList<>();Vector3f p;int r=255,g=255,b=255,a=255,overlay,light;float u,v,nx,ny=1,nz;
        Collector(RenderType type){this.type=type;}
        void flush(){if(p!=null){vertices.add(new V(p,r,g,b,a,u,v,overlay,light,nx,ny,nz));p=null;}}
        void finish(boolean coatedPass){for(int q=0;q+3<vertices.size();q+=4){V a=vertices.get(q),b=vertices.get(q+1),c=vertices.get(q+2),d=vertices.get(q+3);
            int grid=vertices.size()<=256?4:vertices.size()<=1024?2:1;
            for(int x=0;x<grid;x++)for(int y=0;y<grid;y++){float u=x/(float)grid,v=y/(float)grid,un=(x+1)/(float)grid,vn=(y+1)/(float)grid;quad(type,a.mix(b,u).mix(d.mix(c,u),v),a.mix(b,un).mix(d.mix(c,un),v),a.mix(b,un).mix(d.mix(c,un),vn),a.mix(b,u).mix(d.mix(c,u),vn),coatedPass);}}
            if(coatedPass)vertices.clear();
        }
        public VertexConsumer vertex(double x,double y,double z){flush();p=inverse.transformPosition(new Vector3f((float)x,(float)y,(float)z));return this;}
        public VertexConsumer color(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){overlay=u|(v<<16);return this;}
        public VertexConsumer uv2(int u,int v){light=u|(v<<16);return this;}
        public void endVertex(){flush();}
        public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
        public void unsetDefaultColor(){}
        public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
    }
}
