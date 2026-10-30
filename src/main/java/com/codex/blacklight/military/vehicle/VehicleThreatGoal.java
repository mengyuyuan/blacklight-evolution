package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Only idle deployed infantry navigation. Actual combat stays in the existing finite rifle goal. */
public final class VehicleThreatGoal extends Goal {
    private final MilitarySoldier soldier;
    private long nextScan;
    public VehicleThreatGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public boolean controls(MilitarySoldier current){return soldier==current;}
    @Override public boolean canUse(){var deployed=VehicleFleetSystem.deployment(soldier);return deployed!=null&&soldier.tacticsAvailable()&&(deployed.returning()||!soldier.validTarget(soldier.getTarget()));}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public void tick(){
        var deployed=VehicleFleetSystem.deployment(soldier);if(deployed==null)return;
        if(deployed.returning()){
            soldier.setTarget(null);var vehicle=deployed.vehicle();
            if(vehicle.getBoundingBox().inflate(.9).contains(soldier.position())&&vehicle.getPassengers().size()<vehicle.definition().seats().size()){
                if(soldier.startRiding(vehicle)){soldier.getNavigation().stop();VehicleFleetSystem.finishDeployment(soldier);}return;
            }
            double yaw=Math.toRadians(vehicle.getYRot()),side=vehicle.definition().width()/2+.7;
            var boarding=vehicle.position().add(Math.cos(yaw)*side,0,-Math.sin(yaw)*side);
            if(VehicleKinematics.loaded(soldier.level(),soldier.getBoundingBox().expandTowards(boarding.subtract(soldier.position())).inflate(.1)))BlackwatchSquadSystem.navigate(soldier,boarding,.9);
            return;
        }
        long now=soldier.level().getGameTime();
        if(now>=nextScan){
            nextScan=now+20;
            var enemy=((ServerLevel)soldier.level()).getEntitiesOfClass(LivingEntity.class,deployed.vehicle().getBoundingBox().inflate(32),target->soldier.validTarget(target)&&BlacklightInfectionSystem.isInfected(target)
                    &&!FirearmsCombat.protectedTarget(soldier,target)&&VehicleFleetSystem.deploymentAllows(soldier,target.position())&&soldier.hasLineOfSight(target)).stream()
                    .min(Comparator.comparingDouble((LivingEntity target)->soldier.distanceToSqr(target)).thenComparing(LivingEntity::getUUID));
            if(enemy.isPresent()){soldier.getNavigation().stop();soldier.setTarget(enemy.get());return;}
        }
        Vec3 objective=deployed.objective();
        if(VehicleFleetSystem.deploymentAllows(soldier,objective)&&soldier.distanceToSqr(objective)>9)BlackwatchSquadSystem.navigate(soldier,objective,.8);
    }
}
