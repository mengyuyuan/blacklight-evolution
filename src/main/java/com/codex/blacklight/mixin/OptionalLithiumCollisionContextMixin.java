package com.codex.blacklight.mixin;

import com.codex.blacklight.integration.lithium.LithiumVehicleCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Read the actual context captured by Lithium's lazy collision iterable. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions$1",remap=false)
public abstract class OptionalLithiumCollisionContextMixin implements LithiumVehicleCollision.Query {
    @Shadow(remap=false) @Final private Entity val$entity;
    @Shadow(remap=false) @Final private AABB val$box;
    @Override public Entity blacklight$collisionMover(){return val$entity;}
    @Override public AABB blacklight$collisionQuery(){return val$box;}
}
