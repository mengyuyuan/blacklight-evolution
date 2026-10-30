package com.codex.blacklight.client.model;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared indexed surface, smoothly weighted to the live Gecko skeleton. */
public final class ConnectedCreatureMesh {
    private record Vertex(Vector3f p,String a,String b,float weight){}
    private record Face(int a,int b,int c,float au,float av,float bu,float bv,float cu,float cv){}
    private record BonePose(Matrix4f transform,boolean hidden,com.codex.blacklight.animation.RigidSkinning.Joint joint){}
    private final boolean rigidSkinning;
    private final Vertex[] vertices;
    private final Face[] faces;
    private final Vector3f[] points;
    private final boolean[] hidden;
    private static final Map<String,Optional<ConnectedCreatureMesh>> CACHE=new HashMap<>();
    public static void invalidate(){CACHE.clear();}
    public static ConnectedCreatureMesh get(String name){return CACHE.computeIfAbsent(name,n->{
        try(var in=Minecraft.getInstance().getResourceManager().open(new ResourceLocation("blacklight","models/"+n+"_connected.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            return Optional.of(new ConnectedCreatureMesh(JsonParser.parseReader(reader).getAsJsonObject()));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot load connected creature {}",n,e);return Optional.empty();}
    }).orElse(null);}
    private ConnectedCreatureMesh(com.google.gson.JsonObject root){
        rigidSkinning=root.has("skinning")&&root.get("skinning").getAsString().equals("dual_quaternion");
        if(root.get("schema").getAsInt()!=2)throw new IllegalArgumentException("Unsupported skin schema");
        var vs=root.getAsJsonArray("vertices");vertices=new Vertex[vs.size()];
        points=new Vector3f[vertices.length];hidden=new boolean[vertices.length];
        for(int i=0;i<vertices.length;i++){
            var v=vs.get(i).getAsJsonObject();var p=v.getAsJsonArray("p");var w=v.getAsJsonArray("w");
            var point=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());float weight=w.get(2).getAsFloat();
            if(!point.isFinite()||!Float.isFinite(weight)||weight<0||weight>1)throw new IllegalArgumentException("Invalid surface vertex");
            vertices[i]=new Vertex(point,w.get(0).getAsString(),w.get(1).getAsString(),weight);
            points[i]=new Vector3f();
        }
        var fs=root.getAsJsonArray("faces");faces=new Face[fs.size()];
        for(int i=0;i<faces.length;i++){
            var f=fs.get(i).getAsJsonArray();int a=f.get(0).getAsInt(),b=f.get(1).getAsInt(),c=f.get(2).getAsInt();
            if(a<0||b<0||c<0||a>=vertices.length||b>=vertices.length||c>=vertices.length)throw new IllegalArgumentException("Invalid surface index");
            for(int uv=4;uv<10;uv++){float value=f.get(uv).getAsFloat();if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid skin UV");}
            faces[i]=new Face(a,b,c,f.get(4).getAsFloat(),f.get(5).getAsFloat(),f.get(6).getAsFloat(),f.get(7).getAsFloat(),f.get(8).getAsFloat(),f.get(9).getAsFloat());
        }
    }
    private static void collect(GeoBone bone,PoseStack pose,boolean hidden,Map<String,BonePose> result){
        pose.pushPose();RenderUtils.prepMatrixForBone(pose,bone);
        boolean hide=hidden||bone.isHidden();var matrix=new Matrix4f(pose.last().pose());
        result.put(bone.getName(),new BonePose(matrix,hide,com.codex.blacklight.animation.RigidSkinning.Joint.from(matrix)));
        for(var child:bone.getChildBones())collect(child,pose,hidden||bone.isHidingChildren(),result);
        pose.popPose();
    }
    public void render(GeoBone root,PoseStack pose,VertexConsumer out,int light,int overlay,int tint){
        var transforms=new HashMap<String,BonePose>();collect(root,new PoseStack(),false,transforms);
        // Reuse scratch vertices: this runs for every visible creature on every frame.
        var q=new Vector3f();
        var real=new Quaternionf();var dual=new Quaternionf();
        for(int i=0;i<vertices.length;i++){
            var v=vertices[i];var a=transforms.get(v.a);var b=transforms.get(v.b);
            if(a==null||b==null)throw new IllegalStateException("Missing skin joint "+v.a+" / "+v.b);
            if(rigidSkinning&&a.joint.rigid()&&b.joint.rigid())
                com.codex.blacklight.animation.RigidSkinning.transform(a.joint,b.joint,v.weight,v.p,points[i],real,dual);
            else { // Deliberate jaw scaling during consumption remains supported.
                a.transform.transformPosition(v.p,points[i]);b.transform.transformPosition(v.p,q);points[i].lerp(q,v.weight);
            }
            hidden[i]=v.weight<.5F?a.hidden:b.hidden;
        }
        var edge=new Vector3f();var normal=new Vector3f();
        for(var f:faces){
            if(hidden[f.a]||hidden[f.b]||hidden[f.c])continue;
            var a=points[f.a];var b=points[f.b];var c=points[f.c];b.sub(a,edge);c.sub(a,normal);edge.cross(normal,normal);
            if(normal.lengthSquared()<1e-12F)continue;normal.normalize();
            if(out instanceof ConsumeAnatomy.Consumer anatomy)anatomy.anatomy(ConsumeAnatomy.named(vertices[f.a].a,0));
            emit(out,pose.last(),a,normal,f.au,f.av,tint,overlay,light);
            emit(out,pose.last(),b,normal,f.bu,f.bv,tint,overlay,light);
            emit(out,pose.last(),c,normal,f.cu,f.cv,tint,overlay,light);
            emit(out,pose.last(),c,normal,f.cu,f.cv,tint,overlay,light);
        }
    }
    private static void emit(VertexConsumer out,PoseStack.Pose pose,Vector3f p,Vector3f normal,float u,float v,int color,int overlay,int light){
        out.vertex(pose.pose(),p.x,p.y,p.z).color(color).uv(u,v).overlayCoords(overlay).uv2(light).normal(pose.normal(),normal.x,normal.y,normal.z).endVertex();
    }
}
