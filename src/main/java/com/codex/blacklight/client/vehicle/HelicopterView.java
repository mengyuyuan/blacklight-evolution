package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import net.minecraft.client.*;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class HelicopterView {
    public static boolean apply(Camera camera,float partial){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||camera.getEntity()!=mc.player||!(mc.player.getVehicle() instanceof BlackwatchHelicopter craft))return false;
        var type=mc.options.getCameraType();var origin=craft.getPosition(partial);float body=Mth.rotLerp(partial,craft.yRotO,craft.getYRot());
        float yaw=mc.player.getViewYRot(partial)+(type.isMirrored()?180:0),pitch=Mth.clamp(mc.player.getViewXRot(partial),10,75);
        Vec3 pivot=origin.add(0,2.5,0),position;
        if(craft.pilot().hijack().actor(mc.player.getUUID())){
            position=origin.add(new Vec3(craft.pilot().hijack().side()*6,3.3,5).yRot((float)Math.toRadians(body)));var look=pivot.subtract(position);
            yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));
        }else if(type.isFirstPerson()){
            int slot=craft.pilot().slot(mc.player.getUUID());position=origin.add(new Vec3(slot%2==0?-.65:.65,2.3,3.2).yRot((float)Math.toRadians(body)));
            yaw=-body+Mth.clamp(Mth.wrapDegrees(mc.player.getViewYRot(partial)+body),-70,70);pitch=Mth.clamp(mc.player.getViewXRot(partial),-35,50);pivot=position;
        }else{
            position=pivot.subtract(Vec3.directionFromRotation(pitch,yaw).scale(12));
        }
        java.util.function.BiFunction<Vec3,Vec3,Vec3> hit=(from,to)->{var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));return wall.getType()==HitResult.Type.MISS?null:wall.getLocation();};
        position=VehicleCamera.clear(pivot,position,hit);
        VehicleCamera.place(camera,new VehicleCamera.View(pivot,pivot,position,yaw,pitch,false));return true;
    }
    private HelicopterView(){}
}
