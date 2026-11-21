package com.codex.blacklight.client.renderer;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeReceiverEffect;
import com.codex.blacklight.combat.ConsumeSkinGrowth;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
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

/** Render the paired receiver's FIG material on its final posed skin. */
public final class ChestConsumeReceiver implements MultiBufferSource {
    private final LivingEntity mob;
    private final MultiBufferSource output;
    private final Matrix4f inverse;
    private record Run(ConsumeReceiverEffect.Cue cue,long start,ConsumeSkinGrowth.Playback playback){}
    private static final Map<LivingEntity,Run> RUNS=new WeakHashMap<>();
    private final RenderType material=ConsumeWeaveMaterial.receiver();
    private final Set<RenderType> skinLayers;
    private final float scrollU,scrollV;
    private final ConsumeSkinGrowth.Weights weights;
    private ConsumeSkinPath path;
    private float nextCoverage=Float.NaN;
    private final Map<RenderType,Collector> layers=new LinkedHashMap<>();
    private final Vector3f low=new Vector3f(Float.POSITIVE_INFINITY),high=new Vector3f(Float.NEGATIVE_INFINITY);
    private int count;
    private ChestConsumeReceiver(LivingEntity mob,PoseStack stack,MultiBufferSource output,float partial,ConsumeSkinGrowth.Weights weights){
        this.mob=mob;this.output=output;this.weights=weights;
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
        var cue=effect(visual);
        if(cue==null||mob.level().getEntity(visual.consumerId())==null)return null;
        float age=cue.age(visual.elapsed(mob.level().getGameTime(),partial)/20F);
        if(age<=0)return null;
        var run=RUNS.get(mob);
        if(run==null||run.cue!=cue||run.start!=visual.started()){
            run=new Run(cue,visual.started(),ConsumeSkinPath.playback(cue));
            RUNS.put(mob,run);
        }
        return new ChestConsumeReceiver(mob,stack,output,partial,run.playback.sample(age));
    }
    static ConsumeReceiverEffect.Cue effect(ConsumeVisual visual){
        if(visual.stage()!=ConsumeVisual.CONSUME&&visual.stage()!=ConsumeVisual.MOBILE_CONSUME)return null;
        var forms=PowerForm.values();
        if(visual.form()<0||visual.form()>=forms.length)return null;
        return switch(forms[visual.form()]){
            case NONE,CLAWS,HAMMERFISTS,WHIPFIST,MUSCLE_MASS,BLADE ->
                    ConsumeReceiverEffect.forClip(ConsumeSequence.key(forms[visual.form()],visual.variant()));
            default -> null;
        };
    }
    void bindSkin(ConsumeSkinPath captured){path=captured;}
    VertexConsumer skinBuffer(RenderType type,int region,Vec3 originalVertex){
        var out=getBuffer(type);
        if(path!=null&&skinLayers.contains(type))nextCoverage=path.coverage(weights,region,originalVertex);
        return out;
    }
    @Override public VertexConsumer getBuffer(RenderType type){
        nextCoverage=Float.NaN;
        var base=output.getBuffer(type);
        if(!skinLayers.contains(type))return base;
        var collector=layers.computeIfAbsent(type,key->new Collector());
        if(collector.base!=base){
            collector.base=base;
            collector.pair=VertexMultiConsumer.create(collector,base);
        }
        return collector.pair;
    }
    private record SkinVertex(Vector3f p,Vector3f local,float coverage,float nx,float ny,float nz,float u,float v,int alpha,int light){}
    public void finish(){
        for(var layer:layers.values())layer.flush();
        if(count==0||mob.isRemoved())return;
        float height=Math.max(.01F,high.y-low.y);
        var out=output.getBuffer(material);
        for(var layer:layers.values()){
            int complete=layer.vertices.size()/4*4;
            for(int i=0;i<complete;i++){
                var vertex=layer.vertices.get(i);
                float coverage=Float.isFinite(vertex.coverage)?vertex.coverage:
                        ConsumeSkinPath.unrigged(weights,(vertex.local.y-low.y)/height);
                float alpha=coverage*vertex.alpha;
                // Identical positions and EQUAL depth inherit every hole cut by the original skin.
                emit(out,vertex.p,vertex.nx,vertex.ny,vertex.nz,vertex.u,vertex.v,Math.round(alpha),vertex.light);
            }
        }
        layers.clear();count=0;
    }
    private void emit(VertexConsumer out,Vector3f p,float nx,float ny,float nz,float u,float v,int alpha,int light){
        out.addVertex(p.x,p.y,p.z).setColor(255,255,255,alpha).setUv(u*2.5F+scrollU,v*2.5F+scrollV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx,ny,nz);
    }
    private final class Collector implements VertexConsumer {
        final List<SkinVertex> vertices=new ArrayList<>();
        VertexConsumer base,pair;
        Vector3f p;int r,g,b,a=255,overlay,light;float u,v,nx,ny=1,nz,coverage;
        void flush(){
            if(p==null)return;
            Vector3f local=inverse.transformPosition(p,new Vector3f());
            low.min(local);high.max(local);count++;
            vertices.add(new SkinVertex(p,local,coverage,nx,ny,nz,u,v,a,light));p=null;
        }
        public VertexConsumer addVertex(float x,float y,float z){flush();p=new Vector3f(x,y,z);coverage=nextCoverage;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){overlay=u|(v<<16);return this;}
        public VertexConsumer setUv2(int u,int v){light=u|(v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
    }
}
