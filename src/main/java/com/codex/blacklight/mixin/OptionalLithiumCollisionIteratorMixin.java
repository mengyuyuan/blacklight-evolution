package com.codex.blacklight.mixin;

import com.codex.blacklight.integration.lithium.LithiumVehicleCollision;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep noCollision and hard-entity queries consistent with optimized movement. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions$1$1",remap=false)
public abstract class OptionalLithiumCollisionIteratorMixin {
    @Unique private Entity blacklight$collisionMover;
    @Unique private AABB blacklight$collisionQuery;
    @Unique private boolean blacklight$emptyVehicleContact;
    @Inject(method="<init>",at=@At("RETURN"),require=1,remap=false)
    private void blacklight$captureCollisionContext(@Coerce Object iterable,CallbackInfo ci){
        var query=(LithiumVehicleCollision.Query)iterable;
        blacklight$collisionMover=query.blacklight$collisionMover();
        blacklight$collisionQuery=query.blacklight$collisionQuery();
    }
    // Lithium's noCollision path asks only hasNext(), so an empty contact must be
    // skipped rather than returned as one collision. The original iterator still
    // owns every index, entity filter, world-border result and endOfData transition.
    @WrapMethod(method="computeNext()Lnet/minecraft/world/phys/shapes/VoxelShape;",require=1)
    private VoxelShape blacklight$skipEmptyVehicleContact(Operation<VoxelShape> original){
        while(true){
            blacklight$emptyVehicleContact=false;
            VoxelShape result=original.call();
            if(!blacklight$emptyVehicleContact)return result;
        }
    }
    @ModifyExpressionValue(method="computeNext()Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;",remap=false),require=1)
    private VoxelShape blacklight$nativeVehicleContact(VoxelShape original,@Local(ordinal=0) Entity other){
        VoxelShape result=LithiumVehicleCollision.narrow(original,other,blacklight$collisionMover,blacklight$collisionQuery);
        blacklight$emptyVehicleContact=(other instanceof MilitaryVehicle||other instanceof VehicleHullPart)&&result.isEmpty();
        return result;
    }
}
