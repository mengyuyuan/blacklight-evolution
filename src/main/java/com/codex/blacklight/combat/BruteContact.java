package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Visible fist, shoulder and chest cuboids on the authored Brute rig. */
public final class BruteContact {
    private record Bone(String name,String parent,Vec3 pivot){}
    private record Cube(String bone,int index,Vec3 center,Vec3 half){}
    private record Rig(List<Bone> bones,List<Cube> cubes){}
    public record Surface(String bone,int cube,HydraContact.Box box){
        public boolean fist(){return bone.endsWith("_forearm");}
    }
    public record Hit(String bone,int cube,Vec3 point,float age){}
    private static final Rig RIG=load();
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static Rig load(){
        try(var stream=Objects.requireNonNull(BruteContact.class.getResourceAsStream("/assets/blacklight/geo/blacklight_brute_mc.geo.json"));var r=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            var bones=new ArrayList<Bone>();var cubes=new ArrayList<Cube>();
            for(var item:JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var b=item.getAsJsonObject();String name=b.get("name").getAsString();
                if(b.has("rotation"))throw new IllegalStateException("Brute rest rotation requires contact update");
                bones.add(new Bone(name,b.has("parent")?b.get("parent").getAsString():null,vector(b.getAsJsonArray("pivot")).multiply(-1,1,1).scale(1/16.0)));
                var parts=b.getAsJsonArray("cubes");if(parts==null||parts.isEmpty())continue;
                for(int i=0;i<parts.size();i++){var c=parts.get(i).getAsJsonObject();if(c.has("rotation")||c.has("inflate"))throw new IllegalStateException("Transformed brute cube");var size=vector(c.getAsJsonArray("size"));
                    cubes.add(new Cube(name,i,vector(c.getAsJsonArray("origin")).add(size.scale(.5)).multiply(-1,1,1).scale(1/16.0),size.scale(1/32.0)));
                }
            }return new Rig(List.copyOf(bones),List.copyOf(cubes));
        }catch(Exception e){throw new IllegalStateException("Invalid Brute contact rig",e);}
    }
    public static List<Surface> body(MutantMove move,float age,Vec3 origin,float yaw){
        return body(CreaturePoseTable.BRUTE.sample(move.clip,age),origin,yaw);
    }
    public static List<Surface> body(float[][] pose,Vec3 origin,float yaw){
        return surfaces(pose,origin,yaw,false);
    }
    /** Whole skin support for a get-up, including legs which never authorize charge damage. */
    public static List<Surface> wholeBody(float[][] pose,Vec3 origin,float yaw){return surfaces(pose,origin,yaw,true);}
    private static Map<String,Matrix4f> matrices(float[][] pose,float yaw){
        var names=Arrays.asList(CreaturePoseTable.BRUTE.bones);var matrices=new HashMap<String,Matrix4f>();
        for(var bone:RIG.bones){var m=bone.parent==null?new Matrix4f().rotateY((float)Math.toRadians(180-yaw)):new Matrix4f(Objects.requireNonNull(matrices.get(bone.parent)));int i=names.indexOf(bone.name);
            if(i>=0){var p=bone.pivot;var v=pose[i];m.translate((float)p.x-v[3]/16,(float)p.y+v[4]/16,(float)p.z+v[5]/16).rotateZYX(v[2],v[1],v[0]).scale(v[6],v[7],v[8]).translate((float)-p.x,(float)-p.y,(float)-p.z);}matrices.put(bone.name,m);
        }
        return matrices;
    }
    /** Actual animated joint pivot in the local frame used by wholeBody. */
    public static Vec3 pivot(float[][] pose,String name){
        var bone=RIG.bones.stream().filter(b->b.name.equals(name)).findFirst().orElseThrow();
        var p=matrices(pose,180).get(name).transformPosition(bone.pivot.toVector3f());
        return new Vec3(p.x,p.y+.01,p.z);
    }
    /** Exact cuboid bottom for one support limb, in the same local frame as wholeBody. */
    public static double lowest(float[][] pose,String bone){
        var m=matrices(pose,180).get(bone);if(m==null)throw new IllegalArgumentException(bone);
        double low=Double.POSITIVE_INFINITY;
        for(var c:RIG.cubes)if(c.bone.equals(bone)){
            var p=m.transformPosition(c.center.toVector3f());
            double extent=Math.abs(m.m01())*c.half.x+Math.abs(m.m11())*c.half.y+Math.abs(m.m21())*c.half.z;
            low=Math.min(low,p.y+.01-extent);
        }
        return low;
    }
    private static List<Surface> surfaces(float[][] pose,Vec3 origin,float yaw,boolean whole){
        var matrices=matrices(pose,yaw);
        var result=new ArrayList<Surface>();for(var c:RIG.cubes){
            if(!whole&&!c.bone.equals("torso")&&!c.bone.equals("head")&&!c.bone.endsWith("_arm")&&!c.bone.endsWith("_forearm"))continue;
            var m=matrices.get(c.bone);var p=m.transformPosition(c.center.toVector3f());var axes=new Vec3[3];var lengths=new double[3];
            for(int i=0;i<3;i++){var axis=m.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0));lengths[i]=axis.length();axis.normalize();axes[i]=new Vec3(axis.x,axis.y,axis.z);}
            result.add(new Surface(c.bone,c.index,HydraContact.oriented(origin.add(p.x,p.y+.01,p.z),c.half.multiply(lengths[0],lengths[1],lengths[2]),axes[0],axes[1],axes[2])));
        }return List.copyOf(result);
    }
    public static boolean active(MutantMove move,float age){return switch(move){case BRUTE_HAMMER->age>=16&&age<=21;case BRUTE_SLAM->age>=20&&age<=28;case BRUTE_CHARGE->age>=12&&age<=26;default->false;};}
    public static boolean owns(MutantMove move,Surface s){return switch(move){case BRUTE_HAMMER->s.fist()&&s.bone.equals("right_forearm");case BRUTE_SLAM->s.fist();case BRUTE_CHARGE->true;default->false;};}
    public static Hit contact(MutantMove move,float before,float now,Vec3 from,Vec3 to,float yaw,AABB target){
        int steps=Math.max(8,Math.min(48,(int)Math.ceil(from.distanceTo(to)/.025)));
        for(int i=0;i<=steps;i++){float age=before+(now-before)*i/steps;if(!active(move,age))continue;var origin=from.lerp(to,(double)i/steps);
            for(var s:body(move,age,origin,yaw))if(owns(move,s)&&s.box.touches(target))return new Hit(s.bone,s.cube,s.box.contact(target),age);
        }return null;
    }
    public static boolean canReach(MutantMove move,Vec3 origin,float yaw,AABB target){
        double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);var p=target.getCenter().subtract(origin);
        var box=HydraContact.oriented(new Vec3(p.x*c+p.z*s,p.y,p.z*c-p.x*s),new Vec3(target.getXsize()/2,target.getYsize()/2,target.getZsize()/2),new Vec3(c,0,-s),new Vec3(0,1,0),new Vec3(s,0,c));
        for(var sample:Reach.samples.getOrDefault(move,List.of()))if(sample.touches(box))return true;return false;
    }
    private static final class Reach {
        static final Map<MutantMove,List<HydraContact.Box>> samples=build();
        private static Map<MutantMove,List<HydraContact.Box>> build(){var result=new EnumMap<MutantMove,List<HydraContact.Box>>(MutantMove.class);
            for(var move:new MutantMove[]{MutantMove.BRUTE_HAMMER,MutantMove.BRUTE_SLAM}){var boxes=new ArrayList<HydraContact.Box>();for(float t=0;t<=move.ticks;t+=.25F)if(active(move,t))for(var s:body(move,t,Vec3.ZERO,0))if(owns(move,s))boxes.add(s.box);result.put(move,List.copyOf(boxes));}return result;
        }
    }
    public static List<HydraContact.Box> boxes(MutantMove move,float age,Vec3 origin,float yaw,boolean fistsOnly){return body(move,age,origin,yaw).stream().filter(s->!fistsOnly||s.fist()).map(Surface::box).toList();}
    private BruteContact(){}
}
