package com.codex.blacklight.combat;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
/** P2 Evolved's full original hierarchy, neutral-aligned to Minecraft proportions. */
public final class EvolvedMotions {
    public record Bone(String name,int parent,float x,float y,float z){public Vector3f pivot(){return new Vector3f(x,y,z);}}
    public record Clip(float seconds,List<float[]> frames,List<float[]> root){}
    private record Data(List<Bone> bones,Map<String,Clip> clips){}
    private static Data loaded;
    private EvolvedMotions(){}
    private static synchronized Data data(){
        if(loaded!=null)return loaded;var root=MotionResources.read("evolved-poses.data",8*1024*1024).orElseThrow(()->new IllegalStateException("Missing P2 Evolved rig"));
        if(root.get("schema").getAsInt()!=2)throw new IllegalArgumentException("Evolved pose schema");
        var bones=new ArrayList<Bone>();var names=new HashSet<String>();
        for(var item:root.getAsJsonArray("bones")){
            var b=item.getAsJsonObject();var p=b.getAsJsonArray("pivot");int parent=b.get("parent").getAsInt();String name=b.get("name").getAsString();float x=p.get(0).getAsFloat(),y=p.get(1).getAsFloat(),z=p.get(2).getAsFloat();
            if(parent>=bones.size()||parent< -1||!names.add(name)||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Evolved hierarchy");bones.add(new Bone(name,parent,x,y,z));
        }
        if(bones.size()!=67)throw new IllegalArgumentException("P2 Evolved joint count");
        var clips=new HashMap<String,Clip>();for(var entry:root.getAsJsonObject("clips").entrySet()){
            var obj=entry.getValue().getAsJsonObject();float seconds=obj.get("seconds").getAsFloat();var rows=new ArrayList<float[]>();if(!Float.isFinite(seconds)||seconds<=0||seconds>30)throw new IllegalArgumentException("Evolved duration");
            for(var item:obj.getAsJsonArray("frames")){
                var row=item.getAsJsonArray();if(row.size()!=bones.size()*7)throw new IllegalArgumentException("Evolved frame width");float[] values=new float[row.size()];for(int i=0;i<values.length;i++){values[i]=row.get(i).getAsFloat();if(!Float.isFinite(values[i])||Math.abs(values[i])>1000)throw new IllegalArgumentException("Evolved transform");}
                for(int i=0;i<values.length;i+=7){float norm=0;for(int j=0;j<4;j++)norm+=values[i+j]*values[i+j];if(Math.abs(norm-1)>.002)throw new IllegalArgumentException("Evolved quaternion");}rows.add(values);
            }
            var travel=new ArrayList<float[]>();
            if(obj.has("root"))for(var item:obj.getAsJsonArray("root")){
                var p=item.getAsJsonArray();if(p.size()!=3)throw new IllegalArgumentException("Evolved root width");var v=new float[3];
                for(int i=0;i<3;i++){v[i]=p.get(i).getAsFloat();if(!Float.isFinite(v[i])||Math.abs(v[i])>1000)throw new IllegalArgumentException("Evolved root position");}travel.add(v);
            }
            else for(int i=0;i<rows.size();i++)travel.add(new float[3]);
            if(rows.size()<2||rows.size()>1000||travel.size()!=rows.size())throw new IllegalArgumentException("Evolved frames");clips.put(entry.getKey(),new Clip(seconds,List.copyOf(rows),List.copyOf(travel)));
        }
        return loaded=new Data(List.copyOf(bones),Map.copyOf(clips));
    }
    public static List<Bone> bones(){return data().bones;}
    public static int index(String name){for(int i=0;i<bones().size();i++)if(bones().get(i).name.equals(name))return i;throw new IllegalArgumentException(name);}
    public static Clip clip(String key){return data().clips.get(key);}
    /** Actor-local left, up, forward displacement in source metres / Minecraft blocks. */
    public static Vector3f root(String key,float ticks){
        var c=clip(key);if(c==null)return new Vector3f();float at=Math.clamp(ticks/(c.seconds*20),0,1)*(c.root.size()-1);int i=(int)at;float t=at-i;
        var a=c.root.get(i);var b=c.root.get(Math.min(i+1,c.root.size()-1));return new Vector3f(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t);
    }
    public static float forwardExtent(String key){var c=clip(key);if(c==null)return 0;float extent=0;for(var p:c.root)extent=Math.max(extent,p[2]);return extent;}
    public static float[] blend(float[] from,float[] to,float amount){
        if(from==null)return to;if(to==null)return from;var out=new float[to.length];amount=Math.clamp(amount,0,1);
        for(int i=0;i<out.length;i+=7){var q=new Quaternionf(from[i],from[i+1],from[i+2],from[i+3]).slerp(new Quaternionf(to[i],to[i+1],to[i+2],to[i+3]),amount);out[i]=q.x;out[i+1]=q.y;out[i+2]=q.z;out[i+3]=q.w;for(int j=4;j<7;j++)out[i+j]=from[i+j]+(to[i+j]-from[i+j])*amount;}return out;
    }
    public static float[] sample(String key,float ticks,boolean loop){
        var clip=clip(key);if(clip==null)return null;float progress=ticks/(clip.seconds*20);progress=loop?progress-(float)Math.floor(progress):Math.clamp(progress,0,1);
        float at=progress*(loop?clip.frames.size():clip.frames.size()-1);int index=(int)at;float fraction=at-index;var a=clip.frames.get(index);var b=clip.frames.get(loop?(index+1)%clip.frames.size():Math.min(index+1,clip.frames.size()-1));var result=new float[a.length];
        for(int i=0;i<a.length;i+=7){var q=new Quaternionf(a[i],a[i+1],a[i+2],a[i+3]).slerp(new Quaternionf(b[i],b[i+1],b[i+2],b[i+3]),fraction).normalize();result[i]=q.x;result[i+1]=q.y;result[i+2]=q.z;result[i+3]=q.w;for(int j=4;j<7;j++)result[i+j]=a[i+j]+(b[i+j]-a[i+j])*fraction;}return result;
    }
    public static Matrix4f[] skin(float[] row,float weight){
        var bones=bones();var matrices=new Matrix4f[bones.size()];var positions=new Vector3f[bones.size()];var rotations=new Quaternionf[bones.size()];weight=Math.clamp(weight,0,1);
        for(int i=0;i<bones.size();i++){
            var b=bones.get(i);int o=i*7;var q=row==null?new Quaternionf():new Quaternionf().slerp(new Quaternionf(row[o],row[o+1],row[o+2],row[o+3]),weight);rotations[i]=q;
            var p=b.parent<0?b.pivot():rotations[b.parent].transform(b.pivot().sub(bones.get(b.parent).pivot())).add(positions[b.parent]);if(row!=null)p.add(row[o+4]*weight,row[o+5]*weight,row[o+6]*weight);positions[i]=p;
            matrices[i]=new Matrix4f().rotation(q).setTranslation(new Vector3f(p).sub(q.transform(b.pivot())));
        }return matrices;
    }
}
