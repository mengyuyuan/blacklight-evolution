package com.codex.blacklight.client.renderer;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The nest mesh and both hinge animations are baked directly from the editable cube model. */
final class GreeneNestModel {
    private static final String BASE="/assets/blacklight/";
    private static final float RAD=(float)(Math.PI/180);
    private record Corner(Vec3 position,Vec3 normal,float u,float v){}
    private record Track(float[] times,Vec3[] values){
        Vec3 at(float t){
            int index=Arrays.binarySearch(times,t);
            if(index>=0)return values[index];
            int hi=-1-index;
            if(hi==0)return values[0];
            if(hi==times.length)return values[hi-1];
            return values[hi-1].lerp(values[hi],(t-times[hi-1])/(times[hi]-times[hi-1]));
        }
    }
    private record Pose(Track rotation,Track translation){}
    private record Bone(Vec3 pivot,Corner[] mesh,Pose idle,Pose hatch,int root,Vec3 tip){}

    private static JsonObject document(String path){
        try(var in=new InputStreamReader(Objects.requireNonNull(GreeneNestModel.class.getResourceAsStream(BASE+path)),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(in).getAsJsonObject();
        }catch(Exception e){throw new IllegalStateException(path,e);}
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    private static Track track(JsonObject frames){
        var sorted=new TreeMap<Float,Vec3>();
        frames.entrySet().forEach(e->sorted.put(Float.parseFloat(e.getKey())*20,vector(e.getValue().getAsJsonArray())));
        float[] times=new float[sorted.size()];Vec3[] values=new Vec3[sorted.size()];int i=0;
        for(var e:sorted.entrySet()){times[i]=e.getKey();values[i++]=e.getValue();}
        return new Track(times,values);
    }
    private static Pose movement(JsonObject clip,String name){
        if(!clip.has(name))return null;
        var bone=clip.getAsJsonObject(name);
        return new Pose(track(bone.getAsJsonObject("rotation")),track(bone.getAsJsonObject("position")));
    }
    private static final String[] FACES={"north","south","west","east","up","down"};
    private static final int[][] CORNERS={{3,1,0,2},{6,4,5,7},{2,0,4,6},{7,5,1,3},{2,6,7,3},{4,0,1,5}};
    private static final Vec3[] NORMALS={new Vec3(0,0,-1),new Vec3(0,0,1),new Vec3(-1,0,0),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0)};
    private static Bone[] bake(){
        var geo=document("geo/block/greene_wall_nest.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var desc=geo.getAsJsonObject("description");float width=desc.get("texture_width").getAsFloat(),height=desc.get("texture_height").getAsFloat();
        var clips=document("animations/block/greene_wall_nest.animation.json").getAsJsonObject("animations");
        var idle=clips.getAsJsonObject("animation.greene_wall_nest.idle").getAsJsonObject("bones");
        var hatch=clips.getAsJsonObject("animation.greene_wall_nest.hatch").getAsJsonObject("bones");
        var result=new ArrayList<Bone>();
        for(var item:geo.getAsJsonArray("bones")){
            var bone=item.getAsJsonObject();var name=bone.get("name").getAsString();var mesh=new ArrayList<Corner>();
            var pivot=vector(bone.getAsJsonArray("pivot")).scale(1D/16);var tip=pivot;
            for(var cube:bone.getAsJsonArray("cubes")){
                var c=cube.getAsJsonObject();var from=vector(c.getAsJsonArray("origin"));var size=vector(c.getAsJsonArray("size"));
                var center=vector(c.getAsJsonArray("pivot"));var rot=vector(c.getAsJsonArray("rotation")).scale(RAD);
                var q=new Quaternionf().rotateZYX((float)rot.z,(float)rot.y,(float)rot.x);
                var corners=new Vec3[8];
                for(int i=0;i<8;i++){
                    var point=from.add((i&1)!=0?size.x:0,(i&2)!=0?size.y:0,(i&4)!=0?size.z:0);
                    corners[i]=center.add(new Vec3(q.transform(point.subtract(center).toVector3f()))).scale(1D/16);
                    if(corners[i].distanceToSqr(pivot)>tip.distanceToSqr(pivot))tip=corners[i];
                }
                var faces=c.getAsJsonObject("uv");
                for(int f=0;f<6;f++){
                    var face=faces.getAsJsonObject(FACES[f]);var uv=face.getAsJsonArray("uv");var span=face.getAsJsonArray("uv_size");
                    var normal=new Vec3(q.transform(NORMALS[f].toVector3f()));
                    for(int j=0;j<4;j++){
                        float u=(uv.get(0).getAsFloat()+(j>=2?span.get(0).getAsFloat()-.015F:.015F))/width;
                        float v=(uv.get(1).getAsFloat()+(j==1 || j==2?span.get(1).getAsFloat()-.015F:.015F))/height;
                        mesh.add(new Corner(corners[CORNERS[f][j]],normal,u,v));
                    }
                }
            }
            int root=name.startsWith("root_")?Integer.parseInt(name.substring(5)):-1;
            result.add(new Bone(pivot,mesh.toArray(Corner[]::new),movement(idle,name),movement(hatch,name),root,tip));
        }
        return result.toArray(Bone[]::new);
    }
    private static final Bone[] MODEL=bake();
    interface Wall {float angle(int root,Vec3 hinge,Vec3 tip);}
    static void draw(PoseStack stack,VertexConsumer out,int light,float idleTime,float hatchTime,Wall wall){
        for(var bone:MODEL){
            stack.pushPose();
            if(bone.idle!=null){
                float blend=hatchTime>=0?Math.min(Mth.clamp(hatchTime/4,0,1),Mth.clamp((36-hatchTime)/5,0,1)):0;
                var r=bone.idle.rotation.at(idleTime);var t=bone.idle.translation.at(idleTime);
                if(blend>0){
                    r=r.lerp(bone.hatch.rotation.at(hatchTime),blend);
                    t=t.lerp(bone.hatch.translation.at(hatchTime),blend);
                }
                var p=bone.pivot;
                stack.translate(p.x+t.x/16,p.y+t.y/16,p.z+t.z/16);
                stack.mulPose(new Quaternionf().rotateZYX((float)r.z*RAD,(float)r.y*RAD,(float)r.x*RAD));
                stack.translate(-p.x,-p.y,-p.z);
            }else if(bone.root>=0){
                var p=bone.pivot;var d=bone.tip.subtract(p);
                float angle=wall.angle(bone.root,p,bone.tip);
                if(Math.abs(angle)>.004F){
                    var axis=new Vector3f((float)d.y,(float)-d.x,0).normalize();
                    stack.translate(p.x,p.y,p.z);stack.mulPose(new Quaternionf().fromAxisAngleRad(axis,angle));stack.translate(-p.x,-p.y,-p.z);
                }
            }
            for(var corner:bone.mesh)ConsumedMobVisuals.vertex(stack,out,light,corner.position,corner.normal,0xffffffff,corner.u,corner.v);
            stack.popPose();
        }
    }
}
