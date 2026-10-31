package com.codex.blacklight.mixin;

import com.codex.blacklight.military.vehicle.VehicleEntityContacts;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/** Forge's Mixin cannot inject into an interface default method. Level owns the
 * override of that inherited method, using the same native query and predicates. */
@Mixin(Level.class)
public abstract class VehicleEntityCollisionMixin implements EntityGetter {
    @Override public List<VoxelShape> getEntityCollisions(Entity mover,AABB query){
        return VehicleEntityContacts.collect(this,mover,query);
    }
}
