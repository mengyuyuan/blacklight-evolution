package com.codex.blacklight.mixin;

import com.codex.blacklight.entity.HydraPart;
import com.codex.blacklight.entity.FlyerPart;
import com.codex.blacklight.entity.GreenePart;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native picking/projectile ordering stays intact; only animated receiving broad boxes are narrowed. */
@Mixin(ProjectileUtil.class)
public abstract class HydraProjectileContactMixin {
    @WrapOperation(method={"getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"},at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> blacklight$hydraSurface(AABB box,Vec3 from,Vec3 to,Operation<Optional<Vec3>> original,@Local(ordinal=2) Entity candidate){
        return candidate instanceof HydraPart part?part.clip(from,to):candidate instanceof FlyerPart flyer?flyer.clip(from,to):candidate instanceof GreenePart greene?greene.clip(from,to):original.call(box,from,to);
    }
    @WrapOperation(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean blacklight$hydraInside(AABB box,Vec3 from,Operation<Boolean> original,@Local(ordinal=2) Entity candidate){
        return candidate instanceof HydraPart part?part.contains(from):candidate instanceof FlyerPart flyer?flyer.contains(from):candidate instanceof GreenePart greene?greene.contains(from):original.call(box,from);
    }
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void blacklight$hydraImpactPoint(Level level,Entity projectile,Vec3 from,Vec3 to,AABB bounds,Predicate<Entity> filter,float margin,CallbackInfoReturnable<EntityHitResult> result){
        if(result.getReturnValue()!=null&&result.getReturnValue().getEntity() instanceof HydraPart part)
            part.clip(from,to).ifPresent(at->result.setReturnValue(new EntityHitResult(part,at)));
        else if(result.getReturnValue()!=null&&result.getReturnValue().getEntity() instanceof FlyerPart flyer)
            flyer.clip(from,to).ifPresent(at->result.setReturnValue(new EntityHitResult(flyer,at)));
        else if(result.getReturnValue()!=null&&result.getReturnValue().getEntity() instanceof GreenePart greene)
            greene.clip(from,to).ifPresent(at->result.setReturnValue(new EntityHitResult(greene,at)));
    }
}
