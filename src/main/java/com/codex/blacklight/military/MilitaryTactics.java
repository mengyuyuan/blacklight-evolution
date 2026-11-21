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
        boolean blackwatch=soldier.faction()==MilitaryFaction.BLACKWATCH;
        boolean airReady=blackwatch && tag.getLong("AirSupportReady")<=now;
        if(tag.getLong("RadioReady")>now || (!airReady && tag.getLong("UavLeaseUntil")>now)
                || soldier.distanceToSqr(target)<=64 || !soldier.hasLineOfSight(target))return false;
        boolean leader=soldier.commandRole()!=MilitaryRole.TROOPER;
        // An isolated patrol can report a sighting even when its officer is absent.
        if(!leader && (!blackwatch || soldier.tickCount%20!=0 || !patrolCaller(soldier,target)))return false;
        soldier.beginRadio();BlackwatchSquadSystem.release(soldier);return true;
    }
    private static boolean patrolCaller(MilitarySoldier soldier,LivingEntity target){
        return soldier.level().getEntitiesOfClass(MilitarySoldier.class,soldier.getBoundingBox().inflate(28),
                ally->ally!=soldier && ally.faction()==MilitaryFaction.BLACKWATCH && ally.tacticsAvailable()
                        && ally.getTarget()==target && ally.hasLineOfSight(target) && ally.distanceToSqr(target)>64
                        && (ally.radioStarted()>=0 || ally.getPersistentData().getLong("RadioReady")<=ally.level().getGameTime()
                            && (ally.commandRole()!=MilitaryRole.TROOPER || ally.getId()<soldier.getId()))).isEmpty();
    }
    private static void deploy(MilitarySoldier soldier){
        if(soldier.faction()==MilitaryFaction.BLACKWATCH&&com.codex.blacklight.military.aircraft.HelicopterSupport.request(soldier))return;
        if(!(soldier.level() instanceof ServerLevel level)||soldier.getPersistentData().getLong("UavLeaseUntil")>level.getGameTime()
                ||level.getEntitiesOfClass(MilitaryUav.class,soldier.getBoundingBox().inflate(40)).size()>=2)return;
        var uav=com.codex.blacklight.registry.EncounterEntities.UAV.get().create(level);if(uav==null)return;
        Vec3 pos=soldier.position().add(0,3,0);uav.setPos(pos);
        if(!level.hasChunkAt(uav.blockPosition())||!level.noCollision(uav)||!level.getWorldBorder().isWithinBounds(uav.blockPosition()))return;
        uav.assign(soldier);if(level.addFreshEntity(uav))soldier.getPersistentData().putLong("UavLeaseUntil",level.getGameTime()+6000);
        for(var ally:level.getEntitiesOfClass(MilitarySoldier.class,soldier.getBoundingBox().inflate(24),MilitarySoldier::tacticsAvailable))if(ally.getTarget()==null&&ally.validTarget(soldier.getTarget())&&BlackwatchSquadSystem.withinTerritory(ally,soldier.getTarget().position()))ally.setTarget(soldier.getTarget());
    }
    private MilitaryTactics(){}
}
