package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Pins one lesion to a rendered face. Its fine vessels fade before they run off the skin. */
final class QuillSkinContact {
    record Surface(Vec3 point, Vec3 outward, Vec3 x, Vec3 z, double left, double right, double back, double front) {
        static Surface face(Vec3 p, Vec3 n, Vec3 a, Vec3 b, Vec3 d) {
            Vec3 x=b.subtract(a).normalize(), z=x.cross(n).normalize();
            double width=b.distanceTo(a), depth=d.subtract(a).dot(z);
            double insetX=Math.min(.17,width*.22),insetZ=Math.min(.17,Math.abs(depth)*.22);
            double alongX=p.subtract(a).dot(x), alongZ=p.subtract(a).dot(z);
            double innerX=Math.max(insetX,Math.min(width-insetX,alongX));
            double innerZ=Math.max(Math.min(0,depth)+insetZ,Math.min(Math.max(0,depth)-insetZ,alongZ));
            p=p.add(x.scale(innerX-alongX)).add(z.scale(innerZ-alongZ));
            double lo=a.subtract(p).dot(z), hi=d.subtract(p).dot(z);
            return new Surface(p,n,x,z,Math.max(0,p.subtract(a).dot(x)),Math.max(0,b.subtract(p).dot(x)),
                    Math.max(0,-Math.min(lo,hi)),Math.max(0,Math.max(lo,hi)));
        }
        Quaternionf rotation() {
            return new Matrix3f().setColumn(0,x.toVector3f()).setColumn(1,outward.toVector3f())
                    .setColumn(2,z.toVector3f()).getNormalizedRotation(new Quaternionf());
        }
        Vec3 fit(Vec3 p) {
            double xRoom=p.x<0?left:right, zRoom=p.z<0?back:front;
            double scale=Math.min(1,Math.min(Math.abs(p.x)<1e-7?1:xRoom/Math.abs(p.x),Math.abs(p.z)<1e-7?1:zRoom/Math.abs(p.z)));
            return new Vec3(p.x*scale,p.y,p.z*scale);
        }
    }
    private record Face(Vec3 p,Vec3 n,Vec3 a,Vec3 b,Vec3 d) {
        Surface posed(Matrix4f m,Vec3 origin) {
            var normal=new Matrix3f(m).invert().transpose().transform(n.toVector3f()).normalize();
            return Surface.face(world(m,origin,p),new Vec3(normal),world(m,origin,a),world(m,origin,b),world(m,origin,d));
        }
    }
    private static final class Binding {
        final WeakReference<QuillOrgan> organ;
        ModelPart part;
        Face face;
        Surface surface;
        float sampleClock=Float.NaN;
        float lastPose=Float.NEGATIVE_INFINITY;
        double best;
        Binding(QuillOrgan q){organ=new WeakReference<>(q);}
    }
    private static final WeakHashMap<LivingEntity,Binding> ACTIVE=new WeakHashMap<>();
    static Surface lookup(LivingEntity host,QuillOrgan organ){
        Binding binding=ACTIVE.get(host);
        if(binding==null || binding.organ.get()!=organ){
            ACTIVE.put(host,new Binding(organ));
            return null;
        }
        return host.tickCount-binding.lastPose<3?binding.surface:null;
    }
    private static Vec3 world(Matrix4f m,Vec3 origin,Vec3 v){return origin.add(new Vec3(m.transformPosition(v.toVector3f())));}
    static void part(LivingEntity host,ModelPart part,Matrix4f matrix,Vec3 origin,float partial){
        Binding s=ACTIVE.get(host);
        if(s==null)return;
        QuillOrgan q=s.organ.get();
        if(q==null || q.isRemoved() || q.phase()>QuillOrgan.EXTRACT){ACTIVE.remove(host);return;}
        float clock=host.tickCount+partial;
        if(Float.isNaN(s.sampleClock) || clock-s.lastPose>3 && s.sampleClock!=clock){
            s.sampleClock=clock;s.best=Double.POSITIVE_INFINITY;s.face=null;s.part=null;
        }
        if(s.sampleClock!=clock && s.part!=null){
            if(s.part==part){s.surface=s.face.posed(matrix,origin);s.lastPose=clock;}
            return;
        }
        var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
        if(cubes.isEmpty() || Math.abs(matrix.determinant())<1e-9)return;
        Vec3 hit=q.getPosition(partial);
        var local=new Matrix4f(matrix).invert().transformPosition(hit.subtract(origin).toVector3f());
        for(var cube:cubes){
            double[] low={cube.minX/16.,cube.minY/16.,cube.minZ/16.};
            double[] high={cube.maxX/16.,cube.maxY/16.,cube.maxZ/16.};
            for(int k=0;k<6;k++){
                int axis=k/2, u=(axis+1)%3, v=(axis+2)%3;
                if(high[u]-low[u]<1e-6 || high[v]-low[v]<1e-6)continue;
                double[] point={Mth.clamp(local.x,low[0],high[0]),Mth.clamp(local.y,low[1],high[1]),Mth.clamp(local.z,low[2],high[2])};
                point[axis]=k%2==0?low[axis]:high[axis];
                Vec3 p=vec(point);
                double distance=world(matrix,origin,p).distanceToSqr(hit);
                if(distance>=s.best)continue;
                double[] normal={0,0,0};normal[axis]=k%2==0?-1:1;
                point[u]=low[u];point[v]=low[v];Vec3 a=vec(point);
                point[u]=high[u];Vec3 b=vec(point);
                point[u]=low[u];point[v]=high[v];Vec3 d=vec(point);
                s.face=new Face(p,vec(normal),a,b,d);s.part=part;s.best=distance;
                s.surface=s.face.posed(matrix,origin);s.lastPose=clock;
            }
        }
    }
    private static Vec3 vec(double[] a){return new Vec3(a[0],a[1],a[2]);}
    private QuillSkinContact(){}
}
