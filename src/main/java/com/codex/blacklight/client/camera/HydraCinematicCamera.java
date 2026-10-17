package com.codex.blacklight.client.camera;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import net.minecraft.client.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** One world-space shot per endpoint. Neither the grip nor the player drives the camera. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraCinematicCamera {
    private static BlacklightHydra scene;
    private static Object world;
    private static CameraType previous;
    private static Vec3 position;
    private static float yaw,pitch;
    public static boolean active(){return scene!=null;}
    public static int sceneId(){return scene==null?-1:scene.getId();}
    public static Vec3 anchor(){return position;}
    private HydraCinematicCamera(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){update();}
    public static boolean update(){
        var mc=Minecraft.getInstance();var player=mc.player;
        BlacklightHydra next=null;
        if(player!=null&&player.isAlive()&&mc.level!=null&&mc.getCameraEntity()==player){
            for(var entity:mc.level.entitiesForRendering())
                if(entity instanceof BlacklightHydra h&&h.isAlive()&&h.scenePlayerId()==player.getId()){
                    next=h;break;
                }
        }
        if(next==null){release();return false;}
        if(previous==null)previous=mc.options.getCameraType();
        if(scene!=next||world!=mc.level){
            scene=next;world=mc.level;frame(next);
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        return true;
    }
    private static void release(){
        var mc=Minecraft.getInstance();
        if(previous!=null)mc.options.setCameraType(previous);
        previous=null;scene=null;world=null;position=null;
    }
    private static void frame(BlacklightHydra h){
        var mc=Minecraft.getInstance();double scale=h.visualScale();
        Vec3 focus=h.position().add(0,4.5*scale,0);
        // Select a clear establishing shot once, then keep it still for the entire scene.
        double best=-1;Vec3 selected=null;
        for(int i=0;i<16;i++){
            double angle=Math.toRadians(h.getYRot()+45+i*22.5);
            Vec3 wanted=focus.add(Math.cos(angle)*13*scale,3*scale,Math.sin(angle)*13*scale);
            var hit=mc.level.clip(new ClipContext(focus,wanted,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));
            Vec3 candidate=hit.getType()==HitResult.Type.MISS?wanted:hit.getLocation().add(focus.subtract(wanted).normalize().scale(.6));
            var box=new AABB(candidate.x-.3,candidate.y-.3,candidate.z-.3,candidate.x+.3,candidate.y+.3,candidate.z+.3);
            if(!mc.level.noCollision(mc.player,box))continue;
            double distance=candidate.distanceToSqr(focus);
            if(distance>best){best=distance;selected=candidate;}
            if(hit.getType()==HitResult.Type.MISS)break;
        }
        // A cramped room may not fit the preferred wide shot. Use a collision-free
        // starting eye position rather than pushing a camera through the wall.
        position=selected==null?mc.player.getEyePosition():selected;
        Vec3 direction=focus.subtract(position);
        yaw=(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90);
        pitch=(float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance()));
    }
    public static void apply(Camera camera){
        if(!active())return;
        var access=(PrototypeCameraAccess)camera;
        access.blacklight$setPosition(position);access.blacklight$setRotation(yaw,pitch,0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(!active())return;
        event.setYaw(yaw);event.setPitch(pitch);event.setRoll(0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){if(active()&&event.usedConfiguredFov())event.setFOV(65);}
}
