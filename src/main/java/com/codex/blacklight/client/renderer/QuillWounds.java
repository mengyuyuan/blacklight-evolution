package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.ScapulaMount;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Node;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;
import com.codex.blacklight.combat.QuillImpact;
import com.codex.blacklight.network.RagdollImpactPayload;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Intersects the rendered skin, then carries the embedded quill or open bore in bone space. */
final class QuillWounds {
    private static final Map<LivingEntity,List<Mark>> MARKS=new WeakHashMap<>();
    record ExitChip(List<Vertex> skin,Vec3 direction){}
    private static final Map<LivingEntity,ExitChip> EXIT_CHIPS=new WeakHashMap<>();
    static ExitChip takeChip(LivingEntity entity){return EXIT_CHIPS.remove(entity);}
    private static final int SIDES=6;
    private static final double EPS=1e-6;
    private QuillWounds(){}
    static final class Mark {
        final Vec3 point,direction;
        final boolean heavy,fatal;
        final long expires;
        Object key;
        Vec3 localEntry,localExit,localAcross;
        boolean reported;
        Mark(RagdollImpactPayload hit){
            fatal=hit.fatal();
            var wound=hit.wounds().get(0);
            point=wound.point();direction=wound.direction();heavy=hit.motion()==QuillImpact.HEAVY;expires=hit.at()+100;
        }
    }
    static Mark accept(LivingEntity e,RagdollImpactPayload hit){
        if(!QuillImpact.isQuill(hit.motion())||hit.wounds().isEmpty())return null;
        var marks=MARKS.computeIfAbsent(e,k->new ArrayList<>());
        if(marks.size()>=5)marks.remove(0);
        var mark=new Mark(hit);marks.add(mark);return mark;
    }
    static boolean has(LivingEntity e){
        var marks=MARKS.get(e);if(marks==null)return false;
        marks.removeIf(m->e.level().getGameTime()>m.expires);
        if(marks.isEmpty()){MARKS.remove(e);return false;}
        return true;
    }
    static void clear(){MARKS.clear();EXIT_CHIPS.clear();}
    private static Vec3 rotate(Vec3 v,Quaternionf q){return new Vec3(q.transform(v.toVector3f()));}
    private static Node node(LivingEntity e,Map<Object,Node> nodes,Object key,float partial){
        Node n=nodes.get(key);
        return n!=null?n:new Node(key,null,e.getPosition(partial),new Quaternionf().rotationY((float)Math.toRadians(180-e.yBodyRot)),32);
    }
    private record Span(Object key,double first,double last){}
    private static double ray(Vec3 start,Vec3 dir,Vec3 a,Vec3 b,Vec3 c){
        Vec3 edge=b.subtract(a),side=c.subtract(a),cross=dir.cross(side);
        double det=edge.dot(cross);if(Math.abs(det)<1e-10)return Double.POSITIVE_INFINITY;
        Vec3 rel=start.subtract(a);double u=rel.dot(cross)/det;
        if(u< -EPS||u>1+EPS)return Double.POSITIVE_INFINITY;
        Vec3 cross2=rel.cross(edge);double v=dir.dot(cross2)/det;
        if(v< -EPS||u+v>1+EPS)return Double.POSITIVE_INFINITY;
        double t=side.dot(cross2)/det;return t<0?Double.POSITIVE_INFINITY:t;
    }
    private static Span trace(List<Vertex> skin,Vec3 from,Vec3 dir,double minDepth){
        var bounds=new IdentityHashMap<Object,double[]>();
        for(int i=0;i+3<skin.size();i+=4){
            Vertex a=skin.get(i),b=skin.get(i+1),c=skin.get(i+2),d=skin.get(i+3);
            double t=Math.min(ray(from,dir,a.p,b.p,c.p),ray(from,dir,a.p,c.p,d.p));
            if(!Double.isFinite(t))continue;
            var span=bounds.computeIfAbsent(a.key,k->new double[]{t,t});
            span[0]=Math.min(t,span[0]);span[1]=Math.max(t,span[1]);
        }
        Span nearest=null;
        for(var b:bounds.entrySet()){
            double[] ends=b.getValue();
            if(ends[1]-ends[0]<minDepth)continue;
            if(nearest==null||ends[0]<nearest.first)nearest=new Span(b.getKey(),ends[0],ends[1]);
        }
        return nearest;
    }
    private static Vec3 across(Vec3 dir){
        return dir.cross(Math.abs(dir.y)<.85?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
    }
    private static void bind(LivingEntity e,List<Vertex> skin,Map<Object,Node> nodes,float partial,Mark mark){
        float yaw=(float)Math.toRadians(180-e.yBodyRot);
        Vec3 dir=mark.direction.yRot(yaw).normalize(),right=across(dir),up=dir.cross(right);
        Vec3 from=e.getPosition(partial).add(mark.point.yRot(yaw)).subtract(dir.scale(.9));
        double depth=mark.heavy?.025:0;
        Span hit=trace(skin,from,dir,depth);
        // Test the thickness of the flying blade around its centre line. A flat outer
        // layer does not hide the two real body surfaces that a heavy hit needs.
        double width=radius(e);
        for(int ring=1;hit==null&&ring<=2;ring++){
            double offset=width*ring*.5;
            for(int i=0;i<8;i++){
                double angle=i*Math.PI/4;
                Vec3 test=from.add(right.scale(offset*Math.cos(angle))).add(up.scale(offset*Math.sin(angle)));
                hit=trace(skin,test,dir,depth);
                if(hit!=null){from=test;break;}
            }
        }
        if(hit==null||hit.first>e.getBbWidth()+e.getBbHeight()+1)return;
        var n=node(e,nodes,hit.key,partial);var inverse=new Quaternionf(n.rotation()).conjugate();
        var entry=from.add(dir.scale(hit.first));var exit=from.add(dir.scale(mark.heavy ? hit.last : Math.max(hit.first+.04,hit.last)));
        mark.key=hit.key;mark.localEntry=rotate(entry.subtract(n.pivot()),inverse);
        mark.localExit=rotate(exit.subtract(n.pivot()),inverse);mark.localAcross=rotate(right,inverse);
    }
    static ArrayList<Vertex> decorate(LivingEntity e,ArrayList<Vertex> skin,Map<Object,Node> nodes,float partial){
        var marks=MARKS.get(e);if(marks==null)return skin;
        for(var mark:marks)if(mark.key==null)bind(e,skin,nodes,partial,mark);
        for(var mark:marks)if(mark.heavy&&mark.key!=null)skin=apply(e,skin,nodes,partial,mark);
        for(var mark:marks)if(!mark.heavy&&mark.key!=null){
            // A later bore can carry away a lodged blade occupying the same surface.
            boolean displaced=false;
            for(var bore:marks)if(bore.heavy&&bore.key==mark.key&&bore.expires>=mark.expires&&
                    bore.localEntry.distanceToSqr(mark.localEntry)<Math.pow(radius(e),2)){displaced=true;break;}
            if(!displaced)skin=apply(e,skin,nodes,partial,mark);
        }
        return skin;
    }
    static ArrayList<Vertex> fresh(LivingEntity e,ArrayList<Vertex> skin,Map<Object,Node> nodes,Mark mark){
        bind(e,skin,nodes,1,mark);return mark.key==null?skin:apply(e,skin,nodes,1,mark);
    }
    private static double radius(LivingEntity e){return Math.min(.18,Math.max(.07,e.getBbWidth()*.16));}
    private static ArrayList<Vertex> apply(LivingEntity e,ArrayList<Vertex> skin,Map<Object,Node> nodes,float partial,Mark mark){
        var bone=node(e,nodes,mark.key,partial);
        Vec3 a=bone.pivot().add(rotate(mark.localEntry,bone.rotation())),b=bone.pivot().add(rotate(mark.localExit,bone.rotation()));
        Vec3 direction=b.subtract(a).normalize();boolean metal=com.codex.blacklight.combat.PlayerHitEffects.metalAt(e,a);
        var out=new ArrayList<Vertex>(skin.size()+256);
        if(!mark.heavy){
            out.addAll(skin);
            Vertex surface=null;for(var v:skin)if(v.key==mark.key){surface=v;break;}
            if(surface!=null)Barb.attach(out,surface,a.add(direction.scale(Math.min(.22,a.distanceTo(b)*.58))),direction);
        }else{
            Vec3 u=rotate(mark.localAcross,bone.rotation()).normalize(),v=direction.cross(u).normalize();
            boolean eject=mark.fatal && !mark.reported && !metal && com.codex.blacklight.combat.PlayerHitEffects.fleshBody(e)
                    && !com.codex.blacklight.combat.PlayerHitEffects.metalAt(e,b);
            List<Vertex> chip=eject?new ArrayList<>():null;
            perforate(skin,out,mark.key,a,b,u,v,radius(e),metal,RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE),chip);
            if(chip!=null && !chip.isEmpty())EXIT_CHIPS.put(e,new ExitChip(chip,direction));
        }
        if(!mark.reported){
            mark.reported=true;
            if(mark.heavy && !metal && com.codex.blacklight.combat.PlayerHitEffects.fleshBody(e)
                    && !com.codex.blacklight.combat.PlayerHitEffects.metalAt(e,b)) {
                int appearance=com.codex.blacklight.network.InfectedImpactPayload.appearance(
                        com.codex.blacklight.infection.InfectedAttackStyle.PIERCE,com.codex.blacklight.combat.FleshStrike.QUILL,true);
                var exit=new com.codex.blacklight.network.InfectedImpactPayload(-1,e.getId(),appearance,b.x,b.y,b.z,
                        (float)direction.x,(float)direction.y,(float)direction.z,1.20F,mark.expires*31+e.getId());
                WoundBlood.contact(exit,b,direction,e);
            }
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Quill surface victim={} id={} heavy={} depth={} verticesBefore={} verticesAfter={}",e.getType().getDescriptionId(),e.getId(),mark.heavy,a.distanceTo(b),skin.size(),out.size());
        }
        return out;
    }
    private static void triangles(List<Vertex> out,List<Vertex> polygon){
        if(polygon.size()==4){out.addAll(polygon);return;}
        for(int j=1;j+1<polygon.size();j++)Collections.addAll(out,polygon.get(0),polygon.get(j),polygon.get(j+1),polygon.get(j+1));
    }
    /** Remove a convex prism from each struck quad. Interpolated vertices retain the original skin UVs. */
    static void perforate(List<Vertex> skin,List<Vertex> out,Object key,Vec3 a,Vec3 b,Vec3 u,Vec3 v,double radius,boolean metal,RenderType material){
        perforate(skin,out,key,a,b,u,v,radius,metal,material,null);
    }
    static void perforate(List<Vertex> skin,List<Vertex> out,Object key,Vec3 a,Vec3 b,Vec3 u,Vec3 v,double radius,boolean metal,RenderType material,List<Vertex> chip){
        Vec3 travel=b.subtract(a).normalize();
        Vec3[] radial=new Vec3[SIDES],plane=new Vec3[SIDES];double inner=radius*Math.cos(Math.PI/SIDES);
        for(int s=0;s<SIDES;s++){
            double t=2*Math.PI*s/SIDES;
            radial[s]=u.scale(Math.cos(t)).add(v.scale(Math.sin(t)));
            plane[s]=u.scale(Math.cos(t+Math.PI/SIDES)).add(v.scale(Math.sin(t+Math.PI/SIDES)));
        }
        Vertex owner=null;
        for(int i=0;i+3<skin.size();i+=4){
            List<Vertex> face=skin.subList(i,i+4);
            if(face.get(0).key!=key||outside(face,a,plane,inner)){out.addAll(face);continue;}
            if(owner==null)owner=face.get(0);
            List<Vertex> inside=face;var scraps=new ArrayList<Vertex>();
            for(var n:plane){
                var next=new ArrayList<Vertex>();var kept=new ArrayList<Vertex>();
                if(inside.isEmpty())break;
                Vertex prev=inside.get(inside.size()-1);double old=prev.p.subtract(a).dot(n)-inner;
                for(var cur:inside){
                    double now=cur.p.subtract(a).dot(n)-inner;
                    if((now>0)!=(old>0)){
                        var cut=prev.mix(cur,old/(old-now));next.add(cut);kept.add(cut);
                    }
                    if(now>0)kept.add(cur);else next.add(cur);
                    old=now;prev=cur;
                }
                triangles(scraps,kept);inside=next;
            }
            if(inside.isEmpty())out.addAll(face);
            else{
                out.addAll(scraps);
                if(chip!=null && inside.size()>=3){
                    Vec3 mid=Vec3.ZERO;for(var vertex:inside)mid=mid.add(vertex.p);
                    mid=mid.scale(1.0/inside.size());
                    if(Math.abs(mid.subtract(b).dot(travel))<Math.min(radius*.7,a.distanceTo(b)*.3))
                        exitShell(chip,inside,travel,Math.min(radius*.65,a.distanceTo(b)*.3),material);
                }
            }
        }
        if(owner==null)return;
        Vec3 dir=b.subtract(a).normalize();
        // Sample the original surfaces around the rim, including sloped faces.
        Vec3[] front=new Vec3[SIDES],back=new Vec3[SIDES];
        for(int i=0;i<SIDES;i++){
            Vec3 r=radial[i].scale(radius),from=a.add(r).subtract(dir.scale(.7));
            double near=Double.POSITIVE_INFINITY,far=Double.NEGATIVE_INFINITY;
            for(int j=0;j+3<skin.size();j+=4)if(skin.get(j).key==key){
                Vec3 p=skin.get(j).p,q=skin.get(j+1).p,k=skin.get(j+2).p,end=skin.get(j+3).p;
                double t=Math.min(ray(from,dir,p,q,k),ray(from,dir,p,k,end));
                if(Double.isFinite(t)){near=Math.min(near,t);far=Math.max(far,t);}
            }
            front[i]=Double.isFinite(near)?from.add(dir.scale(near)):a.add(r);
            back[i]=far>near+.015?from.add(dir.scale(far)):b.add(r);
        }
        for(int i=0;i<SIDES;i++){
            int j=(i+1)%SIDES;Vec3 normal=radial[i].add(radial[j]).normalize().scale(-1);
            int color=metal?(i%2==0?0xff4d5357:0xff77766d):(i%2==0?0xff682b30:0xff422328);
            for(Vec3 p:List.of(front[i],front[j],back[j],back[i]))
                out.add(new Vertex(key,material,p,normal,.5F,.5F,color,owner.overlay,owner.light));
        }
    }
    private static void exitShell(List<Vertex> out,List<Vertex> surface,Vec3 travel,double thickness,RenderType material){
        triangles(out,surface);
        var back=new ArrayList<Vertex>();
        for(var v:surface)back.add(new Vertex(v.key,material,v.p.subtract(travel.scale(thickness)),travel.scale(-1),
                .5F,.5F,0xff70231f,v.overlay,v.light));
        for(int i=0;i<surface.size();i++){
            int next=(i+1)%surface.size();
            var p=surface.get(i);var q=surface.get(next);
            var normal=q.p.subtract(p.p).cross(travel).normalize();
            for(Vec3 point:List.of(p.p,q.p,back.get(next).p,back.get(i).p))
                out.add(new Vertex(p.key,material,point,normal,.5F,.5F,0xff572428,p.overlay,p.light));
        }
        Collections.reverse(back);triangles(out,back);
    }
    private static boolean outside(List<Vertex> face,Vec3 a,Vec3[] planes,double limit){
        for(var n:planes){
            boolean all=true;
            for(var p:face)if(p.p.subtract(a).dot(n)<=limit){all=false;break;}
            if(all)return true;
        }
        return false;
    }
    /** The same authored, fully opened quill that leaves the shoulder. */
    private static final class Barb {
        static final List<Vertex> SHAPE=new ArrayList<>();
        static Vec3 tip=Vec3.ZERO;
        static {
            var axis=new Vec3(0,1,-.2).normalize();double extent=Double.NEGATIVE_INFINITY;
            var rig=com.codex.blacklight.animation.RooftopRig.INSTANCE;
            var geometry=rig.geometry[rig.mesh[rig.quill[0]]];
            var p=geometry.pos();var normal=geometry.normal();var uv=geometry.uv();
            for(int j=0;j<p.length;j+=9)for(int k=0;k<4;k++){
                int i=j+3*Math.min(k,2);var v=new Vec3(p[i],p[i+1],p[i+2]);
                double d=v.dot(axis);if(d>extent){extent=d;tip=v;}
                var n=new Vec3(normal[i],normal[i+1],normal[i+2]);
                boolean vein=geometry.emissive(i/3);
                SHAPE.add(new Vertex(null,RoofPerformance.material(vein),v,n,uv[i/3*2],uv[i/3*2+1],0xffffffff,0,vein?0xf000f0:0));
            }
        }
        static void attach(List<Vertex> out,Vertex surface,Vec3 point,Vec3 direction){
            var q=new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.2F).normalize(),direction.toVector3f());
            for(var v:SHAPE)out.add(new Vertex(surface.key,v.type,point.add(rotate(v.p.subtract(tip).scale(.9375),q)),
                    rotate(v.n,q),v.u,v.v,v.color,surface.overlay,v.light==0?surface.light:v.light));
        }
    }
}
