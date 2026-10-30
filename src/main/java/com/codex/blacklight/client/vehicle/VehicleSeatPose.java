package com.codex.blacklight.client.vehicle;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.BoneTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Player vehicle poses follow the existing soldier baseline at normal model scale. */
public final class VehicleSeatPose {
    private VehicleSeatPose() {}
    public record Draw(int playerId,int vehicleId,String model,float rightLeg,float leftLeg,float headY,boolean headVisible) {}
    private static Draw drawn;
    public static Draw drawn(){return drawn;}
    /** Opt-in smoke samples the chosen real Player renderer after posing, before its draw. */
    public static void capture(net.minecraft.world.entity.player.Player player,HumanoidModel<?> model) {
        if(Boolean.getBoolean("blacklight.test.vehicleView"))drawn=new Draw(player.getId(),player.getVehicle().getId(),model.getClass().getName(),
                model.rightLeg.xRot,model.leftLeg.xRot,model.head.y,model.head.visible);
    }
    /** A cosmetic 3m body must use this Player's physical proportions inside a vehicle. */
    public static float appearanceScale(float physicalHeight,float restHeight,float requested,boolean mounted) {
        return mounted?Math.min(requested,physicalHeight/(.9375F*restHeight)):requested;
    }
    /** PlayerRenderer shares model instances across people and hand passes. */
    public static final class Saved implements AutoCloseable {
        private final HumanoidModel<?> model;
        private final net.minecraft.client.model.geom.ModelPart[] parts;
        private final net.minecraft.client.model.geom.PartPose[] poses;
        private final float attack,swim;
        private final boolean crouching;
        private Saved(HumanoidModel<?> model) {
            this.model=model;attack=model.attackTime;swim=model.swimAmount;crouching=model.crouching;
            var list=new java.util.ArrayList<>(java.util.List.of(model.head,model.hat,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg));
            if(model instanceof PlayerModel<?> player)list.addAll(java.util.List.of(player.jacket,player.rightSleeve,player.leftSleeve,player.rightPants,player.leftPants));
            parts=list.toArray(net.minecraft.client.model.geom.ModelPart[]::new);poses=new net.minecraft.client.model.geom.PartPose[parts.length];
            for(int i=0;i<parts.length;i++)poses[i]=parts[i].storePose();
        }
        @Override public void close() {
            for(int i=0;i<parts.length;i++)parts[i].loadPose(poses[i]);
            model.attackTime=attack;model.swimAmount=swim;model.crouching=crouching;
        }
    }
    public static Saved save(HumanoidModel<?> model){return new Saved(model);}
    public static float drop(com.codex.blacklight.military.vehicle.VehicleDefinition vehicle){return vehicle.id().equals("truck")?0:5;}
    public static void apply(HumanoidModel<?> model,boolean driver,float yaw,float pitch) {
        apply(model,driver,yaw,pitch,0);
    }
    public static void apply(HumanoidModel<?> model,boolean driver,float yaw,float pitch,float drop) {
        model.attackTime=0;model.crouching=false;model.swimAmount=0;
        model.body.setPos(0,drop,0);model.body.setRotation(0,0,0);
        model.head.setPos(0,drop,0);model.head.setRotation(radians(clamp(pitch,-45,45)),radians(clamp(yaw,-75,75)),0);
        for(var arm:new net.minecraft.client.model.geom.ModelPart[]{model.rightArm,model.leftArm}) {
            var rest=arm.getInitialPose();arm.setPos(rest.x,rest.y+drop,rest.z);arm.setRotation(driver?-1.05F:-.35F,0,0);
        }
        model.rightLeg.setPos(-1.9F,11+drop,0);model.leftLeg.setPos(1.9F,11+drop,0);
        model.rightLeg.setRotation(-(float)Math.PI/2,0,0);model.leftLeg.setRotation(-(float)Math.PI/2,0,0);
        model.hat.copyFrom(model.head);
        if(model instanceof PlayerModel<?> player) {
            player.jacket.copyFrom(player.body);player.rightSleeve.copyFrom(player.rightArm);player.leftSleeve.copyFrom(player.leftArm);
            player.rightPants.copyFrom(player.rightLeg);player.leftPants.copyFrom(player.leftLeg);
        }
    }
    /** The native skin and the dedicated YSM bridge sample this same seated skeleton. */
    public static BlacklightPose skeleton(boolean driver,float yaw,float pitch) {
        return skeleton(driver,yaw,pitch,0);
    }
    public static BlacklightPose skeleton(boolean driver,float yaw,float pitch,float drop) {
        var skeleton=BlacklightSkeleton.get();var bind=skeleton.getPoseMatrices(new BlacklightPose());
        var target=new Matrix4f[bind.length];var pose=new BlacklightPose();
        for(var bone:skeleton.bones()) {
            var parent=bone.parent();var base=parent==null?bone.getLocalTransform():new Matrix4f(target[parent.getId()]).mul(bone.getLocalTransform());
            var point=bind[bone.getId()].getTranslation(new Vector3f());
            Matrix4f change=switch(bone.getName()) {
                case "Head" -> new Matrix4f().translation(point).rotateY(-radians(clamp(yaw,-75,75))).rotateX(-radians(clamp(pitch,-45,45))).translate(-point.x,-point.y,-point.z);
                case "Arm_R","Arm_L" -> new Matrix4f().translation(point).rotateX(driver?1.05F:.35F).translate(-point.x,-point.y,-point.z);
                case "Thigh_R","Thigh_L" -> new Matrix4f().translation(point.x,point.y+1/16F,point.z).rotateX((float)Math.PI/2).translate(-point.x,-point.y,-point.z);
                default -> null;
            };
            target[bone.getId()]=change==null?new Matrix4f(base):change.mul(bind[bone.getId()]);
            pose.getJointTransformData().put(bone.getName(),BoneTransform.fromMatrix(new Matrix4f(base).invert().mul(target[bone.getId()])));
        }
        pose.getJointTransformData().put("Root",BoneTransform.fromMatrix(new Matrix4f().translation(0,-drop/16F,0)));
        return pose;
    }
    private static float radians(float degrees){return degrees*(float)Math.PI/180;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
