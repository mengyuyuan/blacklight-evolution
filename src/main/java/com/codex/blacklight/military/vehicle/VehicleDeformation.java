package com.codex.blacklight.military.vehicle;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Bounded, persistent plastic dents in the authored bone's rest coordinates. */
public final class VehicleDeformation {
    public static final int LIMIT=16;
    public record Dent(String bone,Vec3 point,Vec3 inward,double radius,double depth) {
        public Dent {
            if(bone==null||!bone.matches("[a-z0-9_]{1,64}")||!finite(point)||point.lengthSqr()>1024
                    ||!finite(inward)||inward.lengthSqr()<.9||inward.lengthSqr()>1.1
                    ||!Double.isFinite(radius)||radius<.2||radius>1.5||!Double.isFinite(depth)||depth<=0||depth>.6||depth>radius*.55)
                throw new IllegalArgumentException("Invalid vehicle dent");
            inward=inward.normalize();
        }
    }
    private record Bone(String parent,Vec3 pivot) {}
    private record Skin(String bone,Vec3 low,Vec3 high) {}
    private record Geometry(Map<String,Bone> bones,List<Skin> skin) {}
    private static final Map<String,Geometry> MODELS=load();
    private VehicleDeformation(){}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.lengthSqr());}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()).scale(1/16.0);}
    private static Map<String,Geometry> load(){
        try(var in=VehicleDeformation.class.getResourceAsStream("/assets/blacklight/military/vehicle-models.json")){
            if(in==null)throw new IllegalStateException("Missing vehicle skin");
            var models=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("chassis");
            var result=new HashMap<String,Geometry>();
            for(var entry:models.entrySet()){
                var asset=entry.getValue().getAsJsonObject();var bones=new LinkedHashMap<String,Bone>();var skin=new ArrayList<Skin>();
                for(var raw:asset.getAsJsonArray("bones")){
                    var b=raw.getAsJsonObject();String name=b.get("name").getAsString();
                    bones.put(name,new Bone(name.equals("root")?null:b.get("parent").getAsString(),vector(b.getAsJsonArray("pivot"))));
                }
                for(var raw:asset.getAsJsonArray("parts")){
                    var p=raw.getAsJsonObject();String bone=p.get("parent").getAsString();
                    // Running gear keeps its contact geometry; no dent may stretch across an articulated joint.
                    if(bone.contains("wheel")||bone.startsWith("track_"))continue;
                    var b=p.getAsJsonArray("box");var lo=vector(b);
                    skin.add(new Skin(bone,lo,lo.add(b.get(3).getAsDouble()/16,b.get(4).getAsDouble()/16,b.get(5).getAsDouble()/16)));
                }
                result.put(entry.getKey(),new Geometry(Map.copyOf(bones),List.copyOf(skin)));
            }
            return Map.copyOf(result);
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot read vehicle skin",ex);}
    }
    private static Vec3 rotate(Vec3 p,String bone,double yaw,double pitch,boolean inverse){
        double angle=Math.toRadians((inverse?-1:1)*(bone.equals("turret_yaw")?yaw:pitch)),c=Math.cos(angle),s=Math.sin(angle);
        if(bone.equals("turret_yaw"))return new Vec3(p.x*c+p.z*s,p.y,p.z*c-p.x*s);
        if(bone.equals("gun_pitch"))return new Vec3(p.x,p.y*c-p.z*s,p.y*s+p.z*c);
        return p;
    }
    public static Vec3 toBody(String chassis,String bone,Vec3 p,double yaw,double pitch){
        var geometry=MODELS.get(chassis);
        for(String current=bone;current!=null;current=geometry.bones.get(current).parent){
            p=rotate(p,current,yaw,pitch,false).add(geometry.bones.get(current).pivot);
        }
        return p;
    }
    public static Vec3 toBone(String chassis,String bone,Vec3 p,double yaw,double pitch){
        var geometry=MODELS.get(chassis);var chain=new ArrayDeque<String>();
        for(String current=bone;current!=null;current=geometry.bones.get(current).parent)chain.addFirst(current);
        for(String current:chain)p=rotate(p.subtract(geometry.bones.get(current).pivot),current,yaw,pitch,true);
        return p;
    }
    /** Projects the accepted mechanical contact onto the nearest authored skin, including aimed turrets. */
    public static Dent contact(String chassis,Vec3 bodyPoint,Vec3 push,double yaw,double pitch,double damage,double maxHealth){
        if(!finite(bodyPoint)||!finite(push)||push.lengthSqr()<1e-8||!Double.isFinite(damage)||damage<2||!Double.isFinite(maxHealth)||maxHealth<=0)return null;
        Skin best=null,rayBest=null;Vec3 center=null,normal=null,rayCenter=null,rayNormal=null;double score=Double.POSITIVE_INFINITY,rayScore=Double.POSITIVE_INFINITY;
        for(var skin:MODELS.get(chassis).skin){
            Vec3 p=toBone(chassis,skin.bone,bodyPoint,yaw,pitch);
            Vec3 direction=toBone(chassis,skin.bone,bodyPoint.add(push.normalize()),yaw,pitch).subtract(p).normalize();
            // Search exposed faces, rather than moving an interior point to an arbitrary nearest edge.
            for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){
                Vec3 n=axis==0?new Vec3(side==0?1:-1,0,0):axis==1?new Vec3(0,side==0?1:-1,0):new Vec3(0,0,side==0?1:-1);
                if(n.dot(direction)<.15)continue;
                double x=Math.clamp(p.x,skin.low.x,skin.high.x),y=Math.clamp(p.y,skin.low.y,skin.high.y),z=Math.clamp(p.z,skin.low.z,skin.high.z);
                if(axis==0)x=side==0?skin.low.x:skin.high.x;
                if(axis==1)y=side==0?skin.low.y:skin.high.y;
                if(axis==2)z=side==0?skin.low.z:skin.high.z;
                var q=new Vec3(x,y,z);double distance=q.distanceToSqr(p);
                if(distance<score){score=distance;best=skin;center=q;normal=n;}
                // The mechanical body OBB deliberately encloses empty space around the turret.
                // Prefer the first actual model face along the attack, not a closer panel below the ray.
                Vec3 start=p.subtract(direction.scale(1.5));double t=q.subtract(start).dot(n)/direction.dot(n);
                Vec3 crossing=start.add(direction.scale(t));
                if(t>=0&&t<=3.5&&t<rayScore&&crossing.x>=skin.low.x-1e-6&&crossing.x<=skin.high.x+1e-6
                        &&crossing.y>=skin.low.y-1e-6&&crossing.y<=skin.high.y+1e-6&&crossing.z>=skin.low.z-1e-6&&crossing.z<=skin.high.z+1e-6){
                    rayScore=t;rayBest=skin;rayCenter=crossing;rayNormal=n;
                }
            }
        }
        if(rayBest!=null){best=rayBest;center=rayCenter;normal=rayNormal;}
        else if(best==null||score>1.5*1.5)return null;
        double severity=Math.clamp(damage/maxHealth,0,1);
        return new Dent(best.bone,center,normal,.45+Math.min(.8,Math.sqrt(severity)*2),Math.min(.5,.035+severity*2.5));
    }
    public static List<Dent> add(List<Dent> before,Dent hit){
        if(hit==null)return before;
        var result=new ArrayList<>(before);
        for(int i=0;i<result.size();i++){
            var d=result.get(i);
            if(d.bone.equals(hit.bone)&&d.inward.dot(hit.inward)>.85&&d.point.distanceTo(hit.point)<Math.min(d.radius,hit.radius)*.4){
                double radius=Math.max(d.radius,hit.radius);
                result.set(i,new Dent(d.bone,d.point.lerp(hit.point,.2),d.inward,radius,Math.min(Math.min(.6,radius*.55),d.depth+hit.depth*.65)));
                return List.copyOf(result);
            }
        }
        if(result.size()==LIMIT)result.remove(0);
        result.add(hit);return List.copyOf(result);
    }
    public static double weight(Dent d,Vec3 p){
        Vec3 delta=p.subtract(d.point);double axial=delta.dot(d.inward),r2=Math.max(0,delta.lengthSqr()-axial*axial)/(d.radius*d.radius);
        double thickness=d.radius*.8+.12,a=Math.abs(axial)/thickness;
        if(r2>=1||a>=1)return 0;
        return (1-r2)*(1-r2)*(1-a*a)*(1-a*a);
    }
    public static Vec3 offset(List<Dent> dents,Vec3 p){
        Vec3 shift=Vec3.ZERO;for(var d:dents)shift=shift.add(d.inward.scale(d.depth*weight(d,p)));
        double length=shift.length();return length>.6?shift.scale(.6/length):shift;
    }
    public static List<Dent> repair(List<Dent> dents,double fraction){
        if(fraction>=.999)return List.of();
        return dents.stream().filter(d->d.depth*(1-fraction)>.005).map(d->new Dent(d.bone,d.point,d.inward,d.radius,d.depth*(1-fraction))).toList();
    }
    public static ListTag write(List<Dent> dents){
        var list=new ListTag();for(var d:dents){var t=new CompoundTag();t.putString("Bone",d.bone);
            t.putDouble("X",d.point.x);t.putDouble("Y",d.point.y);t.putDouble("Z",d.point.z);
            t.putDouble("NX",d.inward.x);t.putDouble("NY",d.inward.y);t.putDouble("NZ",d.inward.z);
            t.putDouble("Radius",d.radius);t.putDouble("Depth",d.depth);list.add(t);}return list;
    }
    public static List<Dent> read(String chassis,ListTag list){
        var result=new ArrayList<Dent>();for(int i=0;i<Math.min(LIMIT,list.size());i++)try{
            var t=list.getCompound(i);String bone=t.getString("Bone");
            if(MODELS.get(chassis).skin.stream().noneMatch(s->s.bone.equals(bone)))continue;
            result.add(new Dent(bone,new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z")),new Vec3(t.getDouble("NX"),t.getDouble("NY"),t.getDouble("NZ")),t.getDouble("Radius"),t.getDouble("Depth")));
        }catch(IllegalArgumentException ignored){}return List.copyOf(result);
    }
}
