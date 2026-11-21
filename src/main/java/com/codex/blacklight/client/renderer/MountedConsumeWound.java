package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.client.renderer.ConsumeFracture.Collector;
import com.codex.blacklight.client.renderer.ConsumeFracture.V;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;
import com.codex.blacklight.power.PowerForm;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Surface cuts for the native, animated mounted victim. A wound belongs to the hit triangle, even as it turns. */
public final class MountedConsumeWound {
    public record Sample(int victim,double seconds,int sites,int vertices,double gap,Vec3 struck,Vec3 closestSite,Vec3 owner){}
    public static java.util.function.Consumer<Sample> observer;
    public record Response(int victim,int owner,long start,String mode,int ordinal,int region,double age,
                           int submitted,int displaced,double maximum,double rms){}
    public static java.util.function.Consumer<Response> responseObserver;
    private record Anchor(RenderType layer,int index,int triangle,int count,int region,Vec3 bary,Vec3 tangentBary,double radius,String kind,int event){}
    private record Site(Anchor anchor,Vec3 world,double gap){}
    private static final class Session {
        final long start;final int owner;final List<Anchor> anchors=new ArrayList<>();final Map<Anchor,Double> since=new HashMap<>();
        Session(ConsumeVisual v){start=v.started();owner=v.consumerId();}
    }
    private static final Map<LivingEntity,Session> SESSIONS=new WeakHashMap<>();
    private MountedConsumeWound(){}
    private static Vec3 pos(V v){return new Vec3(v.p.x,v.p.y,v.p.z);}
    static int anatomy(V v){
        int best=0;
        for(int i=1;i<v.weights.length;i++)if(v.weights[i]>v.weights[best])best=i;
        return best;
    }
    private static int mask(int anatomy){return switch(anatomy){case 1->1;case 2->2;case 3->4;case 4->8;case 5->16;default->32;};}
    static void apply(LivingEntity victim,ConsumeVisual visual,float partial,Map<RenderType,Collector> streams,Function<V,Vec3> world){
        if(visual.stage()!=ConsumeVisual.MOUNT_CONSUME&&visual.stage()!=ConsumeVisual.HUNTER_CONSUME)return;
        if(!(victim.level().getEntity(visual.consumerId()) instanceof LivingEntity owner))return;
        var session=SESSIONS.get(victim);
        if(session==null||session.start!=visual.started()||session.owner!=visual.consumerId()){
            session=new Session(visual);SESSIONS.put(victim,session);
        }
        int event=0;
        double seconds=visual.elapsed(victim.level().getGameTime(),partial)/20;
        boolean hunter=visual.stage()==ConsumeVisual.HUNTER_CONSUME;
        double now=hunter?HunterConsume.seconds(visual,victim.level().getGameTime(),partial):seconds;
        if(hunter&&now>=HunterConsume.EFFECT_REVERSE_SECONDS)event=1;
        int ordinal=event;
        boolean accepted=session.anchors.stream().anyMatch(a->a.event==ordinal);
        double start=hunter?(event==0?HunterConsume.EFFECT_BEGIN_SECONDS:HunterConsume.EFFECT_REVERSE_SECONDS):
                ConsumeSequence.contact(PowerForm.values()[visual.form()],true)*(visual.duration()-1)/20.;
        Site candidate=null;
        PowerForm form=PowerForm.values()[visual.form()];
        boolean fit=!hunter&&!ConsumeSequence.bruteMount(victim)&&(form==PowerForm.NONE||form==PowerForm.MUSCLE_MASS||form==PowerForm.HAMMERFISTS);
        if(!accepted&&now>=start-(fit?.25:0)&&now<start+.35){
            Vec3[] sites=ConsumeTrauma.mountSites(owner,visual,partial);
            if(fit){
                Site nearby=closest(streams,world,sites,.70,.12,"dent",event,false);
                if(nearby!=null){
                    Vec3 point=Arrays.stream(sites).min(Comparator.comparingDouble(v->v.distanceToSqr(nearby.world))).orElse(null);
                    if(point!=null)MountedBodyFit.align(owner,victim,visual,start,nearby.world,point,now);
                }
            }
            double reach=Math.min(.20,Math.max(.085,owner.getBbWidth()*.22));
            if(observer!=null){
                Site nearest=closest(streams,world,sites,16,.12,"dent",event,false);
                Vec3 closestSite=nearest==null?null:Arrays.stream(sites).min(Comparator.comparingDouble(x->x.distanceToSqr(nearest.world))).orElse(null);
                observer.accept(new Sample(victim.getId(),now,sites.length,streams.values().stream().mapToInt(c->c.vertices.size()).sum(),
                        nearest==null?Double.NaN:Math.sqrt(nearest.gap),nearest==null?null:nearest.world,closestSite,owner.getPosition(partial)));
            }
            if(now>=start)candidate=closest(streams,world,sites,fit?Math.min(reach,.065):reach,Math.min(.31,Math.max(.12,victim.getBbWidth()*.12)),
                    ConsumeContactMarks.mountedKind(visual),event,hunter);
        }
        var marks=new ArrayList<>(session.anchors);
        if(candidate!=null)marks.add(candidate.anchor);
        if(marks.isEmpty())return;
        var centers=new ArrayList<Vec3>();var directions=new ArrayList<Vec3>();var strokes=new ArrayList<Vec3>();
        for(var mark:marks){
            var layer=streams.get(mark.layer);
            if(layer==null||layer.vertices.size()!=mark.count){centers.add(null);directions.add(null);strokes.add(null);continue;}
            var tri=layer.vertices;var a=pos(tri.get(mark.index));var b=pos(tri.get(mark.index+mark.triangle));var c=pos(tri.get(mark.index+mark.triangle+1));
            centers.add(ConsumeContactMarks.weighted(mark.bary,a,b,c));
            strokes.add(ConsumeContactMarks.weighted(mark.tangentBary,a,b,c));
            directions.add(b.subtract(a).cross(c.subtract(a)).normalize().scale(-1));
        }
        for(int n=0;n<marks.size();n++){
            var mark=marks.get(n);if(centers.get(n)==null||directions.get(n).lengthSqr()<.5)continue;
            var layer=streams.get(mark.layer);int before=layer.vertices.size();
            if(!cut(layer,mark,centers.get(n),directions.get(n),strokes.get(n)))continue;
            boolean first=candidate!=null&&mark==candidate.anchor;
            double strike=first?now:session.since.getOrDefault(mark,now);
            var motion=MountedSurfaceResponse.of(PowerForm.values()[visual.form()],hunter,mark.event);
            motion.apply(layer.vertices,centers.get(n),directions.get(n),strokes.get(n),mark.radius,(now-strike)*20,
                    victim.getId(),visual,mark.event,mask(mark.region));
            if(first){
                session.anchors.add(mark);session.since.put(mark,now);
                ConsumeContactMarks.confirmed(victim,visual,mark.kind,mark.event,candidate.world,mask(mark.region),before,layer.vertices.size());
            }
        }
    }
    private static Site closest(Map<RenderType,Collector> streams,Function<V,Vec3> world,Vec3[] sites,double reach,double radius,String kind,int event,boolean preferHead){
        if(sites.length==0)return null;
        double best=reach*reach;Site result=null;
        for(var entry:streams.entrySet()){
            var layer=entry.getValue();var vs=layer.vertices;int size=layer.quads?4:3;
            for(int i=0;i+size<=vs.size();i+=size){
                V first=vs.get(i);if(first.a<24)continue;
                int region=anatomy(first);Vec3 a=world.apply(first);
                for(int tri=1;tri<size-1;tri++){
                    Vec3 b=world.apply(vs.get(i+tri)),c=world.apply(vs.get(i+tri+1));
                    if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1e-15)continue;
                    double minX=Math.min(a.x,Math.min(b.x,c.x))-reach,maxX=Math.max(a.x,Math.max(b.x,c.x))+reach;
                    double minY=Math.min(a.y,Math.min(b.y,c.y))-reach,maxY=Math.max(a.y,Math.max(b.y,c.y))+reach;
                    double minZ=Math.min(a.z,Math.min(b.z,c.z))-reach,maxZ=Math.max(a.z,Math.max(b.z,c.z))+reach;
                    for(int site=0;site<sites.length;site++){
                        Vec3 point=sites[site];
                        if(point.x<minX||point.x>maxX||point.y<minY||point.y>maxY||point.z<minZ||point.z>maxZ)continue;
                        Vec3 at=SweptBladeSurface.closest(point,a,b,c);double gap=at.distanceToSqr(point);
                        if(gap>=reach*reach)continue;
                        double score=gap+(preferHead&&region!=ConsumeAnatomy.HEAD?.00015:0);
                        if(score>=best)continue;
                        best=score;
                        Vec3 tangent=sites.length>1?sites[Math.min(sites.length-1,site+1)]
                                .subtract(sites[Math.max(0,site-1)]):b.subtract(a);
                        Vec3 weights=tangentWeights(tangent,a,b,c);
                        Vec3 la=pos(first),lb=pos(vs.get(i+tri)),lc=pos(vs.get(i+tri+1));
                        double modelScale=(la.distanceTo(lb)+la.distanceTo(lc))/(a.distanceTo(b)+a.distanceTo(c));
                        result=new Site(new Anchor(entry.getKey(),i,tri,vs.size(),region,ConsumeContactMarks.barycentric(at,a,b,c),
                                weights,radius*modelScale,kind,event),at,gap);
                    }
                }
            }
        }
        return result;
    }
    // Signed triangle weights transport the contact tangent with the animated skin.
    static Vec3 tangentWeights(Vec3 tangent,Vec3 a,Vec3 b,Vec3 c){
        Vec3 ab=b.subtract(a),ac=c.subtract(a);
        double xx=ab.lengthSqr(),xy=ab.dot(ac),yy=ac.lengthSqr(),det=xx*yy-xy*xy;
        if(det<1e-16)return new Vec3(-1,1,0);
        double x=(ab.dot(tangent)*yy-ac.dot(tangent)*xy)/det;
        double y=(ac.dot(tangent)*xx-ab.dot(tangent)*xy)/det;
        if(ab.scale(x).add(ac.scale(y)).lengthSqr()<1e-12)return new Vec3(-1,1,0);
        return new Vec3(-x-y,x,y);
    }
    private static boolean cut(Collector layer,Anchor mark,Vec3 center,Vec3 inward,Vec3 stroke){
        return patch(layer.vertices,layer.quads,mark.region,mark.radius,mark.kind,center,inward,stroke,mark.layer,
                RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
    }
    static boolean patch(List<V> vs,boolean quads,int region,double radius,String kind,Vec3 center,Vec3 inward,Vec3 stroke,
                         RenderType surface,RenderType lining){
        int count=quads?4:3;
        var changed=new ArrayList<V>(vs.size());boolean any=false;
        for(int i=0;i+count<=vs.size();i+=count){
            var original=vs.subList(i,i+count);boolean near=false;
            if(anatomy(original.get(0))==region){
                Vec3 a=pos(original.get(0));
                for(int j=1;j+1<count&&!near;j++){
                    Vec3 b=pos(original.get(j)),c=pos(original.get(j+1));
                    if(b.subtract(a).cross(c.subtract(a)).lengthSqr()>1e-15)
                        near=SweptBladeSurface.closest(center,a,b,c).distanceToSqr(center)<radius*radius*1.4;
                }
            }
            if(!near){changed.addAll(original);continue;}
            for(int j=1;j+1<count;j++){
                List<V> tri=List.of(original.get(0),original.get(j),original.get(j+1));
                var raw=new ArrayList<Vertex>(4);
                for(var v:tri)raw.add(new Vertex(null,surface,pos(v),new Vec3(v.nx,v.ny,v.nz),v.u,v.v,
                        v.a<<24|v.r<<16|v.g<<8|v.b,v.overlay,v.light));
                raw.add(raw.get(2));
                boolean narrow=kind.equals("slash")||kind.equals("tear");
                var patch=narrow?ImpactChip.groove(raw,center,inward,stroke,radius,lining):
                        ImpactChip.carve(raw,center,inward,radius,0,lining);
                any|=patch.cut();
                for(int k=0;k<patch.skin().size();k++){
                    if(!quads&&k%4==3)continue;
                    var pv=patch.skin().get(k);var w=ConsumeContactMarks.barycentric(pv.p,pos(tri.get(0)),pos(tri.get(1)),pos(tri.get(2)));
                    float sum=(float)(w.x+w.y);V v=tri.get(0).mix(tri.get(1),sum<.00001F?0:(float)w.y/sum).mix(tri.get(2),(float)w.z);
                    v.p=pv.p.toVector3f();v.r=pv.color>>16&255;v.g=pv.color>>8&255;v.b=pv.color&255;
                    changed.add(v);
                }
            }
        }
        if(any){vs.clear();vs.addAll(changed);}
        return any;
    }
}
