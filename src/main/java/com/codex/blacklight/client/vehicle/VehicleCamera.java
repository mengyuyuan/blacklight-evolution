package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.entity.MilitaryVehicle;
import java.util.function.BiFunction;

/** Vehicle-local observation and chase camera geometry. */
public final class VehicleCamera {
    public record View(Vec3 eye,Vec3 pivot,Vec3 position,float yaw,float pitch,boolean enclosed) {}
    private VehicleCamera() {}
    public static View view(VehicleDefinition definition,String seat,Vec3 origin,float bodyYaw,float turretYaw,float gunPitch,
                            float yaw,float pitch,boolean first,boolean reverse) {
        var location=definition.seats().stream().filter(s->s.id().equals(seat)).findFirst().orElseThrow();
        var at=definition.mountWorld(location.anchorId(),new VehicleDefinition.Vec3(origin.x,origin.y,origin.z),bodyYaw,turretYaw,gunPitch);
        var eye=new Vec3(at.x(),at.y()+1.62,at.z());
        if(first) {
            String id="camera_"+seat;var anchor=definition.anchors().get(id);
            Vec3 optic=eye;
            if(anchor!=null) {
                var p=definition.mountWorld(id,new VehicleDefinition.Vec3(origin.x,origin.y,origin.z),bodyYaw,turretYaw,gunPitch);
                optic=new Vec3(p.x(),p.y(),p.z());
            }
            boolean turret=anchor!=null&&anchor.frame()!=VehicleDefinition.Frame.BODY;
            float heading=-bodyYaw-(turret?turretYaw:0);
            boolean sidePort=definition.id().equals("apc")&&location.job()==VehicleDefinition.Job.PASSENGER;
            if(sidePort&&anchor!=null)heading+=anchor.position().x()<0?90:-90;
            float relative=sidePort?Mth.wrapDegrees(yaw+bodyYaw):Mth.wrapDegrees(yaw-heading);
            float viewYaw=definition.id().equals("truck")?yaw:heading+Mth.clamp(relative,-50,50);
            float viewPitch=location.job()==VehicleDefinition.Job.GUNNER||turret&&anchor.frame()==VehicleDefinition.Frame.GUN_PITCH?gunPitch:Mth.clamp(pitch,-25,25);
            return new View(eye,optic,optic,viewYaw,viewPitch,!definition.id().equals("truck"));
        }
        // Orbit outside the tallest authored body/turret, looking back at the real chassis.
        var pivot=origin.add(0,definition.height()+.35,0);
        float viewYaw=Mth.wrapDegrees(yaw+(reverse?180:0));
        double distance=Math.hypot(definition.width(),definition.length())*.6+2.5;
        var position=pivot.subtract(Vec3.directionFromRotation(Mth.clamp(pitch,10,75),viewYaw).scale(distance));
        var target=origin.add(0,definition.height()*.6,0);
        float viewPitch=(float)Math.toDegrees(Math.atan2(position.y-target.y,position.subtract(target).horizontalDistance()));
        return new View(eye,pivot,position,viewYaw,viewPitch,false);
    }
    public static Vec3 clear(Vec3 from,Vec3 to,BiFunction<Vec3,Vec3,Vec3> blockHit) {
        var delta=to.subtract(from);double length=delta.length(),fraction=1;
        if(length<1e-7)return from;
        // Eight corners keep the near plane behind real world surfaces as well as the center ray.
        for(int x=-1;x<=1;x+=2)for(int y=-1;y<=1;y+=2)for(int z=-1;z<=1;z+=2) {
            var start=from.add(x*.12,y*.12,z*.12);var hit=blockHit.apply(start,start.add(delta));
            if(hit!=null)fraction=Math.min(fraction,Math.max(0,(hit.distanceTo(start)-.05)/length));
        }
        return from.add(delta.scale(fraction));
    }
    public static boolean apply(Camera camera,float partial) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||camera.getEntity()!=player||!(player.getVehicle() instanceof MilitaryVehicle vehicle))return false;
        var seat=vehicle.seatOf(player.getUUID());
        if(seat==null)return true; // Wait for synchronized seating instead of letting an action camera claim this frame.
        var type=mc.options.getCameraType();
        var view=view(vehicle.definition(),seat,vehicle.getPosition(partial),Mth.rotLerp(partial,vehicle.yRotO,vehicle.getYRot()),
                vehicle.turretYaw(),vehicle.gunPitch(),player.getViewYRot(partial),player.getViewXRot(partial),type.isFirstPerson(),type.isMirrored());
        BiFunction<Vec3,Vec3,Vec3> hit=(from,to)->{
            var result=mc.level.clip(new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,player));
            return result.getType()==HitResult.Type.MISS?null:result.getLocation();
        };
        var pivot=clear(view.eye(),view.pivot(),hit);
        var position=view.enclosed()||type.isFirstPerson()?pivot:clear(pivot,view.position(),hit);
        place(camera,new View(view.eye(),pivot,position,view.yaw(),view.pitch(),view.enclosed()));
        return true;
    }
    public static void place(Camera camera,View view) {
        var access=(PrototypeCameraAccess)camera;
        access.blacklight$setPosition(view.position());access.blacklight$setRotation(view.yaw(),view.pitch(),0);
    }
}
