package com.codex.blacklight.military;

import com.codex.blacklight.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Interruptible radio calls and suppression, layered over finite ammunition and safe fire control. */
public final class MilitaryTactics {
    public static final int RADIO_TICKS=77;
    public static void hurt(MilitarySoldier soldier){if(soldier.level().isClientSide)return;var tag=soldier.getPersistentData();tag.putLong("SuppressedUntil",soldier.level().getGameTime()+40);soldier.cancelRadio();}
    public static boolean tick(MilitarySoldier soldier,LivingEntity target){
        long now=soldier.level().getGameTime();var tag=soldier.getPersistentData();
        if(tag.getLong("SuppressedUntil")>now&&soldier.distanceToSqr(target)>16){
            soldier.cancelRadio();BlackwatchSquadSystem.release(soldier);if(!soldier.cover().move(target))BlackwatchSquadSystem.reposition(soldier,target);return true;
        }
        soldier.cover().clear();
        if(soldier.radioStarted()>=0){
            if(!soldier.hasLineOfSight(target)||soldier.distanceToSqr(target)<25){soldier.cancelRadio();tag.putLong("RadioReady",now+120);return false;}
            soldier.getNavigation().stop();BlackwatchSquadSystem.release(soldier);
            if(now-soldier.radioStarted()>=RADIO_TICKS){deploy(soldier);soldier.cancelRadio();tag.putLong("RadioReady",now+1200);}
            return true;
        }
        boolean officer=soldier.commandRole()==MilitaryRole.OFFICER||soldier.commandRole()==MilitaryRole.COMMANDER||soldier.commandRole()==MilitaryRole.TEAM_LEADER;
        if(officer&&tag.getLong("RadioReady")<=now&&tag.getLong("UavLeaseUntil")<=now&&soldier.hasLineOfSight(target)&&soldier.distanceToSqr(target)>64){soldier.beginRadio();BlackwatchSquadSystem.release(soldier);return true;}
        return false;
    }
    private static void deploy(MilitarySoldier soldier){
        if(soldier.faction()==MilitaryFaction.BLACKWATCH&&com.codex.blacklight.military.aircraft.HelicopterSupport.request(soldier))return;
        if(!(soldier.level() instanceof ServerLevel level)||level.getEntitiesOfClass(MilitaryUav.class,soldier.getBoundingBox().inflate(40)).size()>=2)return;
        var uav=com.codex.blacklight.registry.EncounterEntities.UAV.get().create(level);if(uav==null)return;
        Vec3 pos=soldier.position().add(0,3,0);uav.setPos(pos);
        if(!level.hasChunkAt(uav.blockPosition())||!level.noCollision(uav)||!level.getWorldBorder().isWithinBounds(uav.blockPosition()))return;
        uav.assign(soldier);if(level.addFreshEntity(uav))soldier.getPersistentData().putLong("UavLeaseUntil",level.getGameTime()+6000);
        for(var ally:level.getEntitiesOfClass(MilitarySoldier.class,soldier.getBoundingBox().inflate(24),MilitarySoldier::tacticsAvailable))if(ally.getTarget()==null&&ally.validTarget(soldier.getTarget())&&BlackwatchSquadSystem.withinTerritory(ally,soldier.getTarget().position()))ally.setTarget(soldier.getTarget());
    }
    private MilitaryTactics(){}
}
