package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The two visible sharp digits on each authored arm; no forward damage cone. */
public final class StalkerContact {
    public record Window(int id,float start,float end,int hands) {}
    public record Claw(String bone,int digit,HydraContact.Box box) {}
    public record Hit(Vec3 point,String bone,int digit,float age) {}
    private record Bone(String name,String parent,Vec3 pivot) {}
    private record Cube(String bone,int digit,Vec3 center,Vec3 half) {}
    private record Rig(List<Bone> bones,List<Cube> cubes) {}
    private static final Rig RIG=load();
    public static List<Window> windows(MutantMove move){return switch(move){
        case STALKER_CLAW->List.of(new Window(0,11,15,1));
        case STALKER_DOUBLE->List.of(new Window(0,9,13,1),new Window(1,23,27,2));
        case STALKER_POUNCE->List.of(new Window(0,14,22,3));
        default->List.of();
    };}
    public static Window active(MutantMove move,float before,float age){for(var w:windows(move))if(age>=w.start&&before<=w.end)return w;return null;}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static Rig load(){
        try(var in=Objects.requireNonNull(StalkerContact.class.getResourceAsStream("/assets/blacklight/geo/blacklight_stalker_mc.geo.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var bones=new ArrayList<Bone>();var cubes=new ArrayList<Cube>();
            for(var entry:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var b=entry.getAsJsonObject();String name=b.get("name").getAsString();
                if(b.has("rotation"))throw new IllegalStateException("Rest rotation requires contact update");
                bones.add(new Bone(name,b.has("parent")?b.get("parent").getAsString():null,vector(b.getAsJsonArray("pivot")).multiply(-1,1,1).scale(1/16.0)));
                if(!name.equals("right_arm")&&!name.equals("left_arm"))continue;
                var parts=b.getAsJsonArray("cubes");if(parts.size()!=4)throw new IllegalStateException("Changed stalker claw layout");
                for(int digit=0;digit<2;digit++){var c=parts.get(digit+2).getAsJsonObject();var size=vector(c.getAsJsonArray("size"));
                    if(c.has("rotation")||c.has("inflate")||size.x>1.5||size.y>2||size.z>4)throw new IllegalStateException("Changed sharp digit geometry");
                    var center=vector(c.getAsJsonArray("origin")).add(size.scale(.5)).multiply(-1,1,1).scale(1/16.0);
                    cubes.add(new Cube(name,digit,center,size.scale(1/32.0)));
                }
            }
            if(cubes.size()!=4)throw new IllegalStateException("Both claws required");return new Rig(List.copyOf(bones),List.copyOf(cubes));
        }catch(Exception e){throw new IllegalStateException("Invalid stalker contact mesh",e);}
    }
    public static List<Claw> claws(MutantMove move,float age,Vec3 origin,float yaw){
        var table=CreaturePoseTable.STALKER;var pose=table.sample(move.clip,age);var names=Arrays.asList(table.bones);var matrices=new HashMap<String,Matrix4f>();
        for(var bone:RIG.bones){var m=bone.parent==null?new Matrix4f().rotateY((float)Math.toRadians(180-yaw)):new Matrix4f(Objects.requireNonNull(matrices.get(bone.parent)));int i=names.indexOf(bone.name);
            if(i>=0){var p=bone.pivot;var v=pose[i];m.translate((float)p.x+v[3]/16,(float)p.y+v[4]/16,(float)p.z+v[5]/16).rotateZYX(v[2],v[1],v[0]).scale(v[6],v[7],v[8]).translate((float)-p.x,(float)-p.y,(float)-p.z);}matrices.put(bone.name,m);
        }
        var result=new ArrayList<Claw>(4);
        for(var c:RIG.cubes){var m=matrices.get(c.bone);var p=m.transformPosition(c.center.toVector3f());var axis=new Vec3[3];double[] scale=new double[3];
            for(int i=0;i<3;i++){var v=m.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0));scale[i]=v.length();v.normalize();axis[i]=new Vec3(v.x,v.y,v.z);}
            var half=c.half.multiply(scale[0],scale[1],scale[2]);var box=HydraContact.oriented(origin.add(p.x,p.y+.01,p.z),half,axis[0],axis[1],axis[2]);result.add(new Claw(c.bone,c.digit,box));
        }return List.copyOf(result);
    }
    public static boolean owns(Window w,String bone){return (w.hands&(bone.equals("right_arm")?1:2))!=0;}
    public static Hit contact(MutantMove move,Window window,float before,float now,Vec3 from,Vec3 to,float yaw,AABB target){
        float lo=Math.max(before,window.start),hi=Math.min(now,window.end);if(lo>hi)return null;
        // Subdivide both time and body travel so a fast claw cannot skip a thin target.
        int steps=Math.max(8,Math.min(48,(int)Math.ceil(from.distanceTo(to)/.025)));
        for(int step=0;step<=steps;step++){float age=lo+(hi-lo)*step/steps;double weight=now<=before?1:(age-before)/(now-before);var origin=from.lerp(to,Math.max(0,Math.min(1,weight)));
            for(var claw:claws(move,age,origin,yaw))if(owns(window,claw.bone)&&claw.box.touches(target))return new Hit(claw.box.contact(target),claw.bone,claw.digit,age);
        }return null;
    }
    public static boolean canReach(MutantMove move,Vec3 origin,float yaw,AABB target){
        double radians=Math.toRadians(yaw),cos=Math.cos(radians),sin=Math.sin(radians);var p=target.getCenter().subtract(origin);
        var local=new Vec3(p.x*cos+p.z*sin,p.y,p.z*cos-p.x*sin);
        var box=HydraContact.oriented(local,new Vec3(target.getXsize()/2,target.getYsize()/2,target.getZsize()/2),new Vec3(cos,0,-sin),new Vec3(0,1,0),new Vec3(sin,0,cos));
        for(var claw:Reach.samples.getOrDefault(move,List.of()))if(claw.touches(box))return true;return false;
    }
    private static final class Reach {
        static final Map<MutantMove,List<HydraContact.Box>> samples=build();
        private static Map<MutantMove,List<HydraContact.Box>> build(){var result=new EnumMap<MutantMove,List<HydraContact.Box>>(MutantMove.class);
            for(var move:new MutantMove[]{MutantMove.STALKER_CLAW,MutantMove.STALKER_DOUBLE}){var boxes=new ArrayList<HydraContact.Box>();for(var w:windows(move))for(float t=w.start;t<=w.end;t+=.25F)for(var c:claws(move,t,Vec3.ZERO,0))if(owns(w,c.bone))boxes.add(c.box);result.put(move,List.copyOf(boxes));}return result;
        }
    }
    private StalkerContact(){}
}
