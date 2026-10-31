package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.camera.CameraCompatibility;
import com.codex.blacklight.client.camera.HydraCinematicCamera;
import com.codex.blacklight.entity.MilitaryVehicle;
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
                ||player.isSpectator()||player.isPassenger()||!player.getMainHandItem().isEmpty()
                ||mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK
                ||!camera.isInitialized()||camera.getEntity()!=player||!camera.isDetached())return;
        var state=player.getData(ModAttachments.PLAYER_STATE);
        // Do not ask ownsView/ownsInput here: they depend on the very hit result being resolved.
        if(!state.isInfected()||!state.isPrototypeMode()||HydraCinematicCamera.active()
                ||CameraCompatibility.externalOwnsView()
                ||com.codex.blacklight.power.BiomassSystem.active(player))return;

        Vec3 eye=player.getEyePosition(partial),start=camera.getPosition();
        Vec3 direction=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
        double reach=player.entityInteractionRange(),distance=start.distanceTo(eye)+reach;
        if(!Double.isFinite(distance)||distance<=0||distance>64)return;
        Vec3 end=start.add(direction.scale(distance));
        var wall=mc.level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double nearest=wall.getType()==HitResult.Type.MISS?distance:start.distanceTo(wall.getLocation());
        Entity winner=null;Vec3 contact=null;var seen=new HashSet<UUID>();
        for(Entity entity:mc.level.getEntities(player,new AABB(start,end).inflate(1),
                e->!e.isSpectator()&&e.isPickable())) {
            Vec3 point;MilitaryVehicle vehicle=VehicleDamageBridge.vehicle(entity);
            if(vehicle!=null) {
                if(!seen.add(vehicle.getUUID())||vehicle.isRemoved()||vehicle.health()<=0)continue;
                var hit=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch())
                        .raycast(vector(vehicle.position()),vehicle.getYRot(),vector(start),vector(direction),nearest);
                if(hit.isEmpty())continue;
                point=start.add(direction.scale(hit.getAsDouble()));entity=vehicle;
            }else {
                var box=entity.getBoundingBox().inflate(entity.getPickRadius());
                point=box.contains(start)?start:box.clip(start,end).orElse(null);
                if(point==null)continue;
            }
            double candidate=start.distanceTo(point);
            if(candidate<nearest){nearest=candidate;winner=entity;contact=point;}
        }
        if(winner instanceof MilitaryVehicle vehicle&&eye.distanceToSqr(contact)<reach*reach
                &&player.distanceToSqr(vehicle)<=36
                &&mc.level.clip(new ClipContext(eye,contact,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player))
                        .getType()==HitResult.Type.MISS) {
            mc.hitResult=new EntityHitResult(vehicle,contact);mc.crosshairPickEntity=vehicle;
        }else if(mc.hitResult instanceof EntityHitResult old&&VehicleDamageBridge.vehicle(old.getEntity())!=null) {
            // A vehicle behind the player's back must not win over the visible view, a wall or a nearer entity.
            mc.hitResult=BlockHitResult.miss(end,Direction.getNearest(direction.x,direction.y,direction.z),BlockPos.containing(end));
            mc.crosshairPickEntity=null;
        }
    }
    private static VehicleDefinition.Vec3 vector(Vec3 point){return new VehicleDefinition.Vec3(point.x,point.y,point.z);}
}
