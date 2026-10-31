package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.HunterConsume;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Anatomical separation of the inherited pose; cut pieces no longer animate as a live receiver.
 * Source split markers and Minecraft fragment return timings have separate clocks. */
public final class ConsumeFracture implements MultiBufferSource {
    public enum Style { HEAD_CRUSH, RIP, SEVER, CRUSH, COIL, HEAD_IMPACT, LASH, ARM_RIP, CHEST_ABSORB }
    public record Phase(PowerForm form,float progress,float opening,float returnFlow,float contact,Style style,boolean nativeEvents) {
        public Phase(PowerForm form,float progress,float opening,float returnFlow,float contact,Style style){
            this(form,progress,opening,returnFlow,contact,style,false);
        }
    }
    private final MultiBufferSource target;
    private final Matrix4f frame,inverse;
    private final Vector3f sink;
    private final Phase phase;
    private final float spread;
    private final int divisions;
    private final int seed,light;
    private final boolean headOnly;
    private final boolean bisect;
    private final boolean sourcedReceiver;
    private final Vector3f cutNormal;
    private final Vector3f cutCenter=new Vector3f();
    private final Vector3f up,side,spinAxis,ground;
    private final float fragmentAge;
    private final Vector3f[] pieceCenter={new Vector3f(),new Vector3f()};
    private final Vector3f[] pieceLow={new Vector3f(),new Vector3f()},pieceHigh={new Vector3f(),new Vector3f()};
    private final float[] pieceFloor=new float[2];
    private final org.joml.Quaternionf[] pieceSpin={new org.joml.Quaternionf(),new org.joml.Quaternionf()};
    private final Map<RenderType,Collector> streams=new LinkedHashMap<>();
    private final Map<ModelPart,Integer> modelParts=new IdentityHashMap<>();
    private final Vector3f[] centers=new Vector3f[ConsumeAnatomy.COUNT];
    private final float[] radii=new float[ConsumeAnatomy.COUNT];
    private final boolean[] present=new boolean[ConsumeAnatomy.COUNT];
    private record Edge(Vector3f a,Vector3f b) {}

