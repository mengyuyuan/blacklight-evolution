package com.codex.blacklight.combat;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.WeaponAttackScales;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared collision response for the scaled hammer mesh; body and damage queries use the same pose. */
public final class HammerGroundPose {
    private static final Map<String,List<Vector3f>> ENVELOPES=load();
    private HammerGroundPose() {}
    private static Map<String,List<Vector3f>> load(){
        try(var in=HammerGroundPose.class.getResourceAsStream("/assets/blacklight/models/form_hammerfists.json")){
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
        return minimum(pose,left,WeaponAttackScales.Shape.IDENTITY);
    }
    public static float minimum(BlacklightPose pose,boolean left,WeaponAttackScales.Shape scale){
        return minimum(pose,left,handEnvelope(left,scale));
    }
    private static List<Vector3f> handEnvelope(boolean left,WeaponAttackScales.Shape scale){
        var vertices=ENVELOPES.getOrDefault(left?"Hand_L":"Hand_R",List.of());
        if(scale.equals(WeaponAttackScales.Shape.IDENTITY))return vertices;
        return vertices.stream().map(p->scale.point(new Vector3f(p))).toList();
    }
    private static float minimum(BlacklightPose pose,boolean left,List<Vector3f> hand){
        var rig=BlacklightSkeleton.get();String side=left?"L":"R";float low=Float.POSITIVE_INFINITY;
        for(String name:List.of("Arm_"+side,"Hand_"+side)){
            var m=rig.getBoundTransformFor(pose,rig.searchJointByName(name));
            for(var p:name.startsWith("Hand")?hand:ENVELOPES.getOrDefault(name,List.of()))low=Math.min(low,m.m01()*p.x+m.m11()*p.y+m.m21()*p.z+m.m31());
        }
        return low;
    }
    public static void apply(BlacklightPose pose){
        apply(pose,WeaponAttackScales.Shape.IDENTITY,WeaponAttackScales.Shape.IDENTITY);
    }
    /** Enter only after the last contact window; the strike and its server sweep stay identical. */
    public static void quakeRecovery(BlacklightPose pose,String key,float seconds,
                                     WeaponAttackScales.Shape right,WeaponAttackScales.Shape left){
        if(!key.equals("hammer_quake")&&!key.equals("hammer_quake_charged"))return;
        float end=LocalWeaponMotion.find(key).orElseThrow().contactEndTick()/20F;
        float weight=smoothStep((seconds-end)/.2F);
        if(weight<=0)return;
        var original=pose.copy();apply(pose,right,left);
        if(weight<1){
            var blended=BlacklightPose.interpolatePose(original,pose,weight);
            for(String bone:List.of("Torso","Arm_R","Arm_L"))pose.getJointTransformData().put(bone,blended.orElseEmpty(bone).copy());
        }
    }
    private static float smoothStep(float t){
        t=Math.max(0,Math.min(1,t));
        return t*t*(3-2*t);
    }
    private static void apply(BlacklightPose pose,WeaponAttackScales.Shape right,WeaponAttackScales.Shape leftScale){
        stabilize(pose,right,leftScale,.012F,.012F,1,false);
    }
    public static void stabilize(BlacklightPose pose,WeaponAttackScales.Shape right,WeaponAttackScales.Shape leftScale,
                                 float rightFloor,float leftFloor,float unfold){
        stabilize(pose,right,leftScale,rightFloor,leftFloor,unfold,true);
    }
    private static void stabilize(BlacklightPose pose,WeaponAttackScales.Shape right,WeaponAttackScales.Shape leftScale,
                                  float rightFloor,float leftFloor,float unfold,boolean seatAtSurface){
        stabilize(pose,right,leftScale,unfold,seatAtSurface,
                (p,left,hand)->minimum(p,left,hand)-(left?leftFloor:rightFloor));
    }
    private static void stabilize(BlacklightPose pose,WeaponAttackScales.Shape right,WeaponAttackScales.Shape leftScale,
                                  float unfold,boolean seatAtSurface,Clearance ground){
        var rightHand=handEnvelope(false,right);var leftHand=handEnvelope(true,leftScale);
        float rightGap=ground.at(pose,false,rightHand),leftGap=ground.at(pose,true,leftHand);
        var rig=BlacklightSkeleton.get();
        if(unfold==0){
            // The retargeted stroke already carries the shoulder rotation. Correct only
            // surface penetration along the terrain normal, so a nearly vertical elbow
            // cannot flip the correction axis at impact.
            for(boolean left:new boolean[]{false,true}){
                float gap=left?leftGap:rightGap;
                if(gap>=0||!Float.isFinite(gap))continue;
                var bone=left?rig.armL:rig.armR;
                var parent=rig.getBoundTransformFor(pose,bone.parent()).mul(bone.getLocalTransform());
                var adjusted=pose.orElseEmpty(bone.getName()).copy();
                adjusted.translation().add(parent.invert().transformDirection(new Vector3f(0,-gap,0)));
                pose.getJointTransformData().put(bone.getName(),adjusted);
            }
            return;
        }
        float penetration=-Math.min(rightGap,leftGap);
        if(penetration>0&&unfold>0){
            // The source recovery folds a narrow human torso nearly onto the
            // floor. Unfold the spine for the much wider Minecraft fists while
            // leaving the hip and both legs at their authored planted positions.
            var bone=rig.torso;var world=rig.getBoundTransformFor(pose,bone);
            var pivot=world.getTranslation(new Vector3f());
            var spine=rig.getBoundTransformFor(pose,rig.head).getTranslation(new Vector3f()).sub(pivot);
            if(spine.lengthSquared()>1e-6F){
                var turn=new org.joml.Quaternionf().slerp(new org.joml.Quaternionf().rotationTo(spine.normalize(),new Vector3f(0,1,0)),Math.max(0,Math.min(1,penetration/.25F))*unfold);
                var parent=rig.getBoundTransformFor(pose,bone.parent()).mul(bone.getLocalTransform());
                var corrected=new Matrix4f().translation(pivot).rotate(turn).translate(-pivot.x,-pivot.y,-pivot.z).mul(world);
                pose.getJointTransformData().put(bone.getName(),BoneTransform.fromMatrix(parent.invert().mul(corrected)));
            }
        }
        for(boolean left:new boolean[]{false,true}){
            var hand=left?leftHand:rightHand;
            float initial=ground.at(pose,left,hand);
            if(!Float.isFinite(initial))continue;
            // After the torso unfolds, a grazing fist may already be clear. Ease it
            // onto the surface as penetration grows instead of snapping down on first contact.
            float seat=seatAtSurface?smoothStep(-(left?leftGap:rightGap)/.29F):0;
            if(initial>=0&&seat==0)continue;
            boolean raise=initial<0;
            float sign=raise?1:-1;
            String name=left?"Arm_L":"Arm_R";
            var original=pose.orElseEmpty(name).copy();
            var parent=rig.getBoundTransformFor(pose,rig.searchJointByName(name).parent());
            var bind=rig.searchJointByName(name).getLocalTransform();
            var world=new Matrix4f(parent).mul(bind).mul(original.toMatrix());
            var inverse=new Matrix4f(parent).mul(bind).invert();
            var pivot=world.getTranslation(new Vector3f());
            var elbow=rig.getBoundTransformFor(pose,rig.searchJointByName(left?"Hand_L":"Hand_R")).getTranslation(new Vector3f());
            // Raise around the shoulder using the arm's horizontal direction.
            var horizontal=elbow.sub(pivot);horizontal.y=0;
            if(horizontal.lengthSquared()<1e-6F)horizontal.set(left?-1:1,0,0);
            var axis=horizontal.normalize().cross(new Vector3f(0,1,0)).normalize();
            float best=initial;var bestPose=original;
            for(int degrees=2;degrees<=180;degrees+=2){
                var correction=new Matrix4f().translation(pivot).rotate((float)Math.toRadians(degrees*sign),axis).translate(-pivot.x,-pivot.y,-pivot.z);
                var candidate=BoneTransform.fromMatrix(new Matrix4f(inverse).mul(correction).mul(world));
                pose.getJointTransformData().put(name,candidate);
                float low=ground.at(pose,left,hand);
                if(raise?low>best:low>=0&&low<best){best=low;bestPose=candidate;}
                if(raise?low>=0:low<=0){
                    float a=degrees-2,b=degrees;
                    for(int step=0;step<10;step++){
                        float mid=(a+b)*.5F;
                        var refine=new Matrix4f().translation(pivot).rotate((float)Math.toRadians(mid*sign),axis).translate(-pivot.x,-pivot.y,-pivot.z);
                        var refined=BoneTransform.fromMatrix(new Matrix4f(inverse).mul(refine).mul(world));
                        pose.getJointTransformData().put(name,refined);
                        if(ground.at(pose,left,hand)>=0){bestPose=refined;if(raise)b=mid;else a=mid;}
                        else if(raise)a=mid;else b=mid;
                    }
                    break;
                }
            }
            if(!raise)bestPose=BoneTransform.blend(original,bestPose,seat);
            pose.getJointTransformData().put(name,bestPose);
            // At the bottom of a deep strike the shoulder pivot itself can be too low
            // for arm rotation alone. Clear the remaining gap along model-space up.
            float clearance=ground.at(pose,left,hand);
            if(seatAtSurface&&clearance<-.0001F){
                var lift=inverse.transformDirection(new Vector3f(0,-clearance,0));
                bestPose.translation().add(lift);
            }
        }
    }

    @FunctionalInterface private interface Clearance {
        float at(BlacklightPose pose,boolean left,List<Vector3f> hand);
    }
    /** Re-evaluate support at each candidate vertex, including after the arm rotates.
     * Cache collision columns for this solve only: block changes are visible next frame. */
    private static final class GroundSupport implements Clearance {
        private final net.minecraft.world.entity.LivingEntity actor;
        private final net.minecraft.world.phys.Vec3 feet;
        private final Matrix4f frame;
        private final float scale;
        private final Map<Long,List<net.minecraft.world.phys.AABB>> columns=new HashMap<>();
        GroundSupport(net.minecraft.world.entity.LivingEntity actor,net.minecraft.world.phys.Vec3 feet,float yaw){
            this.actor=actor;this.feet=feet;scale=.9375F*actor.getScale();
            frame=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(scale);
        }
        private double top(double x,double z){
            int bx=net.minecraft.util.Mth.floor(x),bz=net.minecraft.util.Mth.floor(z);
            long key=((long)bx<<32)^(bz&0xffffffffL);
            var boxes=columns.computeIfAbsent(key,k->{
                var result=new ArrayList<net.minecraft.world.phys.AABB>();
                var context=net.minecraft.world.phys.shapes.CollisionContext.of(actor);
                for(int y=net.minecraft.util.Mth.floor(feet.y-1.25)-1;y<=net.minecraft.util.Mth.floor(feet.y+1.25);y++){
                    var pos=new net.minecraft.core.BlockPos(bx,y,bz);
                    for(var box:actor.level().getBlockState(pos).getCollisionShape(actor.level(),pos,context).toAabbs()){
                        var world=box.move(pos);
                        if(world.maxY>=feet.y-1.25&&world.maxY<=feet.y+1.25)result.add(world);
                    }
                }
                return result;
            });
            double top=Double.NEGATIVE_INFINITY;
            for(var box:boxes)if(x>=box.minX&&x<=box.maxX&&z>=box.minZ&&z<=box.maxZ)top=Math.max(top,box.maxY);
            return top;
        }
        @Override public float at(BlacklightPose pose,boolean left,List<Vector3f> hand){
            var rig=BlacklightSkeleton.get();String side=left?"L":"R";
            float gap=Float.POSITIVE_INFINITY;var point=new Vector3f();
            for(String name:List.of("Arm_"+side,"Hand_"+side)){
                var matrix=new Matrix4f(frame).mul(rig.getBoundTransformFor(pose,rig.searchJointByName(name)));
                for(var vertex:name.startsWith("Hand")?hand:ENVELOPES.getOrDefault(name,List.of())){
                    matrix.transformPosition(vertex,point);
                    double surface=top(feet.x+point.x,feet.z+point.z);
                    if(Double.isFinite(surface))gap=Math.min(gap,(float)((feet.y+point.y-surface)/scale)-.012F);
                }
            }
            return gap;
        }
    }
    public static boolean quake(String key){return key.equals("hammer_quake")||key.equals("hammer_quake_charged");}
    public static void quake(net.minecraft.world.entity.LivingEntity actor,BlacklightPose pose,String key,float seconds,
                             net.minecraft.world.phys.Vec3 feet,float yaw,WeaponAttackScales.Shape right,WeaponAttackScales.Shape left){
        if(key.equals("hammer_combo_special")||key.equals("hammer_combo_special_charged")){
            var landing=LocalWeaponMotion.find(key).orElseThrow().landing().orElseThrow();
            if(seconds>=landing.impactTime())
                stabilize(pose,right,left,.35F,true,new GroundSupport(actor,feet,yaw));
            return;
        }
        if(!quake(key)||!actor.onGround())return;
        // Adapt the arms to the actual surface. The authored pelvis and spine carry the strike.
        stabilize(pose,right,left,0,true,new GroundSupport(actor,feet,yaw));
    }
    /** All fist vertices in model space, after the same nonuniform scale as the renderer. */
    public static java.util.List<Vector3f> fist(BlacklightPose pose,WeaponAttackScales.Shape shape,boolean left){
        var rig=BlacklightSkeleton.get();var matrix=rig.getBoundTransformFor(pose,left?rig.handL:rig.handR);
        return handEnvelope(left,shape).stream().map(p->matrix.transformPosition(new Vector3f(p))).toList();
    }
}
