package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.BruteContact;
import com.codex.blacklight.combat.HydraContact;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.client.tuning.ClawTransformSettings;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A planted climbing grip on the authored Brute back, preserving bone lengths. */
public final class BruteRiderPose {
    private BruteRiderPose(){}
    private static final Map<PowerForm,Map<String,List<Vector3f>>> MESHES=new EnumMap<>(PowerForm.class);
    private record BackBox(Vec3 center,Vec3 half,Vec3 right,Vec3 up,Vec3 back){
        static BackBox from(HydraContact.Box box){
            var p=box.localPoint(box.points.get(0));
            return new BackBox(box.center,new Vec3(Math.abs(p.x),Math.abs(p.y),Math.abs(p.z)),
                    box.worldPoint(new Vec3(1,0,0)).subtract(box.center),box.worldPoint(new Vec3(0,1,0)).subtract(box.center),box.worldPoint(new Vec3(0,0,1)).subtract(box.center));
        }
        double gap(double wx,double wy,double wz){
            double dx=wx-center.x,dy=wy-center.y,dz=wz-center.z;
            double x=Math.abs(dx*right.x+dy*right.y+dz*right.z)-half.x;
            double y=Math.abs(dx*up.x+dy*up.y+dz*up.z)-half.y;
            double z=Math.abs(dx*back.x+dy*back.y+dz*back.z)-half.z;
            if(x<=0&&y<=0&&z<=0)return Math.max(x,Math.max(y,z));
            return Math.sqrt(Math.max(0,x)*Math.max(0,x)+Math.max(0,y)*Math.max(0,y)+Math.max(0,z)*Math.max(0,z));
        }
    }
    private static double gap(List<BackBox> boxes,Vec3 point){return gap(boxes,point.x,point.y,point.z);}
    private static double gap(List<BackBox> boxes,double x,double y,double z){double result=Double.POSITIVE_INFINITY;for(var box:boxes)result=Math.min(result,box.gap(x,y,z));return result;}
    private static Map<String,List<Vector3f>> mesh(PowerForm form){
        return MESHES.computeIfAbsent(form,f->{
            String asset=f==PowerForm.CLAWS?"claw_blender":"form_"+f.id();
            try(var in=BruteRiderPose.class.getResourceAsStream("/assets/blacklight/models/"+asset+".json")){
                var result=new HashMap<String,List<Vector3f>>();
                var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
                for(var entry:root.getAsJsonObject("joints").entrySet()){
                    var points=new LinkedHashSet<Vector3f>();
                    for(var face:entry.getValue().getAsJsonArray())for(var v:face.getAsJsonObject().getAsJsonArray("p")){
                        var p=v.getAsJsonArray();points.add(new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
                    }
                    result.put(entry.getKey(),List.copyOf(points));
                }
                return result;
            }catch(java.io.IOException e){throw new IllegalStateException("Cannot load mounted grip mesh",e);}
        });
    }
    private static double meshGap(BlacklightSkeleton rig,BlacklightPose pose,String side,PowerForm form,List<BackBox> boxes,Matrix4f frame,Vec3 actor){
        if(form!=PowerForm.CLAWS&&form!=PowerForm.MUSCLE_MASS)return .01;
        boolean claw=form==PowerForm.CLAWS;var handSide=side.equals("R")?ClawTransformSettings.HandSide.RIGHT:ClawTransformSettings.HandSide.LEFT;
        float width=claw?(float)ClawTransformSettings.limbWidth(handSide):1,length=claw?(float)ClawTransformSettings.talonLength(handSide):1;
        double result=Double.POSITIVE_INFINITY;
        for(String prefix:List.of("Arm_","Hand_")){
            String name=prefix+side;boolean lower=prefix.equals("Hand_");
            var matrix=rig.getBoundTransformFor(pose,rig.searchJointByName(name));Matrix4f wrist=null;
            if(claw&&lower){var tip=rig.searchJointByName("Tool_"+side);wrist=new Matrix4f(matrix).invert().mul(rig.getBoundTransformFor(pose,tip)).mul(new Matrix4f(tip.getLocalTransform()).invert());}
            matrix=new Matrix4f(frame).mul(matrix).scale(width,1,width);
            for(var point:mesh(form).getOrDefault(name,List.of())){
                var p=new Vector3f(point);if(lower&&p.y>.30F)p.y=.30F+(p.y-.30F)*length;
                if(wrist!=null)p.lerp(wrist.transformPosition(new Vector3f(p)),ease((point.y-.28F)/.10F));
                matrix.transformPosition(p);result=Math.min(result,gap(boxes,actor.x+p.x,actor.y+p.y,actor.z+p.z));
            }
        }
        return result;
    }
    private static void outsideSpine(BlacklightSkeleton rig,BlacklightPose pose,List<BackBox> boxes,Matrix4f frame,Vec3 actor){
        var torso=pose.orElseEmpty("Torso").copy();var chest=pose.orElseEmpty("Chest").copy();
        float lo=0,hi=1,weight=1;
        for(int i=0;i<10;i++){
            pose.orElseEmpty("Torso").rotation().identity().slerp(torso.rotation(),weight);
            pose.orElseEmpty("Chest").rotation().identity().slerp(chest.rotation(),weight);
            boolean clear=true;
            for(String name:List.of("Head","Arm_R","Arm_L")){
                var p=rig.getBoundTransformFor(pose,rig.searchJointByName(name)).getTranslation(new Vector3f());frame.transformPosition(p);
                if(gap(boxes,actor.add(p.x,p.y,p.z))<(name.equals("Head")?.34:.20)){clear=false;break;}
            }
            if(clear&&i==0)return;
            if(clear)lo=weight;else hi=weight;
            weight=(lo+hi)/2;
        }
        pose.orElseEmpty("Torso").rotation().identity().slerp(torso.rotation(),lo);
        pose.orElseEmpty("Chest").rotation().identity().slerp(chest.rotation(),lo);
    }
    private static float ease(float v){v=net.minecraft.util.Mth.clamp(v,0,1);return v*v*(3-2*v);}
    private static Vec3 back(List<BruteContact.Surface> surfaces,Matrix4f victimFrame,Vec3 origin,float x,float y){
        var q=victimFrame.transformPosition(new Vector3f(x,y,2));var query=origin.add(q.x,q.y,q.z);
        Vec3 best=query;double distance=Double.POSITIVE_INFINITY;
        for(var surface:surfaces){if(!surface.bone().equals("torso"))continue;
            var point=surface.box().nearest(query);double d=point.distanceToSqr(query);if(d<distance){distance=d;best=point;}
        }
        return best;
    }
    private static Vector3f local(Vec3 world,Vec3 actor,Matrix4f inverse){return inverse.transformPosition(world.subtract(actor).toVector3f());}
    private static void turn(BlacklightSkeleton rig,BlacklightPose pose,BlacklightSkeleton.Bone bone,Quaternionf rotation){
        var q=pose.orElseEmpty(bone.getName()).rotation();
        var world=rig.getBoundTransformFor(pose,bone).getUnnormalizedRotation(new Quaternionf()).normalize();
        var basis=new Quaternionf(world).mul(new Quaternionf(q).invert());
        q.premul(new Quaternionf(basis).invert().mul(rotation).mul(basis)).normalize();
    }
    private static void outsideElbow(BlacklightSkeleton rig,BlacklightPose pose,String side){
        var upper=rig.searchJointByName("Arm_"+side);
        var shoulder=rig.getBoundTransformFor(pose,upper).getTranslation(new Vector3f());
        var elbow=rig.getBoundTransformFor(pose,rig.searchJointByName("Hand_"+side)).getTranslation(new Vector3f());
        var tip=rig.getBoundTransformFor(pose,rig.searchJointByName("Tool_"+side)).getTranslation(new Vector3f());
        var axis=tip.sub(shoulder,new Vector3f()).normalize();
        var from=elbow.sub(shoulder,new Vector3f());from.fma(-from.dot(axis),axis);
        var to=new Vector3f(side.equals("R")?.3F:-.3F,-.5F,.7F);to.fma(-to.dot(axis),axis);
        if(from.lengthSquared()>1e-8F&&to.lengthSquared()>1e-8F)
            turn(rig,pose,upper,new Quaternionf().rotationTo(from.normalize(),to.normalize()));
    }
    public static void apply(BlacklightPose pose,Vec3 actor,Vec3 victim,float yaw,float[][] body,boolean consuming,float progress,PowerForm form){
        var rig=BlacklightSkeleton.get();
        if(!consuming)pose.getJointTransformData().clear();
        // Lean the pelvis into the grip while retaining the collision-aware entity anchor.
        var root=pose.orElseEmpty("Root");root.rotation().identity();root.scale().set(1);
        root.translation().set(new Matrix4f(rig.rootJoint.getLocalTransform()).invert().transformDirection(new Vector3f(0,0,-.24F)));
        var frame=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(.9375F);
        var inverse=new Matrix4f(frame).invert();var victimFrame=new Matrix4f().rotateY((float)Math.toRadians(180-yaw));
        var away=victimFrame.transformDirection(new Vector3f(0,0,1));var backNormal=new Vec3(away.x,away.y,away.z);
        var surfaces=BruteContact.body(body,victim,yaw);
        var boxes=surfaces.stream().filter(s->s.bone().equals("torso")).map(s->BackBox.from(s.box())).toList();
        if(consuming)outsideSpine(rig,pose,boxes,frame,actor);
        for(String side:List.of("R","L")){
            float sign=side.equals("R")?1:-1;
            var thigh=rig.searchJointByName("Thigh_"+side);var leg=rig.searchJointByName("Leg_"+side);
            var hip=rig.getBoundTransformFor(pose,thigh).getTranslation(new Vector3f());
            var knee=rig.getBoundTransformFor(pose,leg).getTranslation(new Vector3f());float length=hip.distance(knee)*.9375F;
            var at=frame.transformPosition(new Vector3f(hip));var hipWorld=actor.add(at.x,at.y,at.z);
            Vec3 goal=back(surfaces,victimFrame,victim,sign*.27F,(float)(hipWorld.y-victim.y-.2)).add(backNormal.scale(.14));
            for(int attempt=0;attempt<3;attempt++){
                double dx=goal.x-hipWorld.x,dz=goal.z-hipWorld.z,horizontal=dx*dx+dz*dz;
                if(horizontal>length*length*.98){double scale=length*.989949/Math.sqrt(horizontal);dx*=scale;dz*=scale;}
                // A climbing knee rises toward the back; the lower solution hangs below the torso.
                double y=hipWorld.y+Math.sqrt(Math.max(0,length*length-dx*dx-dz*dz));
                goal=new Vec3(hipWorld.x+dx,y,hipWorld.z+dz);
                if(attempt<2)goal=back(surfaces,victimFrame,victim,sign*.27F,(float)(y-victim.y)).add(backNormal.scale(.14));
            }
            var wanted=local(goal,actor,inverse).sub(hip).normalize();
            turn(rig,pose,thigh,new Quaternionf().rotationTo(knee.sub(hip,new Vector3f()).normalize(),wanted));
            // Calves hang outside the back instead of folding farther through it.
            var restDown=rig.getBoundTransformFor(new BlacklightPose(),leg).invert().transformDirection(new Vector3f(0,-1,0));
            var down=rig.getBoundTransformFor(pose,leg).transformDirection(restDown).normalize();
            turn(rig,pose,leg,new Quaternionf().rotationTo(down,new Vector3f(0,-1,.22F).normalize()));
            float grip=side.equals("L")||!consuming?1:net.minecraft.util.Mth.clamp(1-ease((progress-.12F)/.12F)+ease((progress-.82F)/.14F),0,1);
            if(grip>0){
                var contact=back(surfaces,victimFrame,victim,sign*.28F,2.02F);var before=pose.copy();
                double lo=.07,hi=.47;boolean found=false;
                for(int i=0;i<=8;i++){
                    double offset=.07+i*.05;
                    BlacklightBoneIk.reachTwoBone(rig,pose,side,local(contact.add(backNormal.scale(offset)),actor,inverse),new Vector3f());outsideElbow(rig,pose,side);
                    if(meshGap(rig,pose,side,form,boxes,frame,actor)>=.008){hi=offset;found=true;break;}lo=offset;
                }
                if(found)for(int i=0;i<6&&hi-lo>.0005;i++){
                    double offset=(lo+hi)/2;
                    BlacklightBoneIk.reachTwoBone(rig,pose,side,local(contact.add(backNormal.scale(offset)),actor,inverse),new Vector3f());outsideElbow(rig,pose,side);
                    if(meshGap(rig,pose,side,form,boxes,frame,actor)>=.008)hi=offset;else lo=offset;
                }
                BlacklightBoneIk.reachTwoBone(rig,pose,side,local(contact.add(backNormal.scale(hi)),actor,inverse),new Vector3f());outsideElbow(rig,pose,side);
                if(grip<1){var mixed=BlacklightPose.interpolatePose(before,pose,grip);for(String name:List.of("Arm_"+side,"Hand_"+side))pose.getJointTransformData().put(name,mixed.orElseEmpty(name).copy());}
            }
        }
    }
}
