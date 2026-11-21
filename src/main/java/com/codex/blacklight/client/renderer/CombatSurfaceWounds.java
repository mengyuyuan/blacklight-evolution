package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Node;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Part;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;
import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.network.InfectedImpactPayload;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Player contact receipts on the actual submitted model. Wounds use the struck bone's local axes. */
public final class CombatSurfaceWounds {
    public record Applied(int victim, int region, String kind, Vec3 contact, Vec3 surface, int before, int after) {}
    public static java.util.function.Consumer<Applied> observer;
    private static final Map<LivingEntity,List<Mark>> MARKS = new WeakHashMap<>();
    private record Site(Object key, int region, Vec3 point, Vec3 normal, double score) {}
    enum Shape { DENT, GROOVE, PUNCTURE }
    private static final class Mark {
        final long seed, expires;
        final Vec3 contact, origin, travel;
        final int region;
        final double radius;
        final Shape shape;
        Object key;
        Vec3 local, inward, tangent;
        boolean reported;
        WeakReference<Object> applied = new WeakReference<>(null);
        Mark(LivingEntity victim, InfectedImpactPayload hit, double start) {
            seed=hit.seed(); expires=(long)start+95; region=hit.region();
            contact=new Vec3(hit.x(),hit.y(),hit.z()); origin=victim.position();
            travel=new Vec3(hit.dx(),hit.dy(),hit.dz()).normalize();
            shape=shape(hit.strike(),InfectedAttackStyle.resolve(hit.style()));
            double size=Math.max(.10,Math.min(.32,victim.getBbWidth()*.30));
            radius=size*Math.min(1.35,Math.max(.65,hit.size()))*(shape==Shape.PUNCTURE?.62:1);
        }
        void bind(Site site,Node node) {
            key=site.key;
            var inverse=new Quaternionf(node.rotation()).conjugate();
            local=rotate(site.point.subtract(node.pivot()),inverse);
            inward=rotate(site.normal.scale(-1),inverse); tangent=rotate(travel,inverse);
        }
        void report(LivingEntity victim,Site site,Vec3 world,int before,int after) {
            if(reported)return;
            reported=true;
            if(observer!=null)observer.accept(new Applied(victim.getId(),site.region,shape.name(),contact,world,before,after));
        }
    }

