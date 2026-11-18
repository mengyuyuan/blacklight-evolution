package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One transport per local call; six-person source waves use loaded-space and local population budgets. */
public final class HelicopterSupport {
    public static boolean request(MilitarySoldier caller){
        if(!(caller.level() instanceof ServerLevel level)||caller.faction()!=MilitaryFaction.BLACKWATCH||!caller.tacticsAvailable())return false;
        return request(caller,caller.position(),null);
    }
    public static boolean request(MilitaryUav scanner,net.minecraft.world.entity.LivingEntity target){
        if(scanner==null||!scanner.confirmedForSupport(target))return false;
        return request(scanner,target.position(),target);
    }
    private static boolean request(net.minecraft.world.entity.Entity caller,Vec3 reference,net.minecraft.world.entity.LivingEntity observed){
        if(!(caller.level() instanceof ServerLevel level))return false;
        long now=level.getGameTime();var tag=caller.getPersistentData();
        if(tag.getLong("AirSupportReady")>now)return false;
        tag.putLong("AirSupportReady",now+200);tag.putString("AirSupportLastResult","local_budget");
        if(!level.getEntitiesOfClass(BlackwatchHelicopter.class,caller.getBoundingBox().inflate(96)).isEmpty()
                ||level.getEntitiesOfClass(MilitarySoldier.class,caller.getBoundingBox().inflate(64)).size()>16)return false;
        var helicopter=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);if(helicopter==null)return false;
        Vec3 landing=helicopter.deployment().findSite(reference);
        if(landing==null){tag.putString("AirSupportLastResult","no_supported_rappel_site");return false;}
        Vec3 origin=null;
        for(int distance:new int[]{40,32,24}){
            for(int direction=0;direction<8;direction++){
                double angle=direction*Math.PI/4;
                Vec3 candidate=landing.add(Math.cos(angle)*distance,20,Math.sin(angle)*distance);
                if(level.isPositionEntityTicking(BlockPos.containing(candidate))&&helicopter.clearAt(candidate,0)){origin=candidate;break;}
            }
            if(origin!=null)break;
        }
        if(origin==null){tag.putString("AirSupportLastResult","no_loaded_ingress");return false;}
        helicopter.setPos(origin);if(!helicopter.deployRappellingTo(landing)||!level.addFreshEntity(helicopter))return false;
        var created=new ArrayList<MilitarySoldier>();
        for(int slot=0;slot<2;slot++){
            var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);
            if(soldier==null){rollback(helicopter,created);return false;}
            soldier.setPos(origin);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
            soldier.initializeNativeLoadout("air-support:"+helicopter.getUUID()+":"+slot);
            soldier.getPersistentData().putBoolean("HelicopterTroop",false);
            // Native passengers receive their own bounded inventory and cannot act while riding.
            if(!level.addFreshEntity(soldier)){soldier.discard();rollback(helicopter,created);return false;}
            created.add(soldier);if(!soldier.startRiding(helicopter,true)){rollback(helicopter,created);return false;}
        }
        if(caller instanceof MilitaryUav scanner){
            if(!helicopter.combat().supportScan(scanner,observed)){rollback(helicopter,created);return false;}
        }else helicopter.combat().supportCall((MilitarySoldier)caller);
        tag.putUUID("AirSupportCraft",helicopter.getUUID());tag.putLong("AirSupportReady",now+3600);tag.putString("AirSupportLastResult","dispatched");return true;
    }
    private static void rollback(BlackwatchHelicopter craft,List<MilitarySoldier> crew){crew.forEach(MilitarySoldier::discard);craft.discard();}
    private static int MthFloor(double number){return net.minecraft.util.Mth.floor(number);}
    private HelicopterSupport(){}
}
