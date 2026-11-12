package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleFeedbackMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Cosmetic spring response shared by first/third person; inputs and server aim are untouched. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleCombatVisuals {
    /** Apply after VehicleCamera places the optic; Camera.setup's earlier event is overwritten there. */
    public static void camera(net.minecraft.client.Camera camera,MilitaryVehicle vehicle,float partial){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.isPaused()||camera.getEntity()!=mc.player
                ||!com.codex.blacklight.config.GunplayClientConfig.CAMERA_KICK.get()
                ||com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView())return;
        var pose=vehicle.feedback(partial);
        double yaw=Math.toRadians(camera.getYRot()+vehicle.getYRot());
        double pitch=pose.pitch()*Math.cos(yaw)-pose.roll()*Math.sin(yaw),roll=pose.roll()*Math.cos(yaw)+pose.pitch()*Math.sin(yaw);
        double shotAge=vehicle.primaryShot()<0?-1:mc.level.getGameTime()+partial-vehicle.primaryShot();
        double kick=VehicleFeedbackMotion.recoil(shotAge)*(vehicle.lastPrimaryWeapon().startsWith("tank_")?1.3:.35);
        double scale=mc.options.getCameraType().isFirstPerson()?1:.65;
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)camera).blacklight$setRotation(camera.getYRot(),
                camera.getXRot()+(float)((pitch-kick)*scale),camera.getRoll()+(float)(roll*scale));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        int budget=8;
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof MilitaryVehicle v)||v.isRemoved()||v.health()<=0||v.health()>v.maxHealth()*.35||v.distanceToSqr(mc.player)>48*48||--budget<0)continue;
            if((v.tickCount+v.getId())%5!=0)continue;
            Vec3 engine=v.position().add(new Vec3(0,v.definition().height()*.65,-v.definition().length()*.28).yRot((float)Math.toRadians(v.getYRot())));
            mc.level.addParticle(ParticleTypes.SMOKE,engine.x,engine.y,engine.z,0,.035,0);
            if(v.health()<v.maxHealth()*.15&&(v.tickCount+v.getId())%10==0)mc.level.addParticle(ParticleTypes.FLAME,engine.x,engine.y,engine.z,0,.02,0);
        }
    }
    private VehicleCombatVisuals(){}
}