    static Shape shape(FleshStrike strike,InfectedAttackStyle style) {
        if(style==InfectedAttackStyle.PIERCE)return Shape.PUNCTURE;
        if(strike==FleshStrike.EDGE||strike==FleshStrike.CLAW||strike==FleshStrike.LASH
                ||style==InfectedAttackStyle.WHIP||style==InfectedAttackStyle.TEAR)return Shape.GROOVE;
        return Shape.DENT;
    }
    static void accept(LivingEntity victim,InfectedImpactPayload hit,double start) {
        if(hit.strike()==FleshStrike.QUILL || victim.isRemoved())return;
        var marks=MARKS.computeIfAbsent(victim,k->new ArrayList<>());
        if(marks.stream().anyMatch(m->m.seed==hit.seed()))return;
        if(marks.size()>=5)marks.remove(0);
        marks.add(new Mark(victim,hit,start));
    }
    static void clear(){MARKS.clear();}
    static boolean needsBody(LivingEntity victim,List<Part> parts) {
        return active(victim) && MARKS.get(victim).stream().anyMatch(m -> m.applied.get()!=parts);
    }
    static boolean active(LivingEntity victim) {
        var marks=MARKS.get(victim);
        if(marks==null)return false;
        marks.removeIf(m->victim.level().getGameTime()>m.expires);
        if(marks.isEmpty()){MARKS.remove(victim);return false;}
        return true;
    }
    private static Vec3 rotate(Vec3 v,Quaternionf q){return new Vec3(q.transform(v.toVector3f()));}
    private static Site nearest(List<Vertex> skin, Vec3 contact, Map<Object,Node> nodes, int region, int preferred) {
        Site best=null;
        for(int i=0;i+3<skin.size();i+=4){
            var a=skin.get(i);
            var node=nodes==null?null:nodes.get(a.key);
            int area=node!=null?node.region():region;
            for(int t=1;t<=2;t++){
                var b=skin.get(i+t);var c=skin.get(i+t+1);
                if(b.p.subtract(a.p).cross(c.p.subtract(a.p)).lengthSqr()<1e-12)continue;
                var at=SweptBladeSurface.closest(contact,a.p,b.p,c.p);
                double score=contact.distanceToSqr(at)+(preferred!=0&&area!=preferred?.008:0);
                if(best==null||score<best.score)best=new Site(a.key,area,at,a.n.normalize(),score);
            }
        }
        return best;
    }
    /** Only nearby quads of this bone enter the polygon clipper. */
    private static ArrayList<Vertex> cut(List<Vertex> skin,Object key,Mark mark,Vec3 point,Vec3 axis,Vec3 tangent) {
        var near=new ArrayList<Vertex>();var rest=new ArrayList<Vertex>();
        for(int i=0;i+3<skin.size();i+=4){
            var face=skin.subList(i,i+4);
            var within=(key==null||face.get(0).key==key)
                    && AdaptiveRagdoll.bounds(face).distanceToSqr(point)<mark.radius*mark.radius*2;
            (within?near:rest).addAll(face);
        }
        if(near.isEmpty())return null;
        var lining=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
        var result=mark.shape==Shape.GROOVE?ImpactChip.groove(near,point,axis,tangent,mark.radius,lining):
                ImpactChip.carve(near,point,axis,mark.radius,0,lining);
        if(!result.cut()||rest.size()+result.skin().size()>24576)return null;
        rest.addAll(result.skin());
        return rest;
    }
    private static Node root(LivingEntity e,Object key,float partial){
        return new Node(key,null,e.getPosition(partial),new Quaternionf().rotationY((float)Math.toRadians(180-e.yBodyRot)),32);
    }
    static ArrayList<Vertex> live(LivingEntity e,ArrayList<Vertex> skin,Map<Object,Node> nodes,float partial){
        for(var m:MARKS.get(e)){
            if(m.key==null){
                Vec3 expected=m.contact.add(e.getPosition(partial).subtract(m.origin));
                var site=nearest(skin,expected,nodes,32,m.region);
                if(site==null||site.score>maxGap(e))continue;
                m.bind(site,nodes.getOrDefault(site.key,root(e,site.key,partial)));
            }
            var n=nodes.getOrDefault(m.key,root(e,m.key,partial));
            Vec3 at=n.pivot().add(rotate(m.local,n.rotation()));
            var changed=cut(skin,m.key,m,at,rotate(m.inward,n.rotation()),rotate(m.tangent,n.rotation()));
            if(changed!=null){
                m.report(e,new Site(m.key,n.region(),at,Vec3.ZERO,0),at,skin.size(),changed.size());
                skin=changed;
            }
        }
        return skin;
    }
    private static double maxGap(LivingEntity e){return Math.pow(Math.max(.32,Math.min(1.2,e.getBbWidth()*.8)),2);}
    static void body(LivingEntity e,List<Part> parts,Vec3[] positions,float partial){
        var marks=MARKS.get(e);
        if(marks==null)return;
        for(var m:marks){
            if(m.applied.get()==parts)continue;
            Part hit=null;Site best=null;Quaternionf rotation=null;Vec3 base=null;
            var expected=m.contact.add(e.getPosition(partial).subtract(m.origin));
            for(int i=0;i<parts.size();i++){
                var p=parts.get(i);
                if(p.scale<.01||p.detachedTissue||p.mesh.isEmpty())continue;
                var turn=new Quaternionf(p.oldRotation).slerp(p.rotation,partial);
                if(m.key!=null){
                    if(p.key!=m.key)continue;
                    var at=p.bind.pivot().add(rotate(m.local,p.bind.rotation()));
                    best=new Site(p.key,p.bind.region(),at,rotate(m.inward,p.bind.rotation()).scale(-1),0);
                }else{
                    var local=p.center.add(rotate(expected.subtract(positions[i]),new Quaternionf(turn).conjugate()).scale(1/p.scale));
                    var site=nearest(p.mesh,local,null,p.bind.region(),m.region);
                    if(site==null||site.score>maxGap(e)||best!=null&&site.score>=best.score)continue;
                    best=new Site(p.key,p.bind.region(),site.point,site.normal,site.score);
                }
                hit=p;rotation=turn;base=positions[i];
                if(m.key!=null)break;
            }
            if(hit==null)continue;
            if(m.key==null){
                m.bind(best,hit.bind);
                m.tangent=rotate(rotate(m.travel,new Quaternionf(rotation).conjugate()),new Quaternionf(hit.bind.rotation()).conjugate());
            }
            var changed=cut(hit.mesh,null,m,best.point,best.normal.scale(-1),rotate(m.tangent,hit.bind.rotation()));
            if(changed==null)continue;
            var world=base.add(rotate(best.point.subtract(hit.center).scale(hit.scale),rotation));
            m.report(e,best,world,hit.mesh.size(),changed.size());
            hit.mesh.clear();hit.mesh.addAll(changed);hit.meshRevision++;
            m.applied=new WeakReference<>(parts);
        }
    }
    private CombatSurfaceWounds() {}
}
