package com.codex.blacklight.client.model;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads the production export, not a documentation copy or a silently invented fallback. */
public final class BlackwatchReferenceProfile {
    public static final String SOURCE_SHA="4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696";
    private final JsonObject root;
    private final Map<String,JsonObject> bones;
    private record Curve(double[] times,double[][] values){}
    private record Animation(double length,boolean looping,Map<String,Map<String,Curve>> bones){}
    private final Map<String,Animation> animations;
    private BlackwatchReferenceProfile(){
        try(var input=BlackwatchReferenceProfile.class.getResourceAsStream("/assets/blacklight/military/reference-rig.json")){
            if(input==null)throw new IllegalStateException("Missing production Blackwatch reference rig");
            root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1||!SOURCE_SHA.equals(root.get("source_sha256").getAsString()))throw new IllegalStateException("Unreviewed Blackwatch reference rig revision");
            var map=new LinkedHashMap<String,JsonObject>();for(var value:root.getAsJsonArray("bones")){var b=value.getAsJsonObject();map.put(b.get("name").getAsString(),b);}bones=Collections.unmodifiableMap(map);
            var compiled=new HashMap<String,Animation>();
            for(var animation:root.getAsJsonObject("animations").entrySet()){
                var a=animation.getValue().getAsJsonObject();var boneMap=new HashMap<String,Map<String,Curve>>();
                for(var bone:a.getAsJsonObject("bones").entrySet()){
                    var channels=new HashMap<String,Curve>();
                    for(var channel:bone.getValue().getAsJsonObject().entrySet()){
                        var keys=channel.getValue().getAsJsonArray();var times=new double[keys.size()];var values=new double[keys.size()][];
                        for(int i=0;i<keys.size();i++){var k=keys.get(i).getAsJsonObject();times[i]=k.get("time").getAsDouble();values[i]=vector(k.getAsJsonArray("value"));}
                        channels.put(channel.getKey(),new Curve(times,values));
                    }boneMap.put(bone.getKey(),Collections.unmodifiableMap(channels));
                }compiled.put(animation.getKey(),new Animation(a.get("length").getAsDouble(),"loop".equals(a.get("loop").getAsString()),Collections.unmodifiableMap(boneMap)));
            }animations=Collections.unmodifiableMap(compiled);
        }catch(IOException failure){throw new IllegalStateException("Cannot load Blackwatch reference profile",failure);}
    }
    private static final class Holder {static final BlackwatchReferenceProfile VALUE=new BlackwatchReferenceProfile();}
    public static BlackwatchReferenceProfile load(){return Holder.VALUE;}
    public double[] vector(String key){return vector(root.getAsJsonObject("native").getAsJsonArray(key));}
    public double number(String key){return root.getAsJsonObject("native").get(key).getAsDouble();}
    public double[] firstPersonGrip(String weapon,boolean support){
        for(var entry:root.getAsJsonArray("firstperson_models")){
            var model=entry.getAsJsonObject();if(model.get("weapon").getAsString().equals(weapon))return vector(model.getAsJsonArray(support?"support_grip":"main_grip"));
        }throw new IllegalArgumentException("Unknown production firearm grip: "+weapon);
    }
    public double[] boneRotation(String name){return vector(bones.get(name).getAsJsonArray("rotation"));}
    public int[] armUv(boolean left,boolean lower){var values=root.getAsJsonObject("native").getAsJsonObject("arm_uv").getAsJsonArray((left?"left":"right")+(lower?"_lower":"_upper"));return new int[]{values.get(0).getAsInt(),values.get(1).getAsInt()};}
    public record WeaponPart(String name,float[] box,int u,int v){}
    /** Eight independent light meshes, with materials sampled from the authorised source. */
    public Map<String,List<WeaponPart>> weapons(){
        var result=new LinkedHashMap<String,List<WeaponPart>>();
        for(var weapon:root.getAsJsonObject("weapons").entrySet()){
            var parts=new ArrayList<WeaponPart>();
            for(var element:weapon.getValue().getAsJsonArray()){
                var p=element.getAsJsonObject();var box=new float[6];for(int i=0;i<6;i++)box[i]=p.getAsJsonArray("box").get(i).getAsFloat();
                var uv=p.getAsJsonArray("uv");parts.add(new WeaponPart(p.get("name").getAsString(),box,uv.get(0).getAsInt(),uv.get(1).getAsInt()));
            }result.put(weapon.getKey(),List.copyOf(parts));
        }return Collections.unmodifiableMap(result);
    }
    private static double[] vector(JsonArray value){return new double[]{value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble()};}
    /** Authored locomotion loops; a shot is one bounded event and must never replay itself. */
    public double[] sample(String animation,String bone,String channel,double seconds){
        var a=animations.get(animation);if(a==null)return new double[3];
        var b=a.bones.get(bone);if(b==null)return new double[3];var curve=b.get(channel);if(curve==null||curve.times.length==0)return new double[3];
        double t=Double.isFinite(seconds)?Math.max(0,seconds):0;
        if(!animation.startsWith("shoot_")&&a.looping&&a.length>0)t%=a.length;else t=Math.min(t,a.length);
        if(t<=curve.times[0])return curve.values[0].clone();
        int at=Arrays.binarySearch(curve.times,t);if(at>=0)return curve.values[at].clone();
        int next=-at-1;if(next>=curve.times.length)return curve.values[curve.values.length-1].clone();
        double start=curve.times[next-1],end=curve.times[next],alpha=end>start?(t-start)/(end-start):1;
        var from=curve.values[next-1].clone();var to=curve.values[next];for(int axis=0;axis<3;axis++)from[axis]+=(to[axis]-from[axis])*alpha;return from;
    }
}
