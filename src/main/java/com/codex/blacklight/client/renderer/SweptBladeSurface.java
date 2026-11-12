package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A finite, curved cutting ribbon. The closest patch gives its local side;
 * coverage is checked separately, so extrapolated side signs never authorize a sever. */
final class SweptBladeSurface {
    private record Patch(Vec3 a,Vec3 b,Vec3 c,Vec3 normal,Vec3 low,Vec3 high,int order) {}
    private record Sample(Vec3 nearest,Vec3 normal) {}
    private final List<Patch> patches=new ArrayList<>();
    private final Node tree;
    SweptBladeSurface(List<BladeSpan> path){
        Vec3 previous=null;
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);
            if(a.points().size()!=b.points().size())continue;
            for(int j=1;j<a.points().size();j++){
                previous=add(a.points().get(j-1),a.points().get(j),b.points().get(j),previous);
                previous=add(a.points().get(j-1),b.points().get(j),b.points().get(j-1),previous);
            }
        }
        var array=patches.toArray(Patch[]::new);tree=array.length==0?null:new Node(array,0,array.length);
    }
    private Vec3 add(Vec3 a,Vec3 b,Vec3 c,Vec3 previous){
        var cross=b.subtract(a).cross(c.subtract(a));double length=cross.length();if(length<1e-7)return previous;
        var normal=cross.scale(1/length);if(previous!=null&&normal.dot(previous)<0)normal=normal.scale(-1);
        patches.add(new Patch(a,b,c,normal,min(a,min(b,c)),max(a,max(b,c)),patches.size()));return normal;
    }
    boolean empty(){return patches.isEmpty();}
    List<Vec3[]> triangles(){return patches.stream().map(p->new Vec3[]{p.a,p.b,p.c}).toList();}
    boolean overlaps(Vec3 low,Vec3 high,double padding){return tree!=null&&tree.overlaps(low,high,padding);}
    private Sample sample(Vec3 p){
        var nearest=new Nearest();if(tree!=null)tree.find(p,nearest);return new Sample(nearest.point,nearest.normal);
    }
    private static Vec3 min(Vec3 a,Vec3 b){return new Vec3(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z));}
    private static Vec3 max(Vec3 a,Vec3 b){return new Vec3(Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));}
    private static double coordinate(Vec3 p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}
    private static final class Nearest {double distance=Double.POSITIVE_INFINITY;int order=Integer.MAX_VALUE;Vec3 point=Vec3.ZERO,normal=new Vec3(0,1,0);}
    /** Curved blade profiles add patches, not an unbounded scan per skin vertex. */
    private static final class Node {
        final Vec3 low,high;final Patch[] patches;final int from,to;final Node left,right;
        Node(Patch[] patches,int from,int to){
            this.patches=patches;this.from=from;this.to=to;
            Vec3 lo=patches[from].low,hi=patches[from].high;for(int i=from+1;i<to;i++){lo=min(lo,patches[i].low);hi=max(hi,patches[i].high);}low=lo;high=hi;
            if(to-from<=8){left=right=null;return;}
            var size=high.subtract(low);int axis=size.x>=size.y&&size.x>=size.z?0:size.y>=size.z?1:2;
            Arrays.sort(patches,from,to,Comparator.comparingDouble(p->coordinate(p.low,axis)+coordinate(p.high,axis)));
            int mid=(from+to)/2;left=new Node(patches,from,mid);right=new Node(patches,mid,to);
        }
        double gap(Vec3 p){double x=Math.max(low.x-p.x,Math.max(0,p.x-high.x)),y=Math.max(low.y-p.y,Math.max(0,p.y-high.y)),z=Math.max(low.z-p.z,Math.max(0,p.z-high.z));return x*x+y*y+z*z;}
        boolean overlaps(Vec3 lo,Vec3 hi,double padding){
            if(high.x+padding<lo.x||low.x-padding>hi.x||high.y+padding<lo.y||low.y-padding>hi.y||high.z+padding<lo.z||low.z-padding>hi.z)return false;
            if(left!=null)return left.overlaps(lo,hi,padding)||right.overlaps(lo,hi,padding);
            for(int i=from;i<to;i++){var p=patches[i];if(p.high.x+padding>=lo.x&&p.low.x-padding<=hi.x
                    &&p.high.y+padding>=lo.y&&p.low.y-padding<=hi.y&&p.high.z+padding>=lo.z&&p.low.z-padding<=hi.z)return true;}
            return false;
        }
        void find(Vec3 p,Nearest best){
            if(gap(p)>best.distance+1e-12)return;
            if(left!=null){if(left.gap(p)<=right.gap(p)){left.find(p,best);right.find(p,best);}else{right.find(p,best);left.find(p,best);}return;}
            for(int i=from;i<to;i++){var patch=patches[i];var at=closest(p,patch.a,patch.b,patch.c);double d=p.distanceToSqr(at);if(d<best.distance-1e-12||Math.abs(d-best.distance)<=1e-12&&patch.order<best.order){best.distance=d;best.point=at;best.normal=patch.normal;best.order=patch.order;}}
        }
    }
    double distance(Vec3 p){var s=sample(p);return p.subtract(s.nearest).dot(s.normal);}
    Vec3 normal(Vec3 p){return sample(p).normal;}
    double gap(Vec3 p){return p.distanceTo(sample(p).nearest);}
    Vec3 project(Vec3 p){
        // Follow the local zero field, not the nearest finite edge. Otherwise a hole
        // in the ribbon could be silently collapsed to its rim and declared cut.
        for(int i=0;i<12;i++){var sample=sample(p);double d=p.subtract(sample.nearest).dot(sample.normal);if(Math.abs(d)<1e-6)break;p=p.subtract(sample.normal.scale(d));}
        return p;
    }
    boolean covers(Vec3 p){return !empty()&&p.distanceToSqr(sample(p).nearest)<=.045*.045;}
    boolean covers(Vec3 a,Vec3 b){
        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.04));
        for(int i=0;i<=steps;i++)if(!covers(a.lerp(b,i/(double)steps)))return false;
        return true;
    }
    /** Closest point on a triangle, including edges and corners (not its infinite plane). */
    static Vec3 closest(Vec3 p,Vec3 a,Vec3 b,Vec3 c){
        var ab=b.subtract(a);var ac=c.subtract(a);var ap=p.subtract(a);
        double d1=ab.dot(ap),d2=ac.dot(ap);if(d1<=0&&d2<=0)return a;
        var bp=p.subtract(b);double d3=ab.dot(bp),d4=ac.dot(bp);if(d3>=0&&d4<=d3)return b;
        double vc=d1*d4-d3*d2;if(vc<=0&&d1>=0&&d3<=0)return a.add(ab.scale(d1/(d1-d3)));
        var cp=p.subtract(c);double d5=ab.dot(cp),d6=ac.dot(cp);if(d6>=0&&d5<=d6)return c;
        double vb=d5*d2-d1*d6;if(vb<=0&&d2>=0&&d6<=0)return a.add(ac.scale(d2/(d2-d6)));
        double va=d3*d6-d5*d4;if(va<=0&&d4-d3>=0&&d5-d6>=0)return b.add(c.subtract(b).scale((d4-d3)/(d4-d3+d5-d6)));
        double sum=va+vb+vc;if(Math.abs(sum)<1e-20)return a;
        return a.add(ab.scale(vb/sum)).add(ac.scale(vc/sum));
    }
}
