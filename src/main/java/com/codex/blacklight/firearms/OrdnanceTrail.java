package com.codex.blacklight.firearms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only particles through Level's sided API; never emits per-tick network traffic. */
public final class OrdnanceTrail {
    private static final Map<Entity,Vec3> HELLFIRE_TAILS=new WeakHashMap<>();
    public static void tick(Entity projectile,WeaponDefinition.Kind kind){
        tick(projectile,kind,true);
    }
    public static void tick(Entity projectile,WeaponDefinition.Kind kind,boolean flame){
        if(projectile instanceof VehicleOrdnanceProjectile missile&&(missile.hellfire()||missile.rapidRocket())){
            hellfire(missile,flame);return;
        }
        if(!projectile.level().isClientSide||projectile.isRemoved()||projectile.tickCount%2!=0)return;
        boolean powered=kind==WeaponDefinition.Kind.ROCKET||kind==WeaponDefinition.Kind.MISSILE;
        var v=projectile.getDeltaMovement();if(!Double.isFinite(v.lengthSqr())||v.lengthSqr()<.001)return;
        var tail=projectile.position().subtract(v.normalize().scale(.35));
        projectile.level().addParticle(powered?ParticleTypes.SMOKE:ParticleTypes.ASH,tail.x,tail.y,tail.z,-v.x*.025,.005,-v.z*.025);
        if(powered&&flame)projectile.level().addParticle(ParticleTypes.SMALL_FLAME,tail.x,tail.y,tail.z,-v.x*.05,-v.y*.05,-v.z*.05);
    }
    private static void hellfire(VehicleOrdnanceProjectile missile,boolean flame){
        if(!missile.level().isClientSide||missile.isRemoved())return;
        Vec3 heading=missile.flightHeading(),velocity=missile.getDeltaMovement();
        if(!Double.isFinite(heading.lengthSqr())||!Double.isFinite(velocity.lengthSqr())||velocity.lengthSqr()<.001)return;
        // A native projectile has already advanced to the end of its tick here,
        // while the renderer is still interpolating from xOld. Emitting at the
        // advanced position can put exhaust in front of the visible nose.
        double length=missile.rapidRocket()?com.codex.blacklight.military.aircraft.HelicopterRapidFlight.LENGTH:com.codex.blacklight.military.aircraft.HelicopterHellfireRack.MISSILE_LENGTH;
        Vec3 tail=new Vec3(missile.xOld,missile.yOld,missile.zOld).subtract(heading.scale(length*.5));
        Vec3 previous=HELLFIRE_TAILS.put(missile,tail);
        if(previous==null||previous.distanceToSqr(tail)>16*16)previous=tail;
        int count=Math.min(8,Math.max(1,(int)Math.ceil(previous.distanceTo(tail)/.4)));
        for(int i=1;i<=count;i++){
            Vec3 at=previous.lerp(tail,(double)i/count);
            missile.level().addParticle(ParticleTypes.SMOKE,at.x,at.y,at.z,-heading.x*.02,.005-heading.y*.02,-heading.z*.02);
        }
        if(flame)missile.level().addParticle(ParticleTypes.SMALL_FLAME,tail.x,tail.y,tail.z,-heading.x*.04,-heading.y*.04,-heading.z*.04);
    }
    private OrdnanceTrail(){}
}
