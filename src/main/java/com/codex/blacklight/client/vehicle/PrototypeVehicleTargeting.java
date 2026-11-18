package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.camera.CameraCompatibility;
import com.codex.blacklight.client.camera.HydraCinematicCamera;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.aircraft.HelicopterSkinContact;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.codex.blacklight.military.vehicle.VehicleHullShape;
import com.codex.blacklight.registry.ModAttachments;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Empty-hand vehicle use follows the displayed free camera, not the prototype's body yaw. */
public final class PrototypeVehicleTargeting {
    private PrototypeVehicleTargeting() {}

    public static void update(float partial) {
        var mc=Minecraft.getInstance();var player=mc.player;var camera=mc.gameRenderer.getMainCamera();
        if(player==null||mc.level==null||mc.getCameraEntity()!=player||!player.isAlive()
                ||player.isSpectator()||player.isPassenger()
                ||!camera.isInitialized()||camera.getEntity()!=player)return;
        var state=player.getData(ModAttachments.PLAYER_STATE);
        // Do not ask ownsView/ownsInput here: they depend on the very hit result being resolved.
        if(HydraCinematicCamera.active()||CameraCompatibility.externalOwnsView()
                ||com.codex.blacklight.power.BiomassSystem.active(player))return;
        boolean freeCamera=mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK&&camera.isDetached()
                &&state.isInfected()&&state.isPrototypeMode();
        boolean groundVehicleUse=freeCamera&&player.getMainHandItem().isEmpty();
        boolean oldAircraft=mc.hitResult instanceof EntityHitResult old && old.getEntity() instanceof BlackwatchHelicopter;


        Vec3 eye=player.getEyePosition(partial),start=freeCamera?camera.getPosition():eye;
        Vec3 direction=freeCamera?Vec3.directionFromRotation(camera.getXRot(),camera.getYRot()):player.getViewVector(partial);
        double reach=player.entityInteractionRange(),distance=start.distanceTo(eye)+reach;
        if(!Double.isFinite(distance)||distance<=0||distance>64)return;
        Vec3 end=start.add(direction.scale(distance));
        var search=new AABB(start,end).inflate(1);
        if(!freeCamera&&!oldAircraft&&mc.level.getEntitiesOfClass(BlackwatchHelicopter.class,search).isEmpty())return;
        var wall=mc.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double nearest=wall.getType()==HitResult.Type.MISS?distance:start.distanceTo(wall.getLocation());
        Entity winner=null;Vec3 contact=null;var seen=new HashSet<UUID>();
        for(Entity entity:mc.level.getEntities(player,search,
                e->!e.isSpectator()&&e.isPickable())) {
            Vec3 point;MilitaryVehicle vehicle=VehicleDamageBridge.vehicle(entity);
            if(vehicle!=null) {
                if(!seen.add(vehicle.getUUID())||vehicle.isRemoved()||vehicle.health()<=0)continue;
                var hit=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch())
                        .raycast(vector(vehicle.position()),vehicle.getYRot(),vector(start),vector(direction),nearest);
                if(hit.isEmpty())continue;
                point=start.add(direction.scale(hit.getAsDouble()));entity=vehicle;
            }else if(entity instanceof BlackwatchHelicopter craft){
                point=HelicopterSkinContact.raycast(craft,start,end).orElse(null);
                if(point==null)continue;
            }else {
                var box=entity.getBoundingBox().inflate(entity.getPickRadius());
                point=box.contains(start)?start:box.clip(start,end).orElse(null);
                if(point==null)continue;
            }
            double candidate=start.distanceTo(point);
            if(candidate<nearest){nearest=candidate;winner=entity;contact=point;}
        }
        boolean groundTarget=groundVehicleUse&&winner instanceof MilitaryVehicle&&player.distanceToSqr(winner)<=36;
        boolean reachable=contact!=null&&eye.distanceToSqr(contact)<=reach*reach
                &&mc.level.clip(new ClipContext(eye,contact,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player))
                    .getType()==HitResult.Type.MISS;
        if(reachable&&(groundTarget||winner instanceof BlackwatchHelicopter||oldAircraft&&winner!=null)){
            mc.hitResult=new EntityHitResult(winner,contact);
            mc.crosshairPickEntity=winner;
        }else if(oldAircraft||groundVehicleUse&&mc.hitResult instanceof EntityHitResult old&&VehicleDamageBridge.vehicle(old.getEntity())!=null){
            mc.hitResult=wall.getType()!=HitResult.Type.MISS&&eye.distanceToSqr(wall.getLocation())<=reach*reach
                    ?wall:BlockHitResult.miss(end,Direction.getNearest(direction.x,direction.y,direction.z),BlockPos.containing(end));
            mc.crosshairPickEntity=null;
        }
    }
    private static VehicleDefinition.Vec3 vector(Vec3 point){return new VehicleDefinition.Vec3(point.x,point.y,point.z);}
}
