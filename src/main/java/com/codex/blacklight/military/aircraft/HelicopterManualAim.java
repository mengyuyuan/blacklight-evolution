package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.combat.ExplicitTarget;
import com.codex.blacklight.combat.TargetSelection;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.vehicle.VehicleAim;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client packet aim, shared with real seat/input regressions. Locks converge
 * from the chin gun; the server still applies mount limits and physical hits. */
public final class HelicopterManualAim {
    public record Solution(Vec3 point,float yaw,float pitch,boolean locked){}
    public static Solution resolve(Player player,BlackwatchHelicopter craft,TargetSelection selection,Vec3 crosshair,double range){
        boolean locked=player!=null&&player.getVehicle()==craft&&ExplicitTarget.enabled(player)
                &&selection!=null&&selection.visibleWithinRange(player,range);
        Vec3 point=locked?selection.point(player):crosshair;
        Vec3 gun=craft.weaponPoint(HelicopterArmament.GUN);
        return new Solution(point,VehicleAim.yaw(gun,point),VehicleAim.pitch(gun,point),locked);
    }
    private HelicopterManualAim(){}
}
