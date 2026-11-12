package com.codex.blacklight.animation;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Optional user-local motion data. No original game assets are included in the mod. */
public final class P1MilitaryMotion {
    public record Transform(Vector3d position,Quaterniond rotation) {}
    public record Frame(Map<String,Transform> joints,Map<String,Transform> reference) {
        public Transform joint(String name){return joints.get(name);}
        public Transform rest(String name){return reference.get(name);}
    }
    private record Curve(double[] times,double[][] values) {}
    private record Channels(Curve position,Curve rotation) {}
    private record Joint(String name,int parent,Vector3d position,Quaterniond rotation) {}
    private record Clip(double frames,double fps,boolean cyclic,Map<String,Channels> bones) {}
    private final List<Joint> joints;
    private final Map<String,Clip> clips;
    private final Map<String,Transform> reference;
    private P1MilitaryMotion(JsonObject root){
        if(root.get("schema").getAsInt()!=1||!root.get("sourceSha256").getAsString().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Unsupported motion profile");
        var list=new ArrayList<Joint>();var names=new HashSet<String>();
        var sourceJoints=root.getAsJsonArray("joints");if(sourceJoints.size()<1||sourceJoints.size()>128)throw new IllegalArgumentException("Joint count");
        for(var value:sourceJoints){var j=value.getAsJsonObject();String name=j.get("name").getAsString();int parent=j.get("parent").getAsInt();
            if(!names.add(name)||name.length()>80||parent<0||parent>=Math.max(1,list.size()))throw new IllegalArgumentException("Joint hierarchy");
            double[] p=vector(j.getAsJsonArray("position"),3),r=vector(j.getAsJsonArray("rotation"),9);
            var matrix=new Matrix3d(r[0],r[3],r[6],r[1],r[4],r[7],r[2],r[5],r[8]);
            if(Math.abs(matrix.determinant()-1)>.03)throw new IllegalArgumentException("Invalid bind rotation");
            list.add(new Joint(name,parent,new Vector3d(p),new Quaterniond().setFromNormalized(matrix).normalize()));
        }
        joints=List.copyOf(list);var map=new HashMap<String,Clip>();
        for(var entry:root.getAsJsonObject("clips").entrySet()){
            if(map.size()>=32)throw new IllegalArgumentException("Clip count");var c=entry.getValue().getAsJsonObject();
            double frames=c.get("frames").getAsDouble(),fps=c.get("fps").getAsDouble();
            if(!Double.isFinite(frames)||!Double.isFinite(fps)||frames<1||frames>2000||fps<1||fps>240)throw new IllegalArgumentException("Clip clock");
            var bones=new HashMap<String,Channels>();
            for(var bone:c.getAsJsonObject("bones").entrySet()){
                if(!names.contains(bone.getKey()))throw new IllegalArgumentException("Unknown joint");var channels=bone.getValue().getAsJsonObject();
                bones.put(bone.getKey(),new Channels(curve(channels,"position",3,frames),curve(channels,"rotation",4,frames)));
            }
            map.put(entry.getKey(),new Clip(frames,fps,c.get("cyclic").getAsBoolean(),Map.copyOf(bones)));
        }
        clips=Map.copyOf(map);
        for(String required:List.of("aim","fire","reload","reload_kneel","melee"))if(!clips.containsKey(required))throw new IllegalArgumentException("Missing "+required);
        for(String required:List.of("Motion_Root","Spine_3","Neck","Head","Wrist_R","Wrist_L"))if(!names.contains(required))throw new IllegalArgumentException("Missing "+required);
        reference=sampleWorld(clips.get("aim"),0);
    }
    private static double[] vector(JsonArray array,int count){
        if(array.size()!=count)throw new IllegalArgumentException("Vector size");double[] v=new double[count];
        for(int i=0;i<count;i++){v[i]=array.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>10000)throw new IllegalArgumentException("Vector range");}return v;
    }
    private static Curve curve(JsonObject channels,String name,int dimensions,double frames){
        if(!channels.has(name))return null;var c=channels.getAsJsonObject(name);var ts=c.getAsJsonArray("times");var vs=c.getAsJsonArray("values");
        if(ts.size()<1||ts.size()>2000||ts.size()!=vs.size())throw new IllegalArgumentException("Curve count");double[] times=new double[ts.size()];double[][] values=new double[ts.size()][];
        for(int i=0;i<times.length;i++){times[i]=ts.get(i).getAsDouble();if(!Double.isFinite(times[i])||times[i]<0||times[i]>frames||i>0&&times[i]<=times[i-1])throw new IllegalArgumentException("Curve order");
            values[i]=vector(vs.get(i).getAsJsonArray(),dimensions);if(dimensions==4){double length=Math.sqrt(Arrays.stream(values[i]).map(v->v*v).sum());if(length<.9||length>1.1)throw new IllegalArgumentException("Quaternion length");for(int k=0;k<4;k++)values[i][k]/=length;}}
        return new Curve(times,values);
    }
    public static P1MilitaryMotion read(Path file)throws IOException{
        if(Files.size(file)>4_000_000)throw new IOException("Motion profile exceeds size limit");
        try(var reader=Files.newBufferedReader(file)){return new P1MilitaryMotion(JsonParser.parseReader(reader).getAsJsonObject());}
    }
    private static final class Local {
        static final P1MilitaryMotion DATA=load();
        private static P1MilitaryMotion load(){
            var path=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("blacklight/p1-military-motion.json");
            if(!Files.isRegularFile(path))return null;
            try{var data=read(path);com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Loaded local P1 military motion: {} clips",data.clips.size());return data;}
            catch(IOException|RuntimeException failure){com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Cannot load local P1 military motion; retaining authored rig",failure);return null;}
        }
    }
    public static P1MilitaryMotion local(){return Local.DATA;}
    public boolean hasClip(String name){return clips.containsKey(name);}
    public Frame sample(String name,double frame){var c=clips.get(name);return c==null?null:new Frame(sampleWorld(c,Math.max(0,Math.min(c.frames,Double.isFinite(frame)?frame:0))),reference);}
    /** Blend local joint transforms before evaluating the hierarchy, preserving bone lengths. */
    public Frame blend(String first,double firstFrame,String second,double secondFrame,double weight){
        var a=clips.get(first);var b=clips.get(second);if(a==null||b==null)return null;
        return new Frame(sampleWorld(a,clock(a,firstFrame),b,clock(b,secondFrame),Double.isFinite(weight)?Math.max(0,Math.min(1,weight)):0),reference);
    }
    private static double clock(Clip clip,double frame){return Math.max(0,Math.min(clip.frames,Double.isFinite(frame)?frame:0));}
    public double frames(String name){return clips.get(name).frames;}
    private Map<String,Transform> sampleWorld(Clip clip,double frame){
        return sampleWorld(clip,frame,null,0,0);
    }
    private Map<String,Transform> sampleWorld(Clip clip,double frame,Clip other,double otherFrame,double weight){
        var all=new ArrayList<Transform>(joints.size());var named=new HashMap<String,Transform>();
        for(int i=0;i<joints.size();i++){var j=joints.get(i);var local=local(j,clip.bones.get(j.name),frame);var p=local.position;var q=local.rotation;
            if(other!=null&&weight>0){var target=local(j,other.bones.get(j.name),otherFrame);p.lerp(target.position,weight);q.slerp(target.rotation,weight).normalize();}
            if(i>0){var parent=all.get(j.parent);parent.rotation.transform(p);p.add(parent.position);q.premul(parent.rotation).normalize();}
            var value=new Transform(p,q);all.add(value);named.put(j.name,value);
        }return Map.copyOf(named);
    }
    private static Transform local(Joint joint,Channels channel,double frame){
        var p=new Vector3d(joint.position);var q=new Quaterniond(joint.rotation);
        if(channel!=null){if(channel.position!=null)p.set(sample(channel.position,frame,false));if(channel.rotation!=null){var v=sample(channel.rotation,frame,true);q.set(v[0],v[1],v[2],v[3]);}}
        return new Transform(p,q);
    }
    private static double[] sample(Curve c,double time,boolean quaternion){
        int end=Arrays.binarySearch(c.times,time);if(end>=0)return c.values[end].clone();end=-end-1;
        if(end==0)return c.values[0].clone();if(end>=c.times.length)return c.values[c.values.length-1].clone();
        double t=(time-c.times[end-1])/(c.times[end]-c.times[end-1]);var a=c.values[end-1];var b=c.values[end];
        if(quaternion){var q=new Quaterniond(a[0],a[1],a[2],a[3]).slerp(new Quaterniond(b[0],b[1],b[2],b[3]),t).normalize();return new double[]{q.x,q.y,q.z,q.w};}
        return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};
    }
}
