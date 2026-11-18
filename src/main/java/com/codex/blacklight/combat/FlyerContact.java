package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Rotated authored cubes, including wing membranes. No inflated attack cone. */
public final class FlyerContact {
    private record Cube(int bone,Vec3 center,Vec3 half,Matrix4f local){}
    public record Surface(String bone,HydraContact.Box box){}
    private static final List<Cube> CUBES=load();
    private static Vec3 vector(JsonObject o,String key){var a=o.getAsJsonArray(key);return a==null?Vec3.ZERO:new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static List<Cube> load(){
        try(var in=Objects.requireNonNull(FlyerContact.class.getResourceAsStream("/assets/blacklight/geo/flyer_mc.geo.json"));var r=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var result=new ArrayList<Cube>();var names=Arrays.asList(FlyerMotion.table().bones);
            for(var b:JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var bone=b.getAsJsonObject();int index=names.indexOf(bone.get("name").getAsString());
                if(!bone.has("cubes"))continue;
                for(var c:bone.getAsJsonArray("cubes")){if(index<0)throw new IllegalStateException("Unmapped flyer cube");var cube=c.getAsJsonObject();
                    var o=vector(cube,"origin");var s=vector(cube,"size");var p=vector(cube,"pivot").multiply(-1,1,1).scale(1/16.0);var v=vector(cube,"rotation");
                    if(cube.has("inflate")&&cube.get("inflate").getAsFloat()!=0)throw new IllegalStateException("Inflated flyer cube requires contact update");
                    var local=new Matrix4f().translate((float)p.x,(float)p.y,(float)p.z)
                        .rotateZYX((float)Math.toRadians(v.z),(float)Math.toRadians(-v.y),(float)Math.toRadians(-v.x)).translate((float)-p.x,(float)-p.y,(float)-p.z);
                    result.add(new Cube(index,o.add(s.scale(.5)).multiply(-1,1,1).scale(1/16.0),s.scale(1/32.0),local));
                }
            }return List.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Invalid flyer contact mesh",e);}
    }
    public static List<Surface> body(float[][] pose,Vec3 origin,float yaw){
        var table=FlyerMotion.table();var transforms=table.transforms(pose);var result=new ArrayList<Surface>(CUBES.size());
        var base=new Matrix4f().rotateY((float)Math.toRadians(180-yaw));
        for(var c:CUBES){var m=new Matrix4f(base).mul(transforms[c.bone]).mul(c.local);var center=m.transformPosition(c.center.toVector3f());var axes=new Vec3[3];
            for(int i=0;i<3;i++){var a=m.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0)).normalize();axes[i]=new Vec3(a.x,a.y,a.z);}
            result.add(new Surface(table.bones[c.bone],HydraContact.oriented(origin.add(center.x,center.y+.01,center.z),c.half,axes[0],axes[1],axes[2])));
        }return result;
    }
    public static List<String> regionBones(){return CUBES.stream().map(c->FlyerMotion.table().bones[c.bone]).distinct().toList();}
    public record BodyMass(int bone,Vec3 center,double volume,double radius){}
    public static List<BodyMass> masses(){
        var result=new ArrayList<BodyMass>();
        for(int bone=0;bone<FlyerMotion.table().bones.length;bone++){
            double volume=0;Vec3 center=Vec3.ZERO;
            for(var c:CUBES)if(c.bone==bone){double v=8*c.half.x*c.half.y*c.half.z;var p=c.local.transformPosition(c.center.toVector3f());center=center.add(new Vec3(p.x,p.y,p.z).scale(v));volume+=v;}
            if(volume<=0)continue;center=center.scale(1/volume);double radius=.1;
            for(var c:CUBES)if(c.bone==bone){var p=c.local.transformPosition(c.center.toVector3f());radius=Math.max(radius,new Vec3(p.x,p.y,p.z).distanceTo(center)+c.half.length());}
            result.add(new BodyMass(bone,center,volume,radius));
        }return List.copyOf(result);
    }
    /** World transforms supplied by the authoritative articulated body. */
    public static List<HydraContact.Box> worldBoxes(int bone,Matrix4f transform){
        var result=new ArrayList<HydraContact.Box>();
        for(var c:CUBES)if(c.bone==bone){var m=new Matrix4f(transform).mul(c.local);var p=m.transformPosition(c.center.toVector3f());var axes=new Vec3[3];
            for(int i=0;i<3;i++){var a=m.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0)).normalize();axes[i]=new Vec3(a.x,a.y,a.z);}
            result.add(HydraContact.oriented(new Vec3(p.x,p.y,p.z),c.half,axes[0],axes[1],axes[2]));
        }return result;
    }
    public static List<HydraContact.Region> regions(float[][] pose,Vec3 origin,float yaw){
        var grouped=new LinkedHashMap<String,List<HydraContact.Box>>();
        for(var surface:body(pose,origin,yaw))grouped.computeIfAbsent(surface.bone(),k->new ArrayList<>()).add(surface.box());
        var result=new ArrayList<HydraContact.Region>();
        grouped.forEach((bone,boxes)->{AABB bounds=boxes.get(0).bounds;for(var b:boxes)bounds=bounds.minmax(b.bounds);result.add(new HydraContact.Region(bone,List.copyOf(boxes),bounds));});
        return List.copyOf(result);
    }
    public static float[][] blend(float[][] a,float[][] b,float weight){var out=new float[a.length][];for(int i=0;i<a.length;i++)out[i]=SourceBossMotion.blend(a[i],b[i],weight);return out;}
    private FlyerContact(){}
}
