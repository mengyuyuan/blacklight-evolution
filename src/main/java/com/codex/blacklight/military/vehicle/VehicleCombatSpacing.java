package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.*;
import java.util.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Close-threat regrouping, using P1's <10 trigger and 15..25 destination band.
 * Minecraft performs bounded collision-safe reversing; this is not the P1 navmesh solver. */
public final class VehicleCombatSpacing {
    private static final class State {final UUID target;long tick=Long.MIN_VALUE;State(UUID target){this.target=target;}}
    private static final Map<MilitaryVehicle,State> STATES=new WeakHashMap<>();
    public static boolean wantsRetreat(double distance,boolean alreadyRetreating){return Double.isFinite(distance)&&distance>=0&&distance<(alreadyRetreating?15:10);}
    /** True holds deployed infantry aboard while the tank is clearing the close threat. */
    public static boolean tick(MilitaryVehicle vehicle,net.minecraft.world.entity.LivingEntity target){
        if(vehicle.level().isClientSide||!vehicle.chassisId().equals("tank")||vehicle.controlLocked()||vehicle.playerOwner()!=null
                ||vehicle.getPassengers().stream().anyMatch(Player.class::isInstance)
                ||!(vehicle.occupant(VehicleDefinition.Job.DRIVER) instanceof MilitarySoldier driver)
                ||!MilitaryVehicle.crewOperational(driver)||!VehicleThreatBridge.legalTarget(vehicle,target)){
            STATES.remove(vehicle);return false;
        }
        var delta=target.position().subtract(vehicle.position());double distance=Math.sqrt(delta.horizontalDistanceSqr());
        var state=STATES.get(vehicle);if(state!=null&&!state.target.equals(target.getUUID()))state=null;
        if(!wantsRetreat(distance,state!=null)){STATES.remove(vehicle);return false;}
        if(state==null){state=new State(target.getUUID());STATES.put(vehicle,state);}
        long now=vehicle.level().getGameTime();if(state.tick==now)return true;state.tick=now;
        float desired=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));float turn=Mth.wrapDegrees(desired-vehicle.getYRot());
        // The native full hull sweep, support checks and loaded-chunk checks remain authoritative.
        vehicle.moveVehicle(Math.abs(turn)>12?0:-Math.min(.10,vehicle.definition().maxSpeed()*.55),Mth.clamp(turn,-2,2));
        return true;
    }
    private VehicleCombatSpacing(){}
}
