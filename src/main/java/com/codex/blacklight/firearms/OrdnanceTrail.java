package com.codex.blacklight.firearms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;

/** Client-only particles through Level's sided API; never emits per-tick network traffic. */
public final class OrdnanceTrail {
    public static void tick(Entity projectile,WeaponDefinition.Kind kind){
        if(!projectile.level().isClientSide||projectile.isRemoved()||projectile.tickCount%2!=0)return;
        boolean powered=kind==WeaponDefinition.Kind.ROCKET||kind==WeaponDefinition.Kind.MISSILE;
        var v=projectile.getDeltaMovement();if(!Double.isFinite(v.lengthSqr())||v.lengthSqr()<.001)return;
        var tail=projectile.position().subtract(v.normalize().scale(.35));
        projectile.level().addParticle(powered?ParticleTypes.SMOKE:ParticleTypes.ASH,tail.x,tail.y,tail.z,-v.x*.025,.005,-v.z*.025);
        if(powered)projectile.level().addParticle(ParticleTypes.SMALL_FLAME,tail.x,tail.y,tail.z,-v.x*.05,-v.y*.05,-v.z*.05);
    }
    private OrdnanceTrail(){}
}
