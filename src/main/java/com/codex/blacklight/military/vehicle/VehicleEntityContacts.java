package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 1.20.1 native entity-collision query with mechanical narrow contacts. */
public final class VehicleEntityContacts {
    public static List<VoxelShape> collect(EntityGetter level,Entity mover,AABB query){
        if(query.getSize()<1.0E-7)return List.of();
        Predicate<Entity> predicate=mover==null?EntitySelector.CAN_BE_COLLIDED_WITH:
                EntitySelector.NO_SPECTATORS.and(mover::canCollideWith);
        var entities=level.getEntities(mover,query.inflate(1.0E-7),predicate);
        if(entities.isEmpty())return List.of();
        var shapes=new ArrayList<VoxelShape>(entities.size());
        for(var other:entities){
            MilitaryVehicle vehicle=other instanceof MilitaryVehicle parent?parent:
                    other instanceof VehicleHullPart part?part.getParent():null;
            VoxelShape shape;
            if(vehicle==null)shape=Shapes.create(other.getBoundingBox());
            else{
                if(mover!=null&&(mover.is(vehicle)||mover.isPassengerOfSameVehicle(vehicle)
                        ||mover instanceof VehicleHullPart part&&part.getParent()==vehicle))continue;
                shape=vehicle.contactCollisionShape(query);
            }
            if(!shape.isEmpty())shapes.add(shape);
        }
        return List.copyOf(shapes);
    }
    private VehicleEntityContacts(){}
}
