package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Keep the enlarged hammer mesh above the planted floor during ram recovery. */
public final class HammerGroundContact {
    private static final Map<String,List<Vector3f>> ENVELOPES=load();
    private HammerGroundContact() {}
    private static Map<String,List<Vector3f>> load(){
        try(var in=HammerGroundContact.class.getResourceAsStream("/assets/blacklight/models/form_hammerfists.json")){
            var mesh=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new HashMap<String,List<Vector3f>>();
            for(var entry:mesh.getAsJsonObject("joints").entrySet()){
                var vertices=new LinkedHashSet<Vector3f>();
                for(var face:entry.getValue().getAsJsonArray())for(var vertex:face.getAsJsonObject().getAsJsonArray("p")){
                    var a=vertex.getAsJsonArray();vertices.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
                }
                result.put(entry.getKey(),List.copyOf(vertices));
            }
            return Map.copyOf(result);
        }catch(java.io.IOException ex){throw new IllegalStateException("Missing hammer ground envelope",ex);}
    }
    public static float minimum(BlacklightPose pose,boolean left){
        var rig=BlacklightSkeleton.get();String side=left?"L":"R";float low=Float.POSITIVE_INFINITY;
        for(String name:List.of("Arm_"+side,"Hand_"+side)){
            var m=rig.getBoundTransformFor(pose,rig.searchJointByName(name));
            for(var p:ENVELOPES.getOrDefault(name,List.of()))low=Math.min(low,m.m01()*p.x+m.m11()*p.y+m.m21()*p.z+m.m31());
        }
        return low;
    }
    public static void apply(BlacklightPose pose){
        var rig=BlacklightSkeleton.get();
        float penetration=.012F-Math.min(minimum(pose,false),minimum(pose,true));
        if(penetration>0){
            // The source recovery folds a narrow human torso nearly onto the
            // floor. Unfold the spine for the much wider Minecraft fists while
            // leaving the hip and both legs at their authored planted positions.
            var bone=rig.torso;var world=rig.getBoundTransformFor(pose,bone);
            var pivot=world.getTranslation(new Vector3f());
            var spine=rig.getBoundTransformFor(pose,rig.head).getTranslation(new Vector3f()).sub(pivot);
            if(spine.lengthSquared()>1e-6F){
                var turn=new org.joml.Quaternionf().slerp(new org.joml.Quaternionf().rotationTo(spine.normalize(),new Vector3f(0,1,0)),com.codex.blacklight.util.MathCompat.clamp(penetration/.25F,0,1));
                var parent=rig.getBoundTransformFor(pose,bone.parent()).mul(bone.getLocalTransform());
                var corrected=new Matrix4f().translation(pivot).rotate(turn).translate(-pivot.x,-pivot.y,-pivot.z).mul(world);
                pose.getJointTransformData().put(bone.getName(),BoneTransform.fromMatrix(parent.invert().mul(corrected)));
            }
        }
        for(boolean left:new boolean[]{false,true}){
            if(minimum(pose,left)>=.012F)continue;
            String name=left?"Arm_L":"Arm_R";
            var original=pose.orElseEmpty(name).copy();
            var parent=rig.getBoundTransformFor(pose,rig.searchJointByName(name).parent());
            var bind=rig.searchJointByName(name).getLocalTransform();
            var world=new Matrix4f(parent).mul(bind).mul(original.toMatrix());
            var pivot=world.getTranslation(new Vector3f());
            var elbow=rig.getBoundTransformFor(pose,rig.searchJointByName(left?"Hand_L":"Hand_R")).getTranslation(new Vector3f());
            // Raise around the shoulder using the arm's horizontal direction.
            var horizontal=elbow.sub(pivot);horizontal.y=0;
            if(horizontal.lengthSquared()<1e-6F)horizontal.set(left?-1:1,0,0);
            var axis=horizontal.normalize().cross(new Vector3f(0,1,0)).normalize();
            var inverse=new Matrix4f(parent).mul(bind).invert();
            float best=minimum(pose,left);var bestPose=original;
            for(int degrees=2;degrees<=180;degrees+=2){
                var correction=new Matrix4f().translation(pivot).rotate((float)Math.toRadians(degrees),axis).translate(-pivot.x,-pivot.y,-pivot.z);
                var candidate=BoneTransform.fromMatrix(new Matrix4f(inverse).mul(correction).mul(world));
                pose.getJointTransformData().put(name,candidate);
                float low=minimum(pose,left);
                if(low>best){best=low;bestPose=candidate;}
                if(low>=.012F){
                    float a=degrees-2,b=degrees;
                    for(int step=0;step<10;step++){
                        float mid=(a+b)*.5F;
                        var refine=new Matrix4f().translation(pivot).rotate((float)Math.toRadians(mid),axis).translate(-pivot.x,-pivot.y,-pivot.z);
                        var refined=BoneTransform.fromMatrix(new Matrix4f(inverse).mul(refine).mul(world));
                        pose.getJointTransformData().put(name,refined);
                        if(minimum(pose,left)>=.012F){b=mid;bestPose=refined;}else a=mid;
                    }
                    break;
                }
            }
            pose.getJointTransformData().put(name,bestPose);
        }
    }
}
