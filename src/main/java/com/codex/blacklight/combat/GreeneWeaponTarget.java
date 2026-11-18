package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** MC acquisition/crew protection adapter; not the original emotional-state AI. */
public final class GreeneWeaponTarget {
    public static GreeneWeaponProgram mechanicalProgram(Entity target){
        if(target instanceof MilitaryVehicle v&&v.definition().id().equals("tank"))return GreeneWeaponProgram.ANTI_TANK;
        return null;
    }
    public static Entity body(Entity target){
        if(target==null)return null;var root=target.getRootVehicle();return mechanicalProgram(root)!=null?root:target;
    }
    public static boolean valid(EncounterMob owner,Entity target){
        if(target==null||target==owner||target.isRemoved()||!target.isAlive()||target.level()!=owner.level()||!owner.level().hasChunkAt(target.blockPosition()))return false;
        if(target instanceof LivingEntity living){
            var root=living.getRootVehicle();
            return !(root instanceof MilitaryVehicle)&&owner.enemy(living);
        }
        if(target instanceof MilitaryVehicle v){if(v.health()<=0||VehicleDamageBridge.protectedHull(owner,v))return false;}
        else return false;
        // Saved ownership alone is not a current hostile crew. Empty vehicles do
        // not lure a boss away from the player or enable griefing parked hulls.
        for(var passenger:target.getIndirectPassengers())if(passenger instanceof LivingEntity living&&owner.enemy(living))return true;
        return false;
    }
    public static boolean accepts(EncounterMob owner,GreeneWeaponProgram program,Entity target){
        return valid(owner,target)&&(program.antiVehicle()?mechanicalProgram(target)==program:target instanceof LivingEntity&&body(target)==target);
    }
    public static Vec3 point(Entity target){return target.getBoundingBox().getCenter();}
    public static boolean visible(EncounterMob owner,Entity target){
        var from=owner.getEyePosition();var to=point(target);
        return owner.level() instanceof net.minecraft.server.level.ServerLevel server&&VehicleDamageBridge.loaded(server,from,to)
            &&server.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS;
    }
    private GreeneWeaponTarget(){}
}