    public static Phase phase(LivingEntity mob,float partial) {
        var v=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
        var form=PowerForm.values()[net.minecraft.util.Mth.clamp(v.form(),0,PowerForm.values().length-1)];
        boolean large=ConsumeSequence.mountedStage(v.stage());
        Style style=switch(form){case CLAWS->Style.HEAD_CRUSH;case MUSCLE_MASS->Style.RIP;case BLADE->Style.SEVER;case HAMMERFISTS->Style.CRUSH;case WHIPFIST->Style.COIL;default->large?Style.RIP:Style.HEAD_CRUSH;};
        if(!v.consuming())return new Phase(form,0,0,0,1,style);
        if(v.stage()==ConsumeVisual.HUNTER_CONSUME) {
            float seconds=HunterConsume.seconds(v,mob.level().getGameTime(),partial);
            float begin=HunterConsume.EFFECT_BEGIN_SECONDS,reverse=HunterConsume.EFFECT_REVERSE_SECONDS,end=HunterConsume.COMPLETE_SECONDS;
            // Source supplies Head/start/reverse/consume events. Mesh shrink is our shader substitute.
            return new Phase(form,seconds/end,smooth((seconds-begin)/(reverse-begin)),
                    smooth((seconds-reverse)/(end-reverse)),begin/end,Style.HEAD_CRUSH);
        }
        var scene=com.codex.blacklight.combat.ConsumeReceiverScene.profile(form,large,v.variant());
        if(scene!=null){
            float seconds=(mob.level().getGameTime()-v.started()+partial)/20F;
            // Some source branches begin the consume shader before spawnGib.
            // Never pull the complete body away before its actual separation.
            float flowStart=Math.max(scene.effectSeconds(),scene.deformSeconds())+scene.settleSeconds();
            return new Phase(form,com.codex.blacklight.forge.WingMath.clamp(seconds/scene.finishSeconds(),0,1),
                    smooth((seconds-scene.deformSeconds())/scene.settleSeconds()),
                    smooth((seconds-flowStart)/(scene.finishSeconds()-flowStart)),
                    (scene.style().equals("SEVER")?scene.deformSeconds():scene.effectSeconds())/scene.finishSeconds(),Style.valueOf(scene.style()),true);
        }
        float t=v.motionProgress(mob.level().getGameTime(),partial),hit=ConsumeSequence.contact(form,large);
        // prototype_consume / blades / receiver: two spawnGib records and
        // visibility switch at 0.833333s. Geometry/trajectory below adapt this to MC.
        if(form==PowerForm.BLADE&&!large)hit=.833333F/Math.max(.05F,(v.duration()-1)/20F);
        return new Phase(form,t,smooth((t-hit)/.08F),smooth((t-(ConsumeSequence.completion(form,large)-.125F))/.125F),hit,style);
    }
    private static float smooth(float t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    public static float cutHeight(PowerForm form){return form==PowerForm.NONE?.8F:.6F;}
    public static boolean visible(LivingEntity mob,float partial){
        var v=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);if(!v.consuming())return false;
        var p=phase(mob,partial);
        var scene=com.codex.blacklight.combat.ConsumeReceiverScene.profile(p.form(),ConsumeSequence.mountedStage(v.stage()),v.variant());
        return scene!=null?(mob.level().getGameTime()-v.started()+partial)/20F>=Math.min(scene.deformSeconds(),scene.effectSeconds()):p.progress()>=Math.max(0,p.contact()-.08F);
    }
    public void bindModel(EntityModel<?> model){modelParts.putAll(ConsumeAnatomy.model(model));}
    public ConsumeFracture(LivingEntity mob,float partial,PoseStack stack,MultiBufferSource target,Vec3 destination) {
        this.target=target;phase=phase(mob,partial);seed=mob.getId();
        headOnly=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.HUNTER_CONSUME;
        sourcedReceiver=phase.nativeEvents();
        bisect=phase.style()==Style.SEVER&&!ConsumeSequence.mountedStage(com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL).stage());
        cutNormal=ConsumeSequence.quadruped(mob)?new Vector3f(0,0,1):new Vector3f(0,1,0);
        light=net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(mob,partial);
        frame=new Matrix4f(stack.last().pose()).mul(ConsumeVictimPose.effectTransform(mob,partial));
        inverse=new Matrix4f(frame).invert();
        var visual=com.codex.blacklight.registry.ModAttachments.getData(mob, ModAttachments.CONSUME_VISUAL);
        if(bisect&&!ConsumeSequence.quadruped(mob)){
            var projected=com.codex.blacklight.combat.ConsumePlacementTrack.projection(
                    com.codex.blacklight.combat.GrappleMotion.sceneKey(mob,visual),
                    com.codex.blacklight.combat.GrappleMotion.receiverProgress(mob,visual,partial)).orElse(null);
            if(projected!=null){
                var chest=com.codex.blacklight.combat.ConsumePlacementTrack.quaternion(projected,11);
                cutNormal.set(com.codex.blacklight.combat.CorpseFragmentMotion.torsoNormal(chest));
            }
        }
        float split=ConsumeSequence.splitSeconds(phase.form(),ConsumeSequence.mountedStage(visual.stage()),visual.variant());
        fragmentAge=Float.isFinite(split)?Math.max(0,(mob.level().getGameTime()-visual.started()+partial)/20F-split):0;
        up=inverse.transformDirection(stack.last().pose().transformDirection(new Vector3f(0,1,0))).normalize();
        side=inverse.transformDirection(stack.last().pose().transformDirection(new Vector3f(1,0,0))).normalize();
        spinAxis=inverse.transformDirection(stack.last().pose().transformDirection(new Vector3f(0,0,1))).normalize();
        float floor=(float)(visual.origin().y-mob.getPosition(partial).y-com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial).y);
        ground=inverse.transformPosition(stack.last().pose().transformPosition(new Vector3f(0,floor,0)));
        sink=inverse.transformPosition(stack.last().pose().transformPosition(new Vector3f((float)destination.x,(float)destination.y,(float)destination.z)));
        spread=net.minecraft.util.Mth.clamp(mob.getBbWidth()*.3F,.18F,.65F);
        divisions=bisect?2:phase.style()==Style.CRUSH?4:1;
        for(int i=0;i<centers.length;i++)centers[i]=new Vector3f(0,mob.getBbHeight()*.5F,0);
    }
    @Override public VertexConsumer getBuffer(RenderType type) {
        if(type.format()!=DefaultVertexFormat.NEW_ENTITY ||
                (type.mode()!=VertexFormat.Mode.QUADS&&type.mode()!=VertexFormat.Mode.TRIANGLES))return target.getBuffer(type);
        return streams.computeIfAbsent(type,Collector::new);
    }
    public void finish() {
        // One set of posed part pivots for skin, clothing and copied layers.
        Vector3f[] lo=new Vector3f[centers.length],hi=new Vector3f[centers.length];
        for(int i=0;i<lo.length;i++){lo[i]=new Vector3f(Float.MAX_VALUE);hi[i]=new Vector3f(-Float.MAX_VALUE);}
        for(var c:streams.values()){
            c.flush();for(var v:c.vertices)for(int a=0;a<centers.length;a++)if(v.weights[a]>.15F){lo[a].min(v.p);hi[a].max(v.p);present[a]=true;}
        }
        for(int a=0;a<centers.length;a++)if(present[a]){centers[a].set(lo[a]).add(hi[a]).mul(.5F);radii[a]=net.minecraft.util.Mth.clamp(lo[a].distance(hi[a])*.25F,.05F,.55F);}
        cutCenter.set(centers[ConsumeAnatomy.BODY]);
        if(bisect)preparePieces();
        for(var entry:streams.entrySet()){
            Collector c=entry.getValue();VertexConsumer out=target.getBuffer(entry.getKey());int count=c.quads?4:3;
            List<List<Edge>> caps=new ArrayList<>();for(int i=0;i<centers.length*divisions;i++)caps.add(new ArrayList<>());
            for(int i=0;i+count<=c.vertices.size();i+=count){
                List<V> polygon=c.vertices.subList(i,i+count);
                for(int a=0;a<centers.length;a++){
                    final int anatomy=a;
                    if(polygon.stream().noneMatch(v->v.weights[anatomy]>.00001F))continue;
                    List<V> anatomical=polygon;
                    for(int rival=0;rival<centers.length&&!anatomical.isEmpty();rival++)if(rival!=a){final int other=rival;anatomical=clip(anatomical,v->v.weights[anatomy]-v.weights[other]);}
                    for(int d=0;d<divisions;d++){
                        int piece=a*divisions+d;List<V> cut=anatomical;
                        int sx=(d&1)==0?-1:1,sz=(d&2)==0?-1:1;
                        if(bisect)cut=clip(cut,v->new Vector3f(v.p).sub(cutCenter).dot(cutNormal)*sx);
                        else if(divisions>1){cut=clip(cut,v->(v.p.x-centers[anatomy].x)*sx);cut=clip(cut,v->(v.p.z-centers[anatomy].z)*sz);}
                        if(cut.size()<3)continue;
                        for(int e=0;e<cut.size();e++){
                            V va=cut.get(e),vb=cut.get((e+1)%cut.size());if(va.p.distanceSquared(vb.p)<1e-8F)continue;
                            boolean seam=bisect?Math.abs(new Vector3f(va.p).sub(cutCenter).dot(cutNormal))<.0001F&&Math.abs(new Vector3f(vb.p).sub(cutCenter).dot(cutNormal))<.0001F:
                                    divisions>1&&((Math.abs(va.p.x-centers[a].x)<.0001F&&Math.abs(vb.p.x-centers[a].x)<.0001F)||(Math.abs(va.p.z-centers[a].z)<.0001F&&Math.abs(vb.p.z-centers[a].z)<.0001F));
                            for(int rival=0;rival<centers.length&&!seam;rival++)if(rival!=a&&va.weights[a]>.001F&&vb.weights[a]>.001F)
                                seam=Math.abs(va.weights[a]-va.weights[rival])<.0001F&&Math.abs(vb.weights[a]-vb.weights[rival])<.0001F;
                            if(seam)caps.get(piece).add(new Edge(va.p,vb.p));
                        }
                        for(int j=1;j<cut.size()-1;j++){
                            emit(out,cut.get(0),piece);emit(out,cut.get(j),piece);emit(out,cut.get(j+1),piece);if(c.quads)emit(out,cut.get(j+1),piece);
                        }
                    }
                }
            }
            VertexConsumer inner=target.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            for(int i=0;i<caps.size();i++)cap(inner,caps.get(i),i,new Vector3f(0,1,0));
        }
        tissueBridges();absorption();streams.clear();
    }
    private static List<V> clip(List<V> input,ToDoubleFunction<V> plane){
        List<V> result=new ArrayList<>();if(input.isEmpty())return result;
        V a=input.get(input.size()-1);double da=plane.applyAsDouble(a);
        for(V b:input){double db=plane.applyAsDouble(b);if((da>=0)!=(db>=0))result.add(a.mix(b,(float)(da/(da-db))));if(db>=0)result.add(b);a=b;da=db;}
        return result;
    }
    private float start(int part){
        if(headOnly)return part==ConsumeAnatomy.HEAD?phase.contact():Float.POSITIVE_INFINITY;
        float hit=phase.contact();
        return switch(phase.style()){
            case HEAD_CRUSH,HEAD_IMPACT->part==ConsumeAnatomy.HEAD?hit:.85F;
            case RIP->part==ConsumeAnatomy.RIGHT_ARM?hit:part==ConsumeAnatomy.LEFT_ARM?Math.min(.79F,hit+.07F):.85F;
            case SEVER->part>=2&&part<=5?Math.min(.81F,hit+(part-2)*.085F):part==1?Math.min(.83F,hit+.34F):.86F;
            case CRUSH->Math.min(.83F,hit+part*.008F);
            case COIL,LASH,ARM_RIP,CHEST_ABSORB->hit;
        };
    }
    private float opening(int part){return smooth((phase.progress()-start(part))/.08F);}
    private org.joml.Quaternionf pieceRotation(int piece){
        return pieceSpin[piece];
    }
    private void preparePieces(){
        for(int d=0;d<2;d++){pieceLow[d].set(cutCenter);pieceHigh[d].set(cutCenter);}
        for(var stream:streams.values())for(var v:stream.vertices){int d=new Vector3f(v.p).sub(cutCenter).dot(cutNormal)<0?0:1;pieceLow[d].min(v.p);pieceHigh[d].max(v.p);}
        for(int d=0;d<2;d++){
            pieceSpin[d].rotationAxis((d==0?-1:1)*Math.min(.55F,fragmentAge)*.8F,spinAxis.x,spinAxis.y,spinAxis.z);
            pieceCenter[d].set(pieceLow[d]).add(pieceHigh[d]).mul(.5F);var rotation=pieceRotation(d);float lowest=Float.POSITIVE_INFINITY;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                var p=new Vector3f(x==0?pieceLow[d].x:pieceHigh[d].x,y==0?pieceLow[d].y:pieceHigh[d].y,z==0?pieceLow[d].z:pieceHigh[d].z);
                p.sub(pieceCenter[d]).rotate(rotation).add(pieceCenter[d]).sub(ground);lowest=Math.min(lowest,p.dot(up));
            }
            pieceFloor[d]=lowest;
        }
    }
    private Vector3f displaced(Vector3f p,int piece){
        int part=piece/divisions,d=piece%divisions;Vector3f pivot=centers[part];
        if(bisect){
            // Two coherent halves: attached head/limbs share their half's transform.
            float sign=d==0?-1:1,open=phase.opening(),flow=phase.returnFlow();
            if(open<=0)return frame.transformPosition(new Vector3f(p));
            float shrink=com.codex.blacklight.combat.CorpseFragmentMotion.shrink(flow),travel=smooth((flow-.30F)/.70F);
            Vector3f pivotPiece=pieceCenter[d];
            Vector3f offset=new Vector3f(side).mul(sign*(spread*.65F+Math.min(fragmentAge,.6F)*.12F)*open)
                    .fma(com.codex.blacklight.combat.CorpseFragmentMotion.drop(fragmentAge,pieceFloor[d])*open,up);
            Vector3f result=new Vector3f(p).sub(pivotPiece).rotate(pieceRotation(d))
                    .mul(1-shrink*.997F).add(pivotPiece).fma(1-travel,offset);
            return frame.transformPosition(result.add(new Vector3f(sink).sub(pivotPiece).mul(travel)));
        }
        if(headOnly) {
            if(part!=ConsumeAnatomy.HEAD)return frame.transformPosition(new Vector3f(p));
            float flow=phase.returnFlow();
            return frame.transformPosition(new Vector3f(p).sub(pivot).mul(1-flow*.999F)
                    .add(pivot).add(new Vector3f(sink).sub(pivot).mul(flow)));
        }
        float open=sourcedReceiver?phase.opening():opening(part),flow=phase.returnFlow();
        if(sourcedReceiver&&(phase.style()==Style.HEAD_CRUSH||phase.style()==Style.HEAD_IMPACT))
            flow=part==ConsumeAnatomy.HEAD?smooth(flow/.7F):smooth((flow-.16F)/.84F);
        float shrink=smooth(flow/.6F),travel=smooth((flow-.4F)/.6F);
        if(phase.style()==Style.LASH||phase.style()==Style.CHEST_ABSORB){
            // Lash has no spawnGib event. Keep the receiver connected as it
            // contracts into the absorption stream; do not spin every limb apart.
            Vector3f anchor=phase.style()==Style.LASH&&present[ConsumeAnatomy.HEAD]?centers[ConsumeAnatomy.HEAD]:centers[ConsumeAnatomy.BODY];
            Vector3f local=new Vector3f(p).sub(anchor);
            if(phase.style()==Style.CHEST_ABSORB&&part==ConsumeAnatomy.BODY)local.z*=1-.20F*open;
            return frame.transformPosition(local.mul(1-shrink*.997F)
                    .add(anchor).add(new Vector3f(sink).sub(anchor).mul(travel)));
        }
        if(phase.style()==Style.ARM_RIP){
            // Source creates a right-arm gib and a remainder gib. The other arm,
            // legs and head stay attached to that remainder instead of exploding.
            boolean arm=part==ConsumeAnatomy.RIGHT_ARM;
            Vector3f anchor=arm?centers[ConsumeAnatomy.RIGHT_ARM]:centers[ConsumeAnatomy.BODY];
            Vector3f local=new Vector3f(p).sub(anchor),offset=new Vector3f();
            if(arm){
                offset.set(centers[ConsumeAnatomy.RIGHT_ARM]).sub(centers[ConsumeAnatomy.BODY]);
                if(offset.lengthSquared()<.001F)offset.set(-1,0,0);else offset.normalize();
                offset.mul(spread*1.8F*open);local.rotateZ(open*.45F);
            }
            return frame.transformPosition(local.mul(1-shrink*.997F).add(anchor).fma(1-travel,offset)
                    .add(new Vector3f(sink).sub(anchor).mul(travel)));
        }
        Vector3f radial=new Vector3f(pivot).sub(centers[0]);
        if(radial.lengthSquared()<.001F)radial.set(0,1,0);else radial.normalize();
        Vector3f offset=new Vector3f(),local=new Vector3f(p).sub(pivot);
        switch(phase.style()){
            case RIP->{if(part==2||part==3){offset.set(radial).mul(spread*open*1.8F);local.rotateZ((part==2?1:-1)*open*.3F);}else offset.set(radial).mul(spread*.25F*open);}
            case SEVER->{if(part!=0){offset.set(radial).mul(spread*open);offset.y-=spread*.65F*open*open;local.rotateZ((part%2==0?1:-1)*open*.65F);} }
            case HEAD_CRUSH->{if(part==1){local.mul(1-open*.72F,1-open*.48F,1-open*.72F);offset.y-=spread*.25F*open;} }
            case HEAD_IMPACT->{if(part==ConsumeAnatomy.HEAD){local.mul(1+open*.12F,1-open*.82F,1+open*.12F);offset.y-=spread*.18F*open;} }
            case CRUSH->{offset.set((d&1)==0?-1:1,.2F,(d&2)==0?-1:1).mul(spread*.65F*open);local.y*=1-open*.7F;local.rotateY((d-1.5F)*open*.5F);offset.y-=spread*.45F*open;}
            case COIL->{float angle=open*(part%2==0?1:-1)*1.6F;local.rotateY(angle).mul(1-open*.55F,1-open*.22F,1-open*.55F);offset.set(radial).rotateY(angle).mul(spread*.4F*open);}
            case LASH,ARM_RIP,CHEST_ABSORB->{}
        }
        Vector3f result=local.mul(1-shrink*.997F).add(pivot).fma(1-travel,offset);
        result.add(new Vector3f(sink).sub(pivot).mul(travel));
        return frame.transformPosition(result);
    }
    private void emit(VertexConsumer out,V v,int piece){
        Vector3f p=displaced(v.p,piece);float dark=headOnly&&piece/divisions!=ConsumeAnatomy.HEAD?0:phase.returnFlow();
        out.vertex(p.x,p.y,p.z).color((int)(v.r*(1-dark)+66*dark),(int)(v.g*(1-dark)+12*dark),(int)(v.b*(1-dark)+19*dark),v.a).uv(v.u,v.v).overlayCoords(v.overlay).uv2(v.light).normal(v.nx,v.ny,v.nz).endVertex();
    }
    private void tissueBridges(){
        if(headOnly||bisect)return;
        if(phase.style()!=Style.RIP&&phase.style()!=Style.SEVER&&phase.style()!=Style.ARM_RIP)return;
        var out=target.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));var stack=new PoseStack();
        for(int a=1;a<centers.length;a++){
            if(phase.style()==Style.ARM_RIP&&a!=ConsumeAnatomy.RIGHT_ARM)continue;
            if(!present[a])continue;float open=sourcedReceiver?phase.opening():opening(a),fade=1-smooth((phase.progress()-start(a)-.13F)/.12F);
            if(open<=0||fade<=0)continue;
            Vector3f base=displaced(new Vector3f(centers[0]).lerp(centers[a],.6F),0),end=displaced(new Vector3f(centers[a]),a*divisions);
            for(int strand=0;strand<3;strand++){
                double side=(strand-1)*.025;
                Vec3 p=new Vec3(base.x+side,base.y,base.z),q=new Vec3(end.x+side,end.y,end.z);
                ConsumedMobVisuals.tube(stack,out,0x00900090,p,q,.018*fade,0xff3b181d);
                ConsumedMobVisuals.tube(stack,out,0x00900090,p,q,.006*fade,0xffa23b40);
            }
        }
    }
    private void absorption(){
        List<ConsumeTendrils.Attachment> roots=new ArrayList<>();
        if(bisect)for(int d=0;d<2;d++){
            var p=displaced(new Vector3f(cutCenter).fma(d==0?-.12F:.12F,cutNormal),d);
            roots.add(new ConsumeTendrils.Attachment(new Vec3(p.x,p.y,p.z),Math.max(.1F,radii[0])*(1-phase.returnFlow())));
        }
        else for(int a=0;a<centers.length;a++)if(present[a]&&(!headOnly||a==ConsumeAnatomy.HEAD)){
            if(phase.style()==Style.ARM_RIP&&a!=ConsumeAnatomy.RIGHT_ARM&&a!=ConsumeAnatomy.BODY)continue;
            if(phase.style()==Style.CHEST_ABSORB&&a!=ConsumeAnatomy.BODY)continue;
            var p=displaced(new Vector3f(centers[a]),a*divisions);roots.add(new ConsumeTendrils.Attachment(new Vec3(p.x,p.y,p.z),radii[a]*(1-smooth(phase.returnFlow()/.6F)*.997F)));
        }
        Vector3f end=frame.transformPosition(new Vector3f(sink));
        // Effects are not combat-target silhouettes, even for externally glowing mobs.
        var out=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        ConsumeAbsorptionFx.drawPosed(new PoseStack(),out,light,roots,new Vec3(end.x,end.y,end.z),phase,seed);
    }
    private static Vector3f loopNormal(List<Vector3f> loop){
        Vector3f n=new Vector3f();for(int i=0;i<loop.size();i++){Vector3f a=loop.get(i),b=loop.get((i+1)%loop.size());n.add((a.y-b.y)*(a.z+b.z),(a.z-b.z)*(a.x+b.x),(a.x-b.x)*(a.y+b.y));}
        return n.lengthSquared()<1e-10F?new Vector3f(0,1,0):n.normalize();
    }
    /** Stitch actual boundary edges, never the convex hull of unrelated limbs. */
    private void cap(VertexConsumer out,List<Edge> edges,int part,Vector3f n) {
        var pending=new ArrayList<Edge>();
        for(Edge e:edges){
            boolean duplicate=false;
            for(Edge old:pending)if((same(e.a,old.a)&&same(e.b,old.b))||(same(e.a,old.b)&&same(e.b,old.a))){duplicate=true;break;}
            if(!duplicate)pending.add(e);
        }
        while(!pending.isEmpty()){
            Edge first=pending.remove(pending.size()-1);
            var loop=new ArrayList<Vector3f>();loop.add(first.a);loop.add(first.b);
            Vector3f end=first.b;
            while(!same(end,first.a)){
                int found=-1;Vector3f next=null;
                for(int i=0;i<pending.size();i++){
                    Edge e=pending.get(i);
                    if(same(end,e.a)){found=i;next=e.b;break;}
                    if(same(end,e.b)){found=i;next=e.a;break;}
                }
                if(found<0)break;
                pending.remove(found);end=next;loop.add(end);
            }
            // Open/non-manifold cuts get no invented bridging surface.
            if(!same(end,first.a)||loop.size()<4)continue;
            loop.remove(loop.size()-1);triangulate(out,loop,part,loopNormal(loop));
        }
    }
    private static boolean same(Vector3f a,Vector3f b){return a.distanceSquared(b)<.00000004F;}
    private void triangulate(VertexConsumer out,List<Vector3f> loop,int part,Vector3f n){
        Vector3f u=new Vector3f(n).cross(Math.abs(n.z)>.9F?new Vector3f(0,1,0):new Vector3f(0,0,1)).normalize(),v=new Vector3f(n).cross(u).normalize();
        for(int i=loop.size()-1;i>=0&&loop.size()>3;i--){
            int size=loop.size();if(Math.abs(turn(loop.get((i+size-1)%size),loop.get(i),loop.get((i+1)%size),u,v))<.0000001F)loop.remove(i);
        }
        float area=0;for(int i=0;i<loop.size();i++){Vector3f a=loop.get(i),b=loop.get((i+1)%loop.size());area+=a.dot(u)*b.dot(v)-b.dot(u)*a.dot(v);}
        if(Math.abs(area)<.0000001F)return;float winding=Math.signum(area);
        while(loop.size()>=3){
            boolean found=false;
            for(int i=0;i<loop.size();i++){
                int size=loop.size();Vector3f a=loop.get((i+size-1)%size),b=loop.get(i),c=loop.get((i+1)%size);
                if(turn(a,b,c,u,v)*winding<=.0000001F)continue;
                boolean inside=false;
                for(Vector3f p:loop){if(p==a||p==b||p==c)continue;
                    if(turn(a,b,p,u,v)*winding>=0&&turn(b,c,p,u,v)*winding>=0&&turn(c,a,p,u,v)*winding>=0){inside=true;break;}}
                if(inside)continue;
                capVertex(out,a,part,0xff341a20);capVertex(out,b,part,0xff6b242b);capVertex(out,c,part,0xff341a20);capVertex(out,c,part,0xff341a20);
                loop.remove(i);found=true;break;
            }
            if(!found)break;
        }
    }
    private static float turn(Vector3f a,Vector3f b,Vector3f c,Vector3f u,Vector3f v){
        float x=b.dot(u)-a.dot(u),y=b.dot(v)-a.dot(v);return x*(c.dot(v)-a.dot(v))-y*(c.dot(u)-a.dot(u));
    }
    private void capVertex(VertexConsumer out,Vector3f source,int part,int color){
        Vector3f p=displaced(source,part);
        out.vertex(p.x,p.y,p.z).color(color).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00c000c0).normal(0,1,0).endVertex();
    }
    private static final class V {
        Vector3f p;float[] weights;int r=255,g=255,b=255,a=255,overlay=OverlayTexture.NO_OVERLAY,light;float u,v,nx,ny=1,nz;
        V mix(V b,float t){V c=new V();c.p=new Vector3f(p).lerp(b.p,t);c.weights=new float[ConsumeAnatomy.COUNT];for(int i=0;i<c.weights.length;i++)c.weights[i]=weights[i]+(b.weights[i]-weights[i])*t;c.r=(int)(r+(b.r-r)*t);c.g=(int)(g+(b.g-g)*t);c.b=(int)(this.b+(b.b-this.b)*t);c.a=(int)(a+(b.a-a)*t);c.u=u+(b.u-u)*t;c.v=v+(b.v-v)*t;c.overlay=overlay;c.light=light;c.nx=nx+(b.nx-nx)*t;c.ny=ny+(b.ny-ny)*t;c.nz=nz+(b.nz-nz)*t;return c;}
    }
    private final class Collector implements VertexConsumer,ConsumeAnatomy.Consumer {
        final boolean quads;final List<V> vertices=new ArrayList<>();V current;float[] weights={1,0,0,0,0,0,0};
        Collector(RenderType type){quads=type.mode()==VertexFormat.Mode.QUADS;}
        @Override public void anatomy(int part){weights=new float[ConsumeAnatomy.COUNT];weights[net.minecraft.util.Mth.clamp(part,0,weights.length-1)]=1;}
        @Override public void anatomy(ModelPart part){anatomy(modelParts.getOrDefault(part,0));}
        @Override public void anatomy(float[] values){weights=values.clone();float sum=0;for(float value:weights)sum+=value;if(sum<.001F)anatomy(0);else for(int i=0;i<weights.length;i++)weights[i]/=sum;}
        void flush(){if(current!=null){vertices.add(current);current=null;}}
        @Override public void endVertex(){flush();}
        @Override public void defaultColor(int r,int g,int b,int a){}
        @Override public void unsetDefaultColor(){}
        @Override public VertexConsumer vertex(double x,double y,double z){flush();current=new V();current.p=inverse.transformPosition(new Vector3f((float)x,(float)y,(float)z));current.weights=weights;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){current.r=r;current.g=g;current.b=b;current.a=a;return this;}
        @Override public VertexConsumer uv(float u,float v){current.u=u;current.v=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){current.overlay=u|(v<<16);return this;}
        @Override public VertexConsumer uv2(int u,int v){current.light=u|(v<<16);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){current.nx=x;current.ny=y;current.nz=z;return this;}
    }
}
