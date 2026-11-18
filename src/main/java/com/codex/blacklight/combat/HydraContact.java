package com.codex.blacklight.combat;

import com.codex.blacklight.city.HydraMotion;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual authored cuboids on the same sampled pose used by Gecko. A broad radius
 * only finds candidates; the oriented contact surfaces decide every body hit. */
public final class HydraContact {
    private record Cube(int bone,Vec3 center,Vec3 half) {}
    public record Sample(float age,HydraCombatAction.Window window,Box box) {}
    public record Obstacle(float safeAge,Vec3 point,AABB shape) {}
    public record Region(String bone,List<Box> boxes,AABB bounds) {}
    private record Projection(Vec3 axis,double radius) {}
    public static final class Box {
        public final Vec3 center;public final AABB bounds;public final List<Vec3> points;
        private final Vec3[] axes,dual,normals;private final Vec3 half;private final List<Projection> projections;
        private Box(Vec3 center,Vec3 half,Vec3[] axes){
            this(center,half,axes,false);
        }
        private Box(Vec3 center,Vec3 half,Vec3[] axes,boolean affine){
            this.center=center;this.half=half;this.axes=axes;
            if(affine){double det=axes[0].dot(axes[1].cross(axes[2]));if(Math.abs(det)<1e-12)throw new IllegalArgumentException("Singular receiving surface");dual=new Vec3[]{axes[1].cross(axes[2]).scale(1/det),axes[2].cross(axes[0]).scale(1/det),axes[0].cross(axes[1]).scale(1/det)};}else dual=null;
            normals=dual==null?axes:dual;
            var world=List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
            var all=new ArrayList<Vec3>(world);all.addAll(Arrays.asList(normals));for(var a:world)for(var b:axes)all.add(a.cross(b));
            projections=all.stream().filter(a->a.lengthSqr()>1e-14).map(a->new Projection(a,half.x*Math.abs(a.dot(axes[0]))+half.y*Math.abs(a.dot(axes[1]))+half.z*Math.abs(a.dot(axes[2])))).toList();
            var p=new ArrayList<Vec3>(27);for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)p.add(center.add(axes[0].scale(x*half.x)).add(axes[1].scale(y*half.y)).add(axes[2].scale(z*half.z)));
            points=List.copyOf(p);AABB box=new AABB(center,center);for(var q:p)box=box.minmax(new AABB(q,q));bounds=box;
        }
        public boolean touches(AABB target){
            if(!bounds.inflate(.00001).intersects(target))return false;
            var delta=target.getCenter().subtract(center);double x=target.getXsize()/2,y=target.getYsize()/2,z=target.getZsize()/2;
            for(var p:projections)if(Math.abs(delta.dot(p.axis))>p.radius+x*Math.abs(p.axis.x)+y*Math.abs(p.axis.y)+z*Math.abs(p.axis.z)+1e-6)return false;
            return true;
        }
        /** Continuous SAT for translating world-aligned cargo against this
         * affine skin box. The enclosing AABB is broad phase only; all face and
         * edge axes participate, including nonuniform-scale/sheared limbs. */
        public ProjectileSphereContact.Hit sweepBox(Vec3 from,Vec3 to,Vec3 cargoHalf){
            if(!finite(from)||!finite(to)||!finite(cargoHalf)||cargoHalf.x<0||cargoHalf.y<0||cargoHalf.z<0)
                throw new IllegalArgumentException("Finite cargo sweep and nonnegative half extents required");
            var delta=from.subtract(center);var travel=to.subtract(from);
            double enter=0,exit=1,penetration=Double.POSITIVE_INFINITY;Vec3 normal=null,insideNormal=null;
            for(var projection:projections){
                double length=projection.axis.length();var axis=projection.axis.scale(1/length);
                double radius=projection.radius/length+cargoHalf.x*Math.abs(axis.x)+cargoHalf.y*Math.abs(axis.y)+cargoHalf.z*Math.abs(axis.z);
                double offset=delta.dot(axis),speed=travel.dot(axis);
                double overlap=radius-Math.abs(offset);
                if(overlap<penetration){penetration=overlap;insideNormal=axis.scale(offset<0?-1:1);}
                if(Math.abs(speed)<1e-12){if(Math.abs(offset)>radius+1e-7)return null;continue;}
                double first=(-radius-offset)/speed,last=(radius-offset)/speed;
                if(first>last){double swap=first;first=last;last=swap;}
                if(first>enter){enter=first;normal=axis.scale(speed>0?-1:1);}
                exit=Math.min(exit,last);
                if(enter>exit+1e-9||exit<0||enter>1)return null;
            }
            var at=from.lerp(to,Math.max(0,Math.min(1,enter)));
            if(normal==null)normal=insideNormal==null?new Vec3(0,1,0):insideNormal;
            // Clip the contact features, not a fixed number of alternating
            // projections (which leave an error at acute/sheared edge contacts).
            var cargo=new AABB(at.subtract(cargoHalf),at.add(cargoHalf));
            var point=sweepPoint(cargo);
            double probeLength=bounds.getSize()*3+cargoHalf.length()+1;
            if(contains(at)){
                var outside=at.add(normal.scale(probeLength));
                point=clip(outside,at).orElseGet(()->nearest(outside));
            }else point=clip(point.add(normal.scale(probeLength)),point).orElse(point);
            return new ProjectileSphereContact.Hit(Math.max(0,Math.min(1,enter)),at,point,normal);
        }
        private Vec3 sweepPoint(AABB cargo){
            int[] ids={0,18,6,24,2,20,8,26};var skin=new Vec3[8];var moving=new Vec3[8];
            var candidates=new ArrayList<Vec3>();
            for(int i=0;i<8;i++){
                skin[i]=points.get(ids[i]);moving[i]=new Vec3((i&1)==0?cargo.minX:cargo.maxX,(i&2)==0?cargo.minY:cargo.maxY,(i&4)==0?cargo.minZ:cargo.maxZ);
                if(HunterContactPose.nearest(cargo,skin[i]).distanceToSqr(skin[i])<1e-14)candidates.add(skin[i]);
                if(nearest(moving[i]).distanceToSqr(moving[i])<1e-14)candidates.add(nearest(moving[i]));
            }
            for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){
                cargo.clip(skin[i],skin[i|bit]).ifPresent(candidates::add);
                cargo.clip(skin[i|bit],skin[i]).ifPresent(candidates::add);
                clip(moving[i],moving[i|bit]).ifPresent(candidates::add);
                clip(moving[i|bit],moving[i]).ifPresent(candidates::add);
            }
            var at=cargo.getCenter();var distinct=new ArrayList<Vec3>();
            for(var candidate:candidates){
                if(HunterContactPose.nearest(cargo,candidate).distanceToSqr(candidate)>1e-12||nearest(candidate).distanceToSqr(candidate)>1e-12)continue;
                if(distinct.stream().noneMatch(p->p.distanceToSqr(candidate)<1e-14))distinct.add(candidate);
            }
            // Initial complete containment has no intersecting surface features;
            // the caller chooses the nearest outward skin for that case.
            if(distinct.isEmpty())return nearest(at);
            // Their convex mean stays on the contact manifold and avoids
            // selecting an arbitrary top/bottom vertex on an edge collision.
            var sum=Vec3.ZERO;for(var p:distinct)sum=sum.add(p.subtract(at));
            return at.add(sum.scale(1D/distinct.size()));
        }
        private static boolean finite(Vec3 point){return point!=null&&Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);}
        public Vec3 nearest(Vec3 point){
            if(dual==null){var d=point.subtract(center);return center.add(axes[0].scale(clamp(d.dot(axes[0]),half.x))).add(axes[1].scale(clamp(d.dot(axes[1]),half.y))).add(axes[2].scale(clamp(d.dot(axes[2]),half.z)));}
            if(contains(point))return point;
            // Nonuniform parent scale followed by cube rotation produces a skewed
            // cuboid. Project to its six faces and twelve edges, not orthogonal axes.
            Vec3 best=center;double distance=point.distanceToSqr(best);double[] h={half.x,half.y,half.z};
            for(int i=0;i<3;i++){
                int j=(i+1)%3,k=(i+2)%3;
                for(int sign:new int[]{-1,1}){
                    var face=center.add(axes[i].scale(h[i]*sign));var normal=dual[i];
                    var p=point.subtract(normal.scale(point.subtract(face).dot(normal)/normal.lengthSqr()));var q=local(p);double[] v={q.x,q.y,q.z};
                    double d=point.distanceToSqr(p);if(Math.abs(v[j])<=h[j]+1e-8&&Math.abs(v[k])<=h[k]+1e-8&&d<distance){best=p;distance=d;}
                }
                for(int a:new int[]{-1,1})for(int b:new int[]{-1,1}){
                    var edge=center.add(axes[j].scale(h[j]*a)).add(axes[k].scale(h[k]*b));
                    var p=edge.add(axes[i].scale(clamp(point.subtract(edge).dot(axes[i])/axes[i].lengthSqr(),h[i])));double d=point.distanceToSqr(p);
                    if(d<distance){best=p;distance=d;}
                }
            }return best;
        }
        /** Minimum translation out of terrain; SAT axes retain rotated thin wings. */
        public Vec3 separation(AABB target){
            if(!bounds.intersects(target))return null;
            var delta=center.subtract(target.getCenter());double x=target.getXsize()/2,y=target.getYsize()/2,z=target.getZsize()/2,best=Double.POSITIVE_INFINITY;Vec3 normal=null;
            for(var p:projections){double length=p.axis.length();
                double overlap=(p.radius+x*Math.abs(p.axis.x)+y*Math.abs(p.axis.y)+z*Math.abs(p.axis.z)-Math.abs(delta.dot(p.axis)))/length;
                if(overlap<=1e-7)return null;
                if(overlap<best){best=overlap;normal=p.axis.scale((delta.dot(p.axis)<0?-1:1)/length);}
            }return normal==null?null:normal.scale(best+.00001);
        }
        private Vec3 local(Vec3 p){var d=p.subtract(center);var basis=dual==null?axes:dual;return new Vec3(d.dot(basis[0]),d.dot(basis[1]),d.dot(basis[2]));}
        private Vec3 world(Vec3 p){return center.add(axes[0].scale(p.x)).add(axes[1].scale(p.y)).add(axes[2].scale(p.z));}
        public Vec3 localPoint(Vec3 point){return local(point);}
        public Vec3 worldPoint(Vec3 point){return world(point);}
        private AABB localBounds(){return new AABB(-half.x,-half.y,-half.z,half.x,half.y,half.z);}
        public boolean contains(Vec3 point){return localBounds().inflate(1e-6).contains(local(point));}
        public Optional<Vec3> clip(Vec3 from,Vec3 to){
            if(contains(from))return Optional.of(from);
            return localBounds().clip(local(from),local(to)).map(this::world);
        }
        public Optional<Vec3> triangle(Vec3 a,Vec3 b,Vec3 c){
            var broad=new AABB(a,b).minmax(new AABB(c,c));if(!bounds.inflate(.015).intersects(broad.inflate(.00001)))return Optional.empty();
            var la=local(a);var lb=local(b);var lc=local(c);var box=localBounds();
            return WeaponCutContact.triangleBox(la,lb,lc,box)?Optional.of(world(WeaponCutContact.intersectionCenter(la,lb,lc,box))):Optional.empty();
        }
        private double radius(Vec3 axis){return half.x*Math.abs(axis.dot(axes[0]))+half.y*Math.abs(axis.dot(axes[1]))+half.z*Math.abs(axis.dot(axes[2]));}
        public boolean touches(Box other){
            if(!bounds.inflate(.00001).intersects(other.bounds))return false;
            var delta=other.center.subtract(center);var directions=new ArrayList<Vec3>();directions.addAll(Arrays.asList(normals));directions.addAll(Arrays.asList(other.normals));
            for(var a:axes)for(var b:other.axes)directions.add(a.cross(b));
            for(var a:directions)if(a.lengthSqr()>1e-14&&Math.abs(delta.dot(a))>radius(a)+other.radius(a)+1e-6)return false;
            return true;
        }
        public Vec3 contact(Box other){Vec3 p=other.center;for(int i=0;i<12;i++)p=nearest(other.nearest(p));return p;}
        public Vec3 contact(AABB target){
            // Alternating convex projections locates the actual shared surface, including
            // a small target beside the tip rather than the enclosing box's centre.
            Vec3 point=target.getCenter();for(int i=0;i<8;i++)point=HunterContactPose.nearest(target,nearest(point));return point;
        }
        private static double clamp(double n,double h){return Math.max(-h,Math.min(h,n));}
    }
    private static final Map<String,List<Cube>> CUBES=load();
    public static List<String> regionBones(){return Arrays.stream(HydraMotion.INSTANCE.bones).filter(b->!CUBES.getOrDefault(b,List.of()).isEmpty()).toList();}
    public static List<Region> regions(float[][] pose,Vec3 origin,float yaw,float size){
        var result=new ArrayList<Region>();var matrices=HydraMotion.INSTANCE.transforms(pose);
        for(String bone:regionBones()){
            var boxes=boxes(matrices,CUBES.get(bone),origin,yaw,size);AABB bounds=boxes.get(0).bounds;
            for(var b:boxes)bounds=bounds.minmax(b.bounds);result.add(new Region(bone,boxes,bounds));
        }return result;
    }
    public static Box oriented(Vec3 center,Vec3 half,Vec3 right,Vec3 up,Vec3 forward){return new Box(center,half,new Vec3[]{right,up,forward});}
    /** Keeps shear from a rotated cube under nonuniform bone scale. Existing OBB callers retain their path. */
    public static Box affine(Vec3 center,Vec3 half,Vec3 right,Vec3 up,Vec3 forward){return new Box(center,half,new Vec3[]{right,up,forward},true);}
    private static Map<String,List<Cube>> load(){
        try(var in=Objects.requireNonNull(HydraContact.class.getResourceAsStream("/assets/blacklight/geo/blacklight_hydra_mc.geo.json"));var r=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var map=new HashMap<String,List<Cube>>();var bones=JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for(var b:bones){var bone=b.getAsJsonObject();String name=bone.get("name").getAsString();var cubes=new ArrayList<Cube>();
                for(var c:bone.getAsJsonArray("cubes")){var cube=c.getAsJsonObject();if(cube.has("rotation")||cube.has("inflate"))throw new IllegalArgumentException("Update Hydra contact for transformed cube");
                    var o=cube.getAsJsonArray("origin");var s=cube.getAsJsonArray("size");double x=o.get(0).getAsDouble(),y=o.get(1).getAsDouble(),z=o.get(2).getAsDouble(),sx=s.get(0).getAsDouble(),sy=s.get(1).getAsDouble(),sz=s.get(2).getAsDouble();
                    cubes.add(new Cube(Arrays.asList(HydraMotion.INSTANCE.bones).indexOf(name),new Vec3(-(x+sx/2)/16,(y+sy/2)/16,(z+sz/2)/16),new Vec3(sx/32,sy/32,sz/32)));
                }map.put(name,List.copyOf(cubes));
            }return Map.copyOf(map);
        }catch(Exception e){throw new IllegalStateException("Invalid Hydra contact geometry",e);}
    }
    public static String bone(String joint){return switch(joint){case "Claw_L"->"claw_left";case "Claw_R"->"claw_right";case "Head"->"head";case "full_rig_body3"->"neck_2";case "full_rig_body1"->"neck_0";case "Neck_3"->"neck_5";default->throw new IllegalArgumentException("Unknown Hydra contact joint "+joint);};}
    public static List<Box> boxes(int action,float age,String joint,Vec3 origin,float yaw,float size){
        return boxes(HydraMotion.INSTANCE.sample(action,age),joint,origin,yaw,size);
    }
    public static List<Box> boxes(float[][] pose,String joint,Vec3 origin,float yaw,float size){
        var geometry=new ArrayList<Cube>(CUBES.get(bone(joint)));
        // The mouth is articulated under Head. Its visible underside is part of
        // the blunt head contact, otherwise the jaw can land with no terrain event.
        if(joint.equals("Head"))geometry.addAll(CUBES.get("jaw"));
        return boxes(pose,geometry,origin,yaw,size);
    }
    /** Every moving visible cuboid, including the neck and the horn roots. */
    public static List<Box> body(float[][] pose,Vec3 origin,float yaw,float size){
        return boxes(pose,Body.CUBES,origin,yaw,size);
    }
    private static final class Body {
        static final List<Cube> CUBES=HydraContact.CUBES.entrySet().stream()
                .filter(e->!e.getKey().equals("root")&&!e.getKey().equals("base"))
                .sorted(Map.Entry.comparingByKey()).flatMap(e->e.getValue().stream()).toList();
    }
    private static List<Box> boxes(float[][] pose,List<Cube> geometry,Vec3 origin,float yaw,float size){
        return boxes(HydraMotion.INSTANCE.transforms(pose),geometry,origin,yaw,size);
    }
    private static List<Box> boxes(Matrix4f[] matrices,List<Cube> geometry,Vec3 origin,float yaw,float size){
        var result=new ArrayList<Box>();
        var base=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(size);
        for(var c:geometry){var m=new Matrix4f(base).mul(matrices[c.bone]);
            var center=m.transformPosition(c.center.toVector3f());var axes=new Vec3[3];for(int k=0;k<3;k++){var a=m.transformDirection(new Vector3f(k==0?1:0,k==1?1:0,k==2?1:0)).normalize();axes[k]=new Vec3(a.x,a.y,a.z);}
            // GeoEntityRenderer lifts its local model by .01 before drawing.
            result.add(new Box(origin.add(center.x,center.y+.01*size,center.z),c.half.scale(size),axes));
        }return result;
    }
    /** Continuous playback stops before a solid obstacle, rather than letting the
     * renderer cross it after the server has merely rejected the damage. */
    public static Obstacle obstacle(java.util.function.DoubleFunction<List<Box>> pose,float from,float to,List<AABB> terrain){
        if(terrain.isEmpty())return null;
        float safe=from;int count=Math.max(1,(int)Math.ceil(Math.abs(to-from)*16));
        for(int i=0;i<=count;i++){
            float t=from+(to-from)*i/count;Box hit=null;AABB shape=null;
            outer:for(var b:pose.apply(t))for(var block:terrain)if(b.touches(block.inflate(.015))){hit=b;shape=block;break outer;}
            if(hit!=null){
                float blocked=t;
                for(int j=0;j<8&&i>0;j++){
                    float mid=(safe+blocked)*.5F;boolean overlap=false;
                    outer:for(var b:pose.apply(mid))for(var block:terrain)if(b.touches(block.inflate(.015))){overlap=true;break outer;}
                    if(overlap)blocked=mid;else safe=mid;
                }
                return new Obstacle(safe,hit.contact(shape),shape);
            }
            safe=t;
        }
        return null;
    }
    /** Rewind the accepted path with a short eased start. No new attack window
     * is evaluated during this recovery; it is an MC obstacle-response adaptation. */
    public static float recoilPoseAge(float clock,float stop){
        if(stop<0||clock<=stop)return clock;
        float t=clock-stop,d=t<3?t*t/6:t-1.5F;
        return Math.max(0,stop-d);
    }
    public static List<AABB> terrain(net.minecraft.world.entity.Entity actor,AABB bounds){
        var out=new ArrayList<AABB>();
        for(var shape:actor.level().getBlockCollisions(actor,bounds))for(var block:shape.toAabbs())
            if(block.maxY>actor.getY()+.1)out.add(block);
        return out;
    }
    /** A rooted idle can also put its horn tips into a newly built wall. A small
     * backwards neck bend keeps recovery/idle clear without moving the root or
     * teleporting to an unrelated pose. Transport choreography is not modified. */
    public static float[][] restPose(net.minecraft.world.entity.Entity actor,float[][] pose,float yaw,float size){
        var raw=body(pose,actor.position(),yaw,size);AABB bounds=raw.get(0).bounds;
        for(var b:raw)bounds=bounds.minmax(b.bounds);
        var blocks=terrain(actor,bounds.inflate(.025));
        if(!overlaps(raw,blocks))return pose;
        // Include the entire possible backwards bend, so escaping one wall cannot
        // bend through another. Quarter samples bound the modest 35-degree arc.
        for(float a=.15F;a<=.6001F;a+=.15F)for(var b:body(bent(pose,a),actor.position(),yaw,size))bounds=bounds.minmax(b.bounds);
        return restPose(pose,actor.position(),yaw,size,terrain(actor,bounds.inflate(size*.5)));
    }
    public static float[][] restPose(float[][] pose,Vec3 origin,float yaw,float size,List<AABB> terrain){
        if(!overlaps(body(pose,origin,yaw,size),terrain))return pose;
        float blocked=0;
        for(float clear=.025F;clear<=.6001F;clear+=.025F){
            if(!overlaps(body(bent(pose,clear),origin,yaw,size),terrain)){
                for(int i=0;i<8;i++){float mid=(blocked+clear)*.5F;if(overlaps(body(bent(pose,mid),origin,yaw,size),terrain))blocked=mid;else clear=mid;}
                return bent(pose,clear);
            }blocked=clear;
        }
        return pose;
    }
    private static boolean overlaps(List<Box> body,List<AABB> terrain){
        for(var b:body)for(var block:terrain)if(b.touches(block.inflate(.015)))return true;return false;
    }
    private static float[][] bent(float[][] pose,float angle){
        var out=pose.clone();int neck=Arrays.asList(HydraMotion.INSTANCE.bones).indexOf("neck_0");out[neck]=out[neck].clone();out[neck][0]+=angle;return out;
    }
    public static List<Sample> sweep(int action,float before,float now,Vec3 origin,float beforeYaw,float yaw,float size){
        var result=new ArrayList<Sample>();var spec=HydraCombatAction.get(action);if(spec==null)return result;
        for(var w:spec.windows){if(w.wave()||!w.active(before,now))continue;float from=Math.max(before,w.from()),to=Math.min(now,w.to());
            int count=Math.max(1,(int)Math.ceil((to-from)*16));
            for(int i=0;i<=count;i++){float t=from+(to-from)*i/count,f=now==before?1:(t-before)/(now-before);
                float angle=beforeYaw+net.minecraft.util.Mth.wrapDegrees(yaw-beforeYaw)*f;
                for(var b:boxes(action,t,w.joint(),origin,angle,size))result.add(new Sample(t,w,b));
            }
        }return result;
    }
    public static boolean canReach(int action,Vec3 origin,float yaw,float size,AABB target){
        if(!Float.isFinite(size)||size<=0)return false;
        // Cached local envelope is an AI preview only. Authoritative contact below
        // still sweeps exact oriented cuboids and performs terrain occlusion.
        AABB local=null;double angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
        for(double x:new double[]{target.minX,target.maxX})for(double y:new double[]{target.minY,target.maxY})for(double z:new double[]{target.minZ,target.maxZ}){
            double dx=x-origin.x,dz=z-origin.z;Vec3 p=new Vec3((dx*c+dz*s)/size,(y-origin.y)/size,(-dx*s+dz*c)/size);
            local=local==null?new AABB(p,p):local.minmax(new AABB(p,p));
        }
        for(var b:Reach.CACHE.getOrDefault(action,List.of()))if(b.touches(local))return true;
        return false;
    }
    private static final class Reach {
        static final Map<Integer,List<Box>> CACHE=build();
        private static Map<Integer,List<Box>> build(){var result=new HashMap<Integer,List<Box>>();
            for(int action:new int[]{3,9,10}){var out=new ArrayList<Box>();var seen=new HashSet<String>();
                for(var w:HydraCombatAction.get(action).windows){if(w.wave()||!seen.add(w.joint()))continue;
                    for(float t=w.from();t<=w.to();t+=.5F)out.addAll(boxes(action,t,w.joint(),Vec3.ZERO,0,1));
                }result.put(action,List.copyOf(out));
            }return Map.copyOf(result);
        }
    }
    private HydraContact(){}
}
