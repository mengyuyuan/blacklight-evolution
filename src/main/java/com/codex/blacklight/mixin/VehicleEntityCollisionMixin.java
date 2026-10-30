package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep vanilla selection and other entities; only mechanical contact uses a narrow shape. */
@Mixin(EntityGetter.class)
public interface VehicleEntityCollisionMixin {
    // Native packet validation treats every returned entry as a prospective obstacle.
    // Empty geometry must therefore be absent, not present as an empty VoxelShape.
    @ModifyReturnValue(method="getEntityCollisions",at=@At("RETURN"))
    default List<VoxelShape> blacklight$onlyActualContacts(List<VoxelShape> original){
        if(original.stream().noneMatch(VoxelShape::isEmpty))return original;
        return original.stream().filter(shape->!shape.isEmpty()).toList();
    }

    @WrapOperation(method="getEntityCollisions",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    default VoxelShape blacklight$vehicleContact(AABB box,Operation<VoxelShape> original,
            @Local(ordinal=1) Entity other,@Local(argsOnly=true) Entity mover,@Local(argsOnly=true) AABB query){
        MilitaryVehicle vehicle=other instanceof MilitaryVehicle parent?parent:other instanceof VehicleHullPart part?part.getParent():null;
        if(vehicle==null)return original.call(box);
        if(mover!=null&&(mover.is(vehicle)||mover.isPassengerOfSameVehicle(vehicle)
                ||mover instanceof VehicleHullPart part&&part.getParent()==vehicle))return Shapes.empty();
        return vehicle.contactCollisionShape(query);
    }
}
