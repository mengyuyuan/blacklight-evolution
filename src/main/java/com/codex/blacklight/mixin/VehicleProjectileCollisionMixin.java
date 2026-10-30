package com.codex.blacklight.mixin;

import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changes only new vehicle ordnance collision results; native events and the complete tick remain native. */
@Mixin(ThrowableProjectile.class)
public abstract class VehicleProjectileCollisionMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult blacklight$actualVehicleHull(Entity projectile,Predicate<Entity> filter,Operation<HitResult> original){
        HitResult ordinary=original.call(projectile,filter);
        return projectile instanceof VehicleOrdnanceProjectile vehicle?vehicle.resolveActualHullImpact(ordinary):ordinary;
    }
}
