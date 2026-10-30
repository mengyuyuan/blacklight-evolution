package com.codex.blacklight.mixin;

import com.codex.blacklight.integration.lithium.LithiumVehicleCollision;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lithium's optimized movement path bypasses EntityGetter.getEntityCollisions. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions",remap=false)
public abstract class OptionalLithiumVehicleCollisionMixin {
    @WrapOperation(method="appendEntityCollisions",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z",remap=false),require=1)
    private static boolean blacklight$skipEmptyVehicleContact(List<VoxelShape> collisions,Object value,Operation<Boolean> original,
            @Local(ordinal=1) Entity other){
        if((other instanceof MilitaryVehicle||other instanceof VehicleHullPart)&&value instanceof VoxelShape shape&&shape.isEmpty())return false;
        return original.call(collisions,value);
    }

    @ModifyExpressionValue(method="appendEntityCollisions",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;",remap=false),require=1)
    private static VoxelShape blacklight$nativeVehicleContact(VoxelShape original,
            @Local(ordinal=1) Entity other,@Local(argsOnly=true) Entity mover,@Local(argsOnly=true) AABB query){
        return LithiumVehicleCollision.narrow(original,other,mover,query);
    }
}
