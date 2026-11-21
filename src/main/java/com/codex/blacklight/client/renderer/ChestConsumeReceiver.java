package com.codex.blacklight.client.renderer;

import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** The normal receiver's Spine_2 Forward EffectConsume, on its final skin after posing and physics. */
public final class ChestConsumeReceiver implements MultiBufferSource {
    private final LivingEntity mob;
    private final MultiBufferSource output;
    private final Matrix4f inverse;
    private final RenderType material=ConsumeWeaveMaterial.mesh();
    private final float partial,age,scrollU,scrollV;
    private final Map<RenderType,Collector> layers=new LinkedHashMap<>();
    private final Vector3f low=new Vector3f(Float.POSITIVE_INFINITY),high=new Vector3f(Float.NEGATIVE_INFINITY);
    private int count;
    private ChestConsumeReceiver(LivingEntity mob,PoseStack stack,MultiBufferSource output,float partial,float age){
        this.mob=mob;this.partial=partial;this.output=output;this.age=age;
        inverse=new Matrix4f(stack.last().pose()).invert();
        float seconds=(mob.level().getGameTime()%10000+partial)/20F;
        scrollU=seconds*.002F;scrollV=seconds*.22F;
    }
    public static ChestConsumeReceiver begin(Entity entity,PoseStack stack,MultiBufferSource output,float partial){
        if(!(entity instanceof LivingEntity mob)||mob.isRemoved()||mob.isInvisible())return null;
        var visual=ModAttachments.getData(mob,ModAttachments.CONSUME_VISUAL);
        if(!ChestConsumeTendrils.matches(visual)||mob.level().getEntity(visual.consumerId())==null)return null;
        float age=visual.elapsed(mob.level().getGameTime(),partial)/20F-2F/3F;
        return age>0?new ChestConsumeReceiver(mob,stack,output,partial,age):null;
    }
    @Override public VertexConsumer getBuffer(RenderType type){
        var base=output.getBuffer(type);
        if(type.mode()!=VertexFormat.Mode.QUADS||type.format()!=DefaultVertexFormat.NEW_ENTITY
                ||type==RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE)
                ||type==material)return base;
        var collector=layers.computeIfAbsent(type,key->new Collector());
        if(collector.base!=base){
            collector.base=base;
            collector.pair=VertexMultiConsumer.create(collector,base);
        }
        return collector.pair;
    }
    private record SkinVertex(Vector3f p,Vector3f local,float nx,float ny,float nz,float u,float v,int alpha,int light){}
    public void finish(){
        for(var layer:layers.values())layer.flush();
        if(count==0||mob.isRemoved())return;
        // Poses can translate the receiver a long way from its vanilla bounding box.
        Vec3[] bones=AdaptiveRagdoll.chestConsumeJoints(mob,partial);
        Vector3f center=bones==null?new Vector3f((low.x+high.x)*.5F,low.y+(high.y-low.y)*.64F,(low.z+high.z)*.5F):
                bones[3].subtract(mob.getPosition(partial)).toVector3f();
        float span=Math.max(.15F,Math.max(high.y-low.y,Math.max(high.z-low.z,high.x-low.x)));
        var out=output.getBuffer(material);
        for(var layer:layers.values()){
            int complete=layer.vertices.size()/4*4;
            for(int i=0;i<complete;i++){
                var vertex=layer.vertices.get(i);
                float distance=center.distance(vertex.local)/span;
                float alpha=strength(age,distance)*vertex.alpha;
                Vector3f p=new Vector3f(vertex.p).add(vertex.nx*.0015F,vertex.ny*.0015F,vertex.nz*.0015F);
                emit(out,p,vertex.nx,vertex.ny,vertex.nz,vertex.u,vertex.v,Math.round(alpha),vertex.light);
            }
        }
        layers.clear();count=0;
    }
    static float strength(float seconds,float distance){
        float edge=Math.max(0F,Math.min(1F,(seconds*.5F+.10F-distance)/.13F));
        return edge*edge*(3-2*edge)*Math.min(1F,seconds/.12F);
    }
    private void emit(VertexConsumer out,Vector3f p,float nx,float ny,float nz,float u,float v,int alpha,int light){
        out.vertex(p.x,p.y,p.z).color(255,255,255,alpha).uv(u*2.5F+scrollU,v*2.5F+scrollV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx,ny,nz).endVertex();
    }
    private final class Collector implements VertexConsumer {
        final List<SkinVertex> vertices=new ArrayList<>();
        VertexConsumer base,pair;
        Vector3f p;int r,g,b,a=255,overlay,light;float u,v,nx,ny=1,nz;
        void flush(){
            if(p==null)return;
            Vector3f local=inverse.transformPosition(p,new Vector3f());
            low.min(local);high.max(local);count++;
            vertices.add(new SkinVertex(p,local,nx,ny,nz,u,v,a,light));p=null;
        }
        public VertexConsumer vertex(double x,double y,double z){flush();p=new Vector3f((float)x,(float)y,(float)z);return this;}
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
