package com.codex.blacklight.integration.lithium;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Optional collision adapters share the same native vehicle contact policy. */
public final class LithiumVehicleCollision {
    /** Captured by Lithium's iterable and consumed by its lazy collision iterator. */
    public interface Query {
        Entity blacklight$collisionMover();
        AABB blacklight$collisionQuery();
    }
    public static VoxelShape narrow(VoxelShape original,Entity other,Entity mover,AABB query){
        MilitaryVehicle vehicle=other instanceof MilitaryVehicle parent?parent:other instanceof VehicleHullPart part?part.getParent():null;
        if(vehicle==null)return original;
        if(mover!=null&&(mover.is(vehicle)||mover.isPassengerOfSameVehicle(vehicle)
                ||mover instanceof VehicleHullPart part&&part.getParent()==vehicle))return Shapes.empty();
        return vehicle.contactCollisionShape(query);
    }
    private LithiumVehicleCollision(){}
}
