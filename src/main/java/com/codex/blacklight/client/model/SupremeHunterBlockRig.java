package com.codex.blacklight.client.model;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Minecraft-style Supreme Hunter: cuboids rigidly bound to the original joint hierarchy.
 * Each clip row stores one model-space rotation per bone (about its rest pivot) plus the
 * pelvis offset, retargeted from the original supreme_hunter clips at 30 fps. */
public final class SupremeHunterBlockRig {
    public record Quad(int bone,float nx,float ny,float nz,float[] xyz,float[] uv){}
    public record Clip(boolean loop,float[][] frames){
        public float seconds(){return (frames.length-(loop?0:1))/30F;}
    }
    public final String[] names;public final int[] parents;public final Vector3f[] pivots;
    public final Quad[] quads;public final Map<String,Clip> clips=new HashMap<>();
    private static SupremeHunterBlockRig instance;
    public static synchronized SupremeHunterBlockRig get(){if(instance==null)instance=new SupremeHunterBlockRig();return instance;}
    private SupremeHunterBlockRig(){
        String path="/assets/blacklight/models/supreme_hunter_block.json";
        try(var in=java.util.Objects.requireNonNull(SupremeHunterBlockRig.class.getResourceAsStream(path));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("schema");
            var bs=root.getAsJsonArray("bones");names=new String[bs.size()];parents=new int[bs.size()];pivots=new Vector3f[bs.size()];
            for(int i=0;i<names.length;i++){var b=bs.get(i).getAsJsonObject();names[i]=b.get("name").getAsString();parents[i]=b.get("parent").getAsInt();
                var p=b.getAsJsonArray("pivot");pivots[i]=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                if(parents[i]>=i)throw new IllegalArgumentException("hierarchy order");}
            var qs=root.getAsJsonArray("quads");quads=new Quad[qs.size()];
            for(int i=0;i<quads.length;i++){var q=qs.get(i).getAsJsonArray();float[] xyz=new float[12],uv=new float[8];
                for(int k=0;k<4;k++){int o=4+k*5;xyz[k*3]=q.get(o).getAsFloat();xyz[k*3+1]=q.get(o+1).getAsFloat();xyz[k*3+2]=q.get(o+2).getAsFloat();uv[k*2]=q.get(o+3).getAsFloat();uv[k*2+1]=q.get(o+4).getAsFloat();}
                quads[i]=new Quad(q.get(0).getAsInt(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat(),xyz,uv);}
            int width=names.length*4+3;
            for(var e:root.getAsJsonObject("clips").entrySet()){var c=e.getValue().getAsJsonObject();var fs=c.getAsJsonArray("frames");float[][] rows=new float[fs.size()][];
                for(int f=0;f<rows.length;f++){var r=fs.get(f).getAsJsonArray();if(r.size()!=width)throw new IllegalArgumentException("frame width "+e.getKey());rows[f]=new float[width];for(int k=0;k<width;k++)rows[f][k]=r.get(k).getAsFloat();}
                if(rows.length<2)throw new IllegalArgumentException("frames "+e.getKey());
                clips.put(e.getKey(),new Clip(c.get("loop").getAsBoolean(),rows));}
        }catch(Exception failure){throw new IllegalStateException("Invalid Supreme Hunter block rig",failure);}
    }
    public record PosedBones(Quaternionf[] rotation, Vector3f[] joint) {}
    public record SkinPoint(int bone, Vector3f offset, Vector3f normal, Quad face) {
        public Vector3f position(PosedBones pose) {
            return pose.rotation[bone].transform(new Vector3f(offset)).add(pose.joint[bone]);
        }
        public Vector3f outward(PosedBones pose) {
            return pose.rotation[bone].transform(new Vector3f(normal)).normalize();
        }
    }

    public PosedBones pose(float[] row) {
        int count=names.length;
        Quaternionf[] rotation=new Quaternionf[count];
        Vector3f[] joint=new Vector3f[count];
        for(int i=0;i<count;i++) {
            int o=i*4, parent=parents[i];
            rotation[i]=new Quaternionf(row[o],row[o+1],row[o+2],row[o+3]);
            joint[i]=parent<0 ? new Vector3f(pivots[i]).add(row[count*4],row[count*4+1],row[count*4+2])
                    : rotation[parent].transform(new Vector3f(pivots[i]).sub(pivots[parent])).add(joint[parent]);
        }
        return new PosedBones(rotation,joint);
    }

    /** Bind once against the visible triangles, then follow the same spot on the moving joint. */
    public SkinPoint bind(PosedBones pose, Vector3f contact) {
        float closest=Float.POSITIVE_INFINITY;
        SkinPoint result=null;
        Vector3f local=new Vector3f(), point=new Vector3f();
        Vector3f[] corners={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
        for(Quad face:quads) {
            int b=face.bone();
            pose.rotation[b].transformInverse(local.set(contact).sub(pose.joint[b])).add(pivots[b]);
            for(int k=0;k<4;k++)corners[k].set(face.xyz[k*3],face.xyz[k*3+1],face.xyz[k*3+2]);
            for(int k=1;k<=2;k++) {
                org.joml.Intersectionf.findClosestPointOnTriangle(corners[0],corners[k],corners[k+1],local,point);
                float distance=point.distanceSquared(local);
                if(distance<closest) {
                    closest=distance;
                    result=new SkinPoint(b,new Vector3f(point).sub(pivots[b]),new Vector3f(face.nx,face.ny,face.nz),face);
                }
            }
        }
        return result;
    }

    public boolean has(String clip){return clips.containsKey(clip);}
    public float seconds(String clip){var c=clips.get(clip);return c==null?0:c.seconds();}
    /** Samples at server ticks (20 Hz) into 30 fps rows. */
    public float[] sample(String key,float ticks){
        var c=clips.getOrDefault(key,clips.get("idle"));int n=c.frames.length;float at=ticks*1.5F;
        if(c.loop){at=at%n;if(at<0)at+=n;}else at=Math.max(0,Math.min(at,n-1));
        int lo=(int)at;int hi=c.loop?(lo+1)%n:Math.min(lo+1,n-1);return blend(c.frames[lo],c.frames[hi],at-lo);
    }
    public static float[] blend(float[] a,float[] b,float t){
        if(a==null)return b;if(b==null||t<=0)return a;if(t>=1)return b;
        var out=new float[b.length];int bones=(b.length-3)/4;var qa=new Quaternionf();var qb=new Quaternionf();
        for(int i=0;i<bones;i++){int o=i*4;qa.set(a[o],a[o+1],a[o+2],a[o+3]);qb.set(b[o],b[o+1],b[o+2],b[o+3]);qa.slerp(qb,t).normalize();out[o]=qa.x;out[o+1]=qa.y;out[o+2]=qa.z;out[o+3]=qa.w;}
        for(int k=b.length-3;k<b.length;k++)out[k]=a[k]+(b[k]-a[k])*t;return out;
    }
}
