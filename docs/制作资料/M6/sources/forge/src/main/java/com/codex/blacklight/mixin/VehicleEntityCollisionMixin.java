package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Forge 0.8.5's AP supports a mapped default-method overwrite, but rejects interface injectors. */
@Mixin(EntityGetter.class)
public interface VehicleEntityCollisionMixin {
    /**
     * @author Blacklight contributors
     * @reason Preserve vanilla querying and only substitute mechanical contact shapes on Forge's older Mixin toolchain.
     */
    @Overwrite
    default java.util.List<VoxelShape> getEntityCollisions(Entity mover,AABB query){
        if(query.getSize()<1e-7)return java.util.List.of();
        var predicate=mover==null?EntitySelector.CAN_BE_COLLIDED_WITH:EntitySelector.NO_SPECTATORS.and(mover::canCollideWith);
        var entities=((EntityGetter)(Object)this).getEntities(mover,query.inflate(1e-7),predicate);
        if(entities.isEmpty())return java.util.List.of();
        var result=ImmutableList.<VoxelShape>builderWithExpectedSize(entities.size());
        for(Entity other:entities){
            MilitaryVehicle vehicle=other instanceof MilitaryVehicle parent?parent:other instanceof VehicleHullPart part?part.getParent():null;
            if(vehicle==null)result.add(Shapes.create(other.getBoundingBox()));
            else if(mover!=null&&(mover.is(vehicle)||mover.isPassengerOfSameVehicle(vehicle)
                    ||mover instanceof VehicleHullPart part&&part.getParent()==vehicle))result.add(Shapes.empty());
            else result.add(vehicle.contactCollisionShape(query));
        }
        return result.build();
    }
}
