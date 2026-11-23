package com.codex.blacklight.client.camera;

import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.client.renderer.GoddessExecutionTracks;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import net.minecraft.client.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ViewportEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GoddessExecutionCamera {
    private static EncounterMob actor;
    private static float yaw,pitch;
    private static double fov;
    public static boolean active(){
        var mc=Minecraft.getInstance();
        return actor!=null && mc.level==actor.level() && mc.player!=null && mc.getCameraEntity()==mc.player
                && mc.player.isAlive() && !actor.isRemoved() && actor.goddess.action()==GoddessEncounter.Action.EXTRACTING;
    }
    public static boolean update(net.minecraft.world.entity.Entity view){
        var mc=Minecraft.getInstance();actor=view==mc.player && mc.player!=null && mc.player.isAlive()?GoddessEncounter.paired(mc.player):null;
        return active();
    }
    public static void apply(Camera camera,float partial){
        var mc=Minecraft.getInstance();
        var shot=GoddessExecutionTracks.shot(actor,partial);
        Vec3 focus=shot.focus(),eye=shot.eye();
        var hit=mc.level.clip(new ClipContext(focus,eye,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.MISS)eye=hit.getLocation().lerp(focus,.08);
        if(eye.distanceToSqr(focus)<.3 || !mc.level.noCollision(mc.player,new AABB(eye,eye).inflate(.17))){
            eye=mc.player.getEyePosition(partial);
        }
        var dir=focus.subtract(eye);
        yaw=(float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
        pitch=(float)-Math.toDegrees(Math.atan2(dir.y,dir.horizontalDistance()));
        fov=Math.toDegrees(2*Math.atan(20.25/(2*shot.lens())));
        var access=(PrototypeCameraAccess)camera;
        access.blacklight$setPosition(eye);access.blacklight$setRotation(yaw,pitch);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(active()){event.setYaw(yaw);event.setPitch(pitch);event.setRoll(0);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){if(active() && fov>0 && event.usedConfiguredFov())event.setFOV(fov);}
    private GoddessExecutionCamera(){}
}
