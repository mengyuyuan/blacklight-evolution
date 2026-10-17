package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.SovereignWingMotion;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cached skinned surfaces; file IO and vertex construction never occur in the render loop. */
public final class SovereignWingModel {
    private record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,Bone a,Bone b,float blend){}
    private static final class Bone {
        final Quaternionf[] folded={new Quaternionf(),new Quaternionf()};
        String name;Vector3f pivot;Bone parent;List<Vector3f> path=new ArrayList<>();
        final Matrix4f frame=new Matrix4f(),skin=new Matrix4f();
    }
    private final Map<String,Bone> bones=new LinkedHashMap<>();
    private final Map<String,List<Vector3f>> roots=new HashMap<>();
    private final List<Vertex> mesh=new ArrayList<>();
    private float[] prepared, viewPrepared;
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    private static final String[] DIRECTIONS={"north","south","west","east","down","up"};
    public SovereignWingModel(){
        try(var in=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")){
            if(in==null)throw new IllegalStateException("Missing sovereign wing model");
            var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var item:data.getAsJsonArray("bones")){
                var json=item.getAsJsonObject();var b=new Bone();b.name=json.get("name").getAsString();b.pivot=vec(json.getAsJsonArray("pivot"));
                b.pivot.div(16);
                if(!json.get("parent").isJsonNull())b.parent=bones.get(json.get("parent").getAsString());
                for(int order=0;order<2;order++){
                    var q=data.getAsJsonObject("fold_poses").getAsJsonObject(order==0?"left_over":"right_over").getAsJsonArray(b.name);
                    b.folded[order].set(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat()).normalize();
                }
                bones.put(b.name,b);
            }
            if(data.has("skin_paths"))for(var entry:data.getAsJsonObject("skin_paths").entrySet())
                for(var point:entry.getValue().getAsJsonArray())bones.get(entry.getKey()).path.add(vec(point.getAsJsonArray()).div(16));
            if(data.has("root_paths"))for(var entry:data.getAsJsonObject("root_paths").entrySet()){
                var path=new ArrayList<Vector3f>();
                for(var point:entry.getValue().getAsJsonArray())path.add(vec(point.getAsJsonArray()).div(16));
                roots.put(entry.getKey(),path);
            }
            for(var item:data.getAsJsonArray("elements")){
                var e=item.getAsJsonObject();var bone=bones.get(e.get("_parent").getAsString());
                if(e.has("type")&&e.get("type").getAsString().equals("mesh")){addSurface(e);continue;}
                var a=vec(e.getAsJsonArray("from"));var b=vec(e.getAsJsonArray("to"));var origin=vec(e.getAsJsonArray("origin"));
                var angles=e.getAsJsonArray("rotation");
                var rotation=new Quaternionf().rotationXYZ((float)Math.toRadians(angles.get(0).getAsFloat()),(float)Math.toRadians(angles.get(1).getAsFloat()),(float)Math.toRadians(angles.get(2).getAsFloat()));
                Vector3f[] raw={new Vector3f(a.x,a.y,a.z),new Vector3f(b.x,a.y,a.z),new Vector3f(b.x,b.y,a.z),new Vector3f(a.x,b.y,a.z),new Vector3f(a.x,a.y,b.z),new Vector3f(b.x,a.y,b.z),new Vector3f(b.x,b.y,b.z),new Vector3f(a.x,b.y,b.z)};
                var posed=new Vector3f[8];for(int i=0;i<8;i++)posed[i]=new Vector3f(raw[i]).sub(origin).rotate(rotation).add(origin).div(16);
                boolean skin=e.has("_skin")&&e.get("_skin").getAsBoolean();
                for(int f=0;f<6;f++){
                    int[] corners=FACES[f];var uv=e.getAsJsonObject("faces").getAsJsonObject(DIRECTIONS[f]).getAsJsonArray("uv");
                    float u0=uv.get(0).getAsFloat(),v0=uv.get(1).getAsFloat(),u1=uv.get(2).getAsFloat(),v1=uv.get(3).getAsFloat();
                    int ax=f<2?0:f<4?2:0,ay=f<4?1:2;
                    var n=new Vector3f(posed[corners[1]]).sub(posed[corners[0]]).cross(new Vector3f(posed[corners[2]]).sub(posed[corners[0]])).normalize();
                    for(int index:corners){var point=posed[index];float fu=(raw[index].get(ax)-a.get(ax))/(b.get(ax)-a.get(ax)),fv=(raw[index].get(ay)-a.get(ay))/(b.get(ay)-a.get(ay));
                        Bone first=bone,second=bone;float blend=0;
                        if(e.has("_root_skin")){
                            // Attachment tissue follows the body frame only. The outer
                            // spars articulate around the overlapping root hinge.
                            first=bones.get("wing_mount");second=bones.get(e.get("_root_skin").getAsString());blend=0;
                        }else if(e.has("_shoulder_skin")){
                            // The mantle is part of the scapula, not the swinging arm.
                            // Blending its low corners onto Arm_R/L collapsed the cap
                            // as the arms opened. The native upper-arm surface still
                            // bends normally underneath this fixed shoulder crown.
                            first=bones.get("wing_mount");second=bones.get(e.get("_shoulder_skin").getAsString()+"_root");
                            blend=0;
                        }else if(skin){
                            float near=Float.MAX_VALUE,next=Float.MAX_VALUE;
                            String side=bone.name.startsWith("left")?"left":"right";
                            for(var candidate:bones.values()){
                                if(!candidate.name.startsWith(side)||candidate.path.size()<2)continue;
                                float d=distance(point,candidate.path);
                                if(d<near){second=first;next=near;first=candidate;near=d;}
                                else if(d<next){second=candidate;next=d;}
                            }
                            blend=near+next>.00001F?near/(near+next):.5F;
                        }
                        mesh.add(new Vertex(point.x,point.y,point.z,(u0+(u1-u0)*fu)/256,(v1-(v1-v0)*fv)/256,n.x,n.y,n.z,first,second,blend));}
                }
            }
            prepared=new float[mesh.size()*6];viewPrepared=new float[prepared.length];
        }catch(Exception ex){throw new IllegalStateException("Could not load Sovereign Wings",ex);}
    }
    private void addSurface(JsonObject element){
        var points=element.getAsJsonObject("vertices");var weights=element.getAsJsonObject("_weights");
        for(var value:element.getAsJsonObject("faces").entrySet()){
            var face=value.getValue().getAsJsonObject();var corners=face.getAsJsonArray("vertices");
            if(corners.size()<3||corners.size()>4)throw new IllegalArgumentException("Wing surface requires triangles or quads");
            var a=vec(points.getAsJsonArray(corners.get(0).getAsString()));
            var b=vec(points.getAsJsonArray(corners.get(1).getAsString()));
            var c=vec(points.getAsJsonArray(corners.get(2).getAsString()));
            var normal=b.sub(a).cross(c.sub(a));
            if(normal.lengthSquared()<1e-12F)throw new IllegalArgumentException("Degenerate wing surface");
            normal.normalize();
            for(int i=0;i<4;i++){
                // Minecraft's entity buffer consumes quads; repeat the third corner
                // to emit a triangle without an extra visible diagonal surface.
                String key=corners.get(Math.min(i,corners.size()-1)).getAsString();
                var point=vec(points.getAsJsonArray(key)).div(16);var uv=face.getAsJsonObject("uv").getAsJsonArray(key);
                var weight=weights.getAsJsonArray(key);var first=bones.get(weight.get(0).getAsString());var second=bones.get(weight.get(1).getAsString());
                float blend=weight.get(2).getAsFloat();
                if(first==null||second==null||!Float.isFinite(blend)||blend<0||blend>1)throw new IllegalArgumentException("Invalid wing skin weights");
                mesh.add(new Vertex(point.x,point.y,point.z,uv.get(0).getAsFloat()/256,uv.get(1).getAsFloat()/256,
                        normal.x,normal.y,normal.z,first,second,blend));
            }
        }
    }
    private static Vector3f vec(JsonArray a){return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    private static float distance(Vector3f point,List<Vector3f> path){
        float best=Float.MAX_VALUE;
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var delta=new Vector3f(path.get(i)).sub(a);
            float t=com.codex.blacklight.forge.WingMath.clamp(new Vector3f(point).sub(a).dot(delta)/Math.max(.000001F,delta.lengthSquared()),0,1);
            best=Math.min(best,point.distance(delta.mul(t).add(a)));
        }
        return best;
    }
    /** Evaluate once per actor, reuse the same deformed surface for colour and emission. */
    public void prepare(SovereignWingMotion.Pose motion,Vector3f strokeAxis,float deployment,float air,int foldVariant){
        for(var bone:bones.values()){
            var parent=bone.parent==null?new Vector3f():bone.parent.pivot;
            if(bone.parent==null)bone.frame.identity();else bone.frame.set(bone.parent.frame);
            bone.frame.translate(bone.pivot.x-parent.x,bone.pivot.y-parent.y,bone.pivot.z-parent.z);
            bone.frame.rotate(SovereignWingMotion.articulation(bone.name,motion,strokeAxis,air,bone.folded[foldVariant&1]));
            bone.skin.set(bone.frame).translate(-bone.pivot.x,-bone.pivot.y,-bone.pivot.z);
            if(!bone.name.equals("wing_mount")){
                var base=roots.get(bone.name.startsWith("left")?"left_root":"right_root").get(0);
                // Each wing emerges from its own embedded tissue root. Scaling the
                // whole accessory about the spine would pull both roots off the skin.
                float growth=com.codex.blacklight.animation.WingMorphTimeline.growth(bone.name,deployment);
                bone.skin.set(new Matrix4f().translation(base).scale(growth)
                        .translate(-base.x,-base.y,-base.z).mul(bone.skin));
            }
        }
        var p=new Vector3f();var other=new Vector3f();var n=new Vector3f();int at=0;
        for(var v:mesh){
            v.a.skin.transformPosition(p.set(v.x,v.y,v.z));
            if(v.a.name.equals("wing_mount")&&v.b.name.endsWith("_root")){
                var base=roots.get(v.b.name.startsWith("left")?"left_root":"right_root").get(0);
                p.sub(base).mul(Math.min(1,deployment*4)).add(base);
            }
            if(v.blend>0)p.lerp(v.b.skin.transformPosition(other.set(v.x,v.y,v.z)),v.blend);
            v.a.skin.transformDirection(n.set(v.nx,v.ny,v.nz));
            if(v.blend>0)n.lerp(v.b.skin.transformDirection(other.set(v.nx,v.ny,v.nz)),v.blend);
            n.normalize();prepared[at++]=p.x;prepared[at++]=p.y;prepared[at++]=p.z;
            prepared[at++]=n.x;prepared[at++]=n.y;prepared[at++]=n.z;
        }
    }
    /** Posed endpoints let the wake originate on the actual mesh, even while banking. */
    public Vector3f tip(String side,int number){
        var bone=bones.get(side+"_tip_"+number);
        return bone.skin.transformPosition(new Vector3f(bone.path.get(bone.path.size()-1)));
    }
    /** Deformed outer spar, from the shoulder hinge to its tip. */
    public Vector3f[] leadingEdge(String side){
        var points=new ArrayList<Vector3f>();
        for(String part:new String[]{"rib","arch","finger","tip"}){
            var bone=bones.get(side+"_"+part+"_1");
            for(var point:bone.path)points.add(bone.skin.transformPosition(new Vector3f(point)));
        }
        return points.toArray(Vector3f[]::new);
    }
    /** Same camera-space surface for both passes; do not allocate vectors per emitted vertex. */
    public void prepareRender(PoseStack stack){
        var pose=stack.last();var p=new Vector3f();var n=new Vector3f();
        for(int at=0;at<prepared.length;at+=6){
            pose.pose().transformPosition(prepared[at],prepared[at+1],prepared[at+2],p);
            pose.normal().transform(prepared[at+3],prepared[at+4],prepared[at+5],n);
            viewPrepared[at]=p.x;viewPrepared[at+1]=p.y;viewPrepared[at+2]=p.z;
            viewPrepared[at+3]=n.x;viewPrepared[at+4]=n.y;viewPrepared[at+5]=n.z;
        }
    }
    public void render(PoseStack stack,VertexConsumer output,int light){
        int at=0;
        for(var v:mesh){
            output.vertex(viewPrepared[at],viewPrepared[at+1],viewPrepared[at+2],1,1,1,1,v.u,v.v,
                    OverlayTexture.NO_OVERLAY,light,viewPrepared[at+3],viewPrepared[at+4],viewPrepared[at+5]);at+=6;
        }
    }
}
