package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original curves retargeted to the authored mesh, shared by rendering and contact queries. */
public final class SourceBossMotion {
    public record Clip(String source,float fps,float seconds,float[][][] frames) {}
    private record Socket(int bone,float[] offset) {}
    private static final Map<EncounterKind,SourceBossMotion> TABLES=new EnumMap<>(EncounterKind.class);
    public final String[] bones;
    public final int[] parents;
    public final float[][] pivots;
    private final float pixelsPerSourceUnit;
    private final float presentationScale;
    private final Map<String,Clip> clips=new HashMap<>();
    private final Map<String,Socket> sockets=new HashMap<>();
    private SourceBossMotion(EncounterKind kind){
        presentationScale=kind==EncounterKind.ELIZABETH_GREENE?GreenePresentation.SCALE:1F;
        String path="/assets/blacklight/models/encounter_"+kind.id+"_source.json";
        try(var in=Objects.requireNonNull(SourceBossMotion.class.getResourceAsStream(path));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            pixelsPerSourceUnit=root.has("pixelsPerSourceUnit")?root.get("pixelsPerSourceUnit").getAsFloat():Float.NaN;
            bones=gson.fromJson(root.get("bones"),String[].class);
            parents=gson.fromJson(root.get("parents"),int[].class);
            pivots=gson.fromJson(root.get("pivots"),float[][].class);
            if(root.has("sockets"))for(var entry:root.getAsJsonObject("sockets").entrySet()){
                var s=entry.getValue().getAsJsonObject();int bone=Arrays.asList(bones).indexOf(s.get("bone").getAsString());
                float[] offset=gson.fromJson(s.get("offset"),float[].class);
                if(bone<0||offset.length!=3)throw new IllegalStateException("Invalid source socket "+entry.getKey());
                sockets.put(entry.getKey(),new Socket(bone,offset));
            }
            for(var entry:root.getAsJsonObject("clips").entrySet()){
                var c=entry.getValue().getAsJsonObject();
                clips.put(entry.getKey(),new Clip(c.get("source").getAsString(),c.get("fps").getAsFloat(),
                    c.get("seconds").getAsFloat(),gson.fromJson(c.get("frames"),float[][][].class)));
            }
        }catch(Exception ex){throw new IllegalStateException("Invalid original boss motion "+path,ex);}
    }
    public static boolean supports(EncounterKind kind){return kind==EncounterKind.FLYER||kind==EncounterKind.JUGGERNAUT||kind==EncounterKind.GOLIATH||kind==EncounterKind.ELIZABETH_GREENE||kind==EncounterKind.LEADER_HUNTER;}
    public static synchronized SourceBossMotion get(EncounterKind kind){return supports(kind)?TABLES.computeIfAbsent(kind,SourceBossMotion::new):null;}
    public Clip clip(String name){return clips.get(name);}
    public double sourceUnitBlocks(){if(!Float.isFinite(pixelsPerSourceUnit)||pixelsPerSourceUnit<=0)throw new IllegalStateException("Source unit scale is unavailable");return pixelsPerSourceUnit*presentationScale/16D;}
    public boolean has(String name){return clips.containsKey(name);}
    public Set<String> names(){return Collections.unmodifiableSet(clips.keySet());}
    public int ticks(String name){return (int)Math.ceil(clips.get(name).seconds*20);}
    public float[][] sample(String name,float ticks,boolean loop){
        var c=Objects.requireNonNull(clips.get(name),name);
        float frame=Math.max(0,ticks*c.fps/20);
        if(loop&&c.frames.length>1)frame%=c.frames.length-1;else frame=Math.min(c.frames.length-1,frame);
        int lo=(int)frame,hi=Math.min(lo+1,c.frames.length-1);var pose=new float[bones.length][6];
        for(int b=0;b<bones.length;b++)pose[b]=blend(c.frames[lo][b],c.frames[hi][b],frame-lo);
        return pose;
    }
    /** Explicit Rz Ry Rx extraction, including the coupled X/Z rotation at
     * +/-90 degree Y. Native inverse-bind helpers can sit exactly on that
     * singularity; independent atan2(near-zero,near-zero) loses their twist. */
    public static float[] blend(float[] a,float[] b,float weight){
        var q=new Quaternionf().rotationZYX(a[2],a[1],a[0]).slerp(new Quaternionf().rotationZYX(b[2],b[1],b[0]),weight).normalize();
        // Normalize in double for extraction: the float quaternion norm can
        // straddle one, and cancellation at the pole otherwise chooses an
        // unrelated X/Z pair. The pose is still the same shortest-path slerp.
        double inv=1/Math.sqrt((double)q.x*q.x+(double)q.y*q.y+(double)q.z*q.z+(double)q.w*q.w);
        double x=q.x*inv,y=q.y*inv,z=q.z*inv,w=q.w*inv;
        double r00=1-2*(y*y+z*z),r10=2*(x*y+w*z),r20=2*(x*z-w*y);
        double cosY=Math.hypot(r00,r10);float[] out=new float[Math.max(a.length,b.length)];
        out[1]=(float)Math.atan2(-r20,cosY);
        if(cosY<1E-6){
            // At either pole Z can be zero if X retains the coupled twist.
            out[0]=(float)Math.atan2(2*(w*x-y*z),1-2*(x*x+z*z));out[2]=0;
        }else{
            out[0]=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));
            out[2]=(float)Math.atan2(r10,r00);
        }
        for(int i=3;i<6;i++)out[i]=a[i]+(b[i]-a[i])*weight;
        for(int i=6;i<out.length;i++){float av=i<a.length?a[i]:1,bv=i<b.length?b[i]:1;out[i]=av+(bv-av)*weight;}
        return out;
    }
    public Matrix4f[] transforms(float[][] pose){
        var out=new Matrix4f[bones.length];
        for(int i=0;i<bones.length;i++){
            float[] p=pivots[i],v=pose[i];
            out[i]=parents[i]<0?new Matrix4f().scale(presentationScale):new Matrix4f(out[parents[i]]);
            out[i].translate((p[0]+v[3])/16,(p[1]+v[4])/16,(p[2]+v[5])/16)
                .rotate(new Quaternionf().rotationZYX(v[2],v[1],v[0]));
            if(v.length>=9)out[i].scale(v[6],v[7],v[8]);
            out[i].translate(-p[0]/16,-p[1]/16,-p[2]/16);
        }
        return out;
    }
    public float[][] actionPose(SourceBossAction action,float age){
        float[][] pose=sample(action.clip,action.sourceTick(age),false);
        var segments=action.segments;int index=0;for(int i=1;i<segments.length;i++)if(age/20>=segments[i].at())index=i;
        var segment=segments[index];float elapsed=age/20-segment.at();
        if(segment.blendIn()>0&&elapsed<segment.blendIn()){
            float[][] before=index==0?sample("idle",0,false):sample(action.clip,action.sourceTick(segment.at()*20-.001F),false);
            for(int b=0;b<bones.length;b++)pose[b]=blend(before[b],pose[b],Math.max(0,elapsed/segment.blendIn()));
        }
        float remaining=action.seconds-age/20;float fade=segments[segments.length-1].blendOut();
        if(fade>0&&remaining<fade){var idle=sample("idle",0,false);for(int b=0;b<bones.length;b++)pose[b]=blend(pose[b],idle[b],1-Math.max(0,remaining/fade));}
        return pose;
    }
    public Vector3f point(float[][] pose,String bone){
        int index=Arrays.asList(bones).indexOf(bone);
        if(index<0)return null;
        float[] p=pivots[index];return transforms(pose)[index].transformPosition(new Vector3f(p[0]/16,p[1]/16,p[2]/16));
    }
    /** Source joint-local attachments, converted by the exporter into reference
     * model axes. Pose and render share one clock; offsets are model pixels. */
    public Vector3f socket(float[][] pose,String name){
        var socket=sockets.get(name);if(socket==null)return null;
        float[] p=pivots[socket.bone],o=socket.offset;
        return transforms(pose)[socket.bone].transformPosition(new Vector3f((p[0]+o[0])/16,(p[1]+o[1])/16,(p[2]+o[2])/16));
    }
}
