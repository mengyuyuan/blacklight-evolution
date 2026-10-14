package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Breaks the actual posed player skin into solid chunks, then melts those chunks into liquid. */
public final class BiomassShatterBuffers implements MultiBufferSource {
    private final MultiBufferSource target;
    private final Matrix4f frame,inverse;
    private final float coverage,morph,height,width;
    private final double time;
    private final Map<RenderType,Collector> streams=new LinkedHashMap<>();
    public BiomassShatterBuffers(LivingEntity mob,PoseStack stack,MultiBufferSource target,float coverage,float morph,double time){
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
        double seed=Math.floor(center.x*9)*17+Math.floor(center.y*9)*31+Math.floor(center.z*9)*47;
        float t=coverage,burst=Math.min(1,Math.max(0,(t-.10F)/.40F)),melt=Math.min(1,Math.max(0,(t-.48F)/.52F));
        double angle=seed*2.39996,radial=.16+.40*(.5+.5*Math.sin(seed));
        float cx=(float)(center.x+Math.cos(angle)*radial*burst)*(1-melt*.65F);
        float cz=(float)(center.z+Math.sin(angle)*radial*burst)*(1-melt*.65F);
        float cy=Math.max(.025F,center.y+(float)(Math.sin(burst*Math.PI)*.24)-melt*(center.y+.1F));
        boolean coated=t>.48F;
        var out=target.getBuffer(coated?RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE):type);
        float shrink=Math.max(0,1-Math.max(0,(t-.75F)/.25F));
        var rotation=new org.joml.Quaternionf().rotationXYZ((float)Math.sin(seed)*burst*1.6F,(float)Math.cos(seed)*burst*2,(float)Math.sin(seed+2)*burst);
        // Each original face cell becomes a shallow solid fragment; its skin UV remains attached.
        V[] corners={a,b,c,d};Vector3f[] front=new Vector3f[4],back=new Vector3f[4];
        var normal=new Vector3f(b.p).sub(a.p).cross(new Vector3f(c.p).sub(a.p)).normalize();
        for(int i=0;i<4;i++){
            var q=new Vector3f(corners[i].p).sub(center).mul(shrink).rotate(rotation);q.y*=1-melt*.85F;
            front[i]=new Vector3f(q).add(cx,cy,cz);back[i]=new Vector3f(front[i]).sub(new Vector3f(normal).mul(.085F*shrink));
        }
        int[][] faces={{0,1,2,3},{7,6,5,4},{0,4,5,1},{1,5,6,2},{2,6,7,3},{3,7,4,0}};
        for(var face:faces)for(int i:face){
            V v=corners[i%4];var p=new Vector3f(i<4?front[i]:back[i-4]);p.y=Math.max(.01F,p.y);frame.transformPosition(p);
            int r=coated?32:v.r,g=coated?26:v.g,blue=coated?33:v.b;
            out.vertex(p.x,p.y,p.z).color(r,g,blue,v.a).uv(coated?.5F:v.u,coated?.5F:v.v).overlayCoords(v.overlay).uv2(v.light).normal(v.nx,v.ny,v.nz).endVertex();
        }
    }
    private final class Collector implements VertexConsumer {
        final RenderType type;final List<V> vertices=new ArrayList<>();Vector3f p;int r=255,g=255,b=255,a=255,overlay,light;float u,v,nx,ny=1,nz;
        Collector(RenderType type){this.type=type;}
        void flush(){if(p!=null){vertices.add(new V(p,r,g,b,a,u,v,overlay,light,nx,ny,nz));p=null;}}
        void finish(){flush();for(int q=0;q+3<vertices.size();q+=4){V a=vertices.get(q),b=vertices.get(q+1),c=vertices.get(q+2),d=vertices.get(q+3);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){float u=x/2F,v=y/2F,un=(x+1)/2F,vn=(y+1)/2F;quad(type,a.mix(b,u).mix(d.mix(c,u),v),a.mix(b,un).mix(d.mix(c,un),v),a.mix(b,un).mix(d.mix(c,un),vn),a.mix(b,u).mix(d.mix(c,u),vn));}}
            vertices.clear();
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
