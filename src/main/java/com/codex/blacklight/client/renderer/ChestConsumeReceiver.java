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
    private final RenderType material=ConsumeWeaveMaterial.receiver();
    private final Set<RenderType> skinLayers;
    private final float partial,age,scrollU,scrollV;
    private final Map<RenderType,Collector> layers=new LinkedHashMap<>();
    private final Vector3f low=new Vector3f(Float.POSITIVE_INFINITY),high=new Vector3f(Float.NEGATIVE_INFINITY);
    private int count;
    private ChestConsumeReceiver(LivingEntity mob,PoseStack stack,MultiBufferSource output,float partial,float age){
        this.mob=mob;this.partial=partial;this.output=output;this.age=age;
        var skin=net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob).getTextureLocation(mob);
        skinLayers=Set.of(RenderType.entityCutoutNoCull(skin),RenderType.entityCutout(skin),RenderType.entitySolid(skin),
                RenderType.entityTranslucent(skin),RenderType.entityTranslucentCull(skin));
        inverse=new Matrix4f(stack.last().pose()).invert();
        float seconds=(mob.level().getGameTime()%10000+partial)/20F;
        scrollU=seconds*.002F;scrollV=seconds*.22F;
    }
    public static ChestConsumeReceiver begin(Entity entity,PoseStack stack,MultiBufferSource output,float partial){
        if(!(entity instanceof LivingEntity mob)||mob.isRemoved()||mob.isInvisible())return null;
        var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
        if(!ChestConsumeTendrils.matches(visual)||!com.codex.blacklight.combat.ConsumeSystem.smallDirectReceiver(mob)
                ||mob.level().getEntity(visual.consumerId())==null)return null;
        float age=visual.elapsed(mob.level().getGameTime(),partial)/20F-2F/3F;
        return age>0?new ChestConsumeReceiver(mob,stack,output,partial,age):null;
    }
    @Override public VertexConsumer getBuffer(RenderType type){
        var base=output.getBuffer(type);
        if(!skinLayers.contains(type))return base;
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
        // Follow the posed torso axis even when a villager is lying across the shoulder.
        Vector3f pelvis=bones==null?new Vector3f(center.x,low.y+span*.43F,center.z):
                bones[0].subtract(mob.getPosition(partial)).toVector3f();
        Vector3f up=bones==null?new Vector3f(0,span*.36F,0):bones[1].subtract(bones[0]).toVector3f();
        float spineLength=Math.max(.001F,up.lengthSquared());
        float close=unit((ConsumeFracture.phase(mob,partial).returnFlow()-.86F)/.14F);
        var out=output.getBuffer(material);
        for(var layer:layers.values()){
            int complete=layer.vertices.size()/4*4;
            for(int face=0;face<complete;face+=4){
                float partHeight=0;
                for(int j=0;j<4;j++)partHeight+=.25F*axial(layer.vertices.get(face+j).local,pelvis,up,spineLength);
                if(partHeight<-.22F||partHeight>1.08F)continue;
                for(int j=0;j<4;j++){
                    var vertex=layer.vertices.get(face+j);
                    float distance=center.distance(vertex.local)/span;
                    float axial=axial(vertex.local,pelvis,up,spineLength);
                    float trunk=unit((axial+.32F)/.44F)*unit((1.27F-axial)/.44F);
                    float broken=.55F+.45F*(float)Math.sin(21F*vertex.u+15F*vertex.v+3F*axial);
                    // The source cloth equation makes even small vertex alphas optically dense.
                    // Keep the face and feet readable between the exterior ropes until the final closure.
                    float dye=.015F+trunk*(.026F+.086F*broken);
                    float alpha=strength(age,distance)*(dye+close*close*(.62F-dye))*vertex.alpha;
                    // Identical positions and EQUAL depth inherit every hole cut by the original skin.
                    emit(out,vertex.p,vertex.nx,vertex.ny,vertex.nz,vertex.u,vertex.v,Math.round(alpha),vertex.light);
                }
            }
        }
        layers.clear();count=0;
    }
    static float strength(float seconds,float distance){
        float edge=Math.max(0F,Math.min(1F,(seconds*.5F+.10F-distance)/.13F));
        return edge*edge*(3-2*edge)*Math.min(1F,seconds/.12F);
    }
    private static float unit(float value){return Math.max(0F,Math.min(1F,value));}
    private static float axial(Vector3f p,Vector3f origin,Vector3f up,float squaredLength){
        return ((p.x-origin.x)*up.x+(p.y-origin.y)*up.y+(p.z-origin.z)*up.z)/squaredLength;
    }
    private void emit(VertexConsumer out,Vector3f p,float nx,float ny,float nz,float u,float v,int alpha,int light){
        out.addVertex(p.x,p.y,p.z).setColor(255,255,255,alpha).setUv(u*2.5F+scrollU,v*2.5F+scrollV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx,ny,nz);
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
        public VertexConsumer addVertex(float x,float y,float z){flush();p=new Vector3f(x,y,z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){overlay=u|(v<<16);return this;}
        public VertexConsumer setUv2(int u,int v){light=u|(v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
    }
}
