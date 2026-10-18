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
    private final float coverage,morph,height,width;
    private final double time;
    private final Map<RenderType,Collector> streams=new LinkedHashMap<>();
    public BiomassCoatBuffers(LivingEntity mob,PoseStack stack,MultiBufferSource target,float coverage,float morph,double time){
        this.target=target;this.coverage=coverage;this.morph=morph;this.time=time;
        height=mob instanceof net.minecraft.world.entity.player.Player?1.8F:Math.max(.1F,mob.getBbHeight());width=Math.max(.1F,mob.getBbWidth());
        frame=new Matrix4f(stack.last().pose());inverse=new Matrix4f(frame).invert();
    }
    public VertexConsumer getBuffer(RenderType type){return streams.computeIfAbsent(type,Collector::new);}
    public void finish(){for(var c:streams.values())c.finish();}
    private record V(Vector3f p,int r,int g,int b,int a,float u,float v,int overlay,int light,float nx,float ny,float nz){
        V mix(V o,float t){return new V(new Vector3f(p).lerp(o.p,t),(int)(r+(o.r-r)*t),(int)(g+(o.g-g)*t),(int)(b+(o.b-b)*t),(int)(a+(o.a-a)*t),u+(o.u-u)*t,v+(o.v-v)*t,overlay,light,nx,ny,nz);}
    }
    private void quad(RenderType type,V a,V b,V c,V d){
        var center=new Vector3f(a.p).add(b.p).add(c.p).add(d.p).mul(.25F);
        float noise=(float)(Math.sin(center.x*19+time*.18)*Math.sin(center.z*17+time*.13)*.09);
        boolean coated=coverage>=.999F||coverage>0&&center.y/height+noise<coverage*1.2F-.05F;
        var out=target.getBuffer(coated?RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE):type);
        for(var v:new V[]{a,b,c,d}){
            var p=new Vector3f(v.p);if(morph>0){p.x*=1+morph*(.6F/width-1);p.z*=1+morph*(.6F/width-1);p.y*=1+morph*(1.8F/height-1);}
            frame.transformPosition(p);
            int r=v.r,g=v.g,blue=v.b;
            if(coated){boolean vein=Math.floorMod((int)Math.floor(center.x*12)+(int)Math.floor(center.y*9)+(int)Math.floor(center.z*12),13)==0;r=vein?82:24;g=vein?27:29;blue=vein?32:31;}
            out.addVertex(p.x,p.y,p.z).setColor(r,g,blue,v.a).setUv(coated?.5F:v.u,coated?.5F:v.v).setOverlay(v.overlay).setLight(v.light).setNormal(v.nx,v.ny,v.nz);
        }
    }
    private final class Collector implements VertexConsumer {
        final RenderType type;final List<V> vertices=new ArrayList<>();Vector3f p;int r=255,g=255,b=255,a=255,overlay,light;float u,v,nx,ny=1,nz;
        Collector(RenderType type){this.type=type;}
        void flush(){if(p!=null){vertices.add(new V(p,r,g,b,a,u,v,overlay,light,nx,ny,nz));p=null;}}
        void finish(){flush();for(int q=0;q+3<vertices.size();q+=4){V a=vertices.get(q),b=vertices.get(q+1),c=vertices.get(q+2),d=vertices.get(q+3);
            int grid=vertices.size()<=256?4:vertices.size()<=1024?2:1;
            for(int x=0;x<grid;x++)for(int y=0;y<grid;y++){float u=x/(float)grid,v=y/(float)grid,un=(x+1)/(float)grid,vn=(y+1)/(float)grid;quad(type,a.mix(b,u).mix(d.mix(c,u),v),a.mix(b,un).mix(d.mix(c,un),v),a.mix(b,un).mix(d.mix(c,un),vn),a.mix(b,u).mix(d.mix(c,u),vn));}}
            vertices.clear();
        }
        public VertexConsumer addVertex(float x,float y,float z){flush();p=inverse.transformPosition(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){overlay=u|(v<<16);return this;}
        public VertexConsumer setUv2(int u,int v){light=u|(v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
    }
}
