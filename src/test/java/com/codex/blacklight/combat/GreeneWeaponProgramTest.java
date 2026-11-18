package com.codex.blacklight.combat;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneWeaponProgramTest {
    private static List<Integer> slots(GreeneWeaponProgram p,int age){return p.shots().stream().filter(s->s.active(age)).map(GreeneWeaponProgram.Shot::slot).toList();}
    @Test void deathFlowerUsesFiveActualPortsAndOneExecuteOffset(){
        var p=GreeneWeaponProgram.DEATH_FLOWER;
        assertEquals(List.of(),slots(p,46));assertEquals(List.of(2,6,4,7,3),slots(p,47));
        assertEquals(List.of(2,6,4,7,3),slots(p,49));assertEquals(List.of(),slots(p,50));
        assertEquals(123,p.action().duration());assertEquals(8,p.action().segments.length);
        assertTrue(p.shots().stream().allMatch(s->s.fixed()&&s.ammo()==GreeneMissileFlight.Ammo.DEATH_FLOWER));
    }
    @Test void shotgunAlternatesTwoPortsWithoutInventingAnInnerPelletBatch(){
        var p=GreeneWeaponProgram.SHOTGUN;
        assertEquals(List.of(9),slots(p,7));assertEquals(List.of(9),slots(p,10));assertEquals(List.of(),slots(p,11));
        assertEquals(List.of(8),slots(p,12));assertEquals(List.of(8),slots(p,14));assertEquals(List.of(),slots(p,15));
        assertEquals(7,countShots(p));assertTrue(p.shots().stream().noneMatch(GreeneWeaponProgram.Shot::fixed));
    }
    @Test void continuousSourceRequestsAreSampledAtActualServerRate(){
        assertEquals(5,countShots(GreeneWeaponProgram.DEATH_FLOWER));
        assertEquals(8,countShots(GreeneWeaponProgram.INSECT_SWARM));
        assertEquals(10,countShots(GreeneWeaponProgram.RIFLE));
        assertEquals(4,countShots(GreeneWeaponProgram.ANTI_TANK));
        assertEquals(4,countShots(GreeneWeaponProgram.ANTI_AIR));
    }
    @Test void antiTankKeepsTheOriginalAntiAirUserAndIsSeparateFromOnFootStates(){
        var shot=GreeneWeaponProgram.ANTI_TANK.shots().get(0);
        assertEquals(10,shot.slot());assertEquals(GreeneMissileFlight.User.ANTI_AIR,shot.user());
        assertEquals(GreeneMissileFlight.Ammo.ANTI_TANK,shot.ammo());
        assertFalse(shot.fixed());assertTrue(GreeneWeaponProgram.ANTI_AIR.antiVehicle());
        for(double distance:new double[]{2,30,50,80})assertFalse(GreeneWeaponProgram.onFoot(distance).antiVehicle());
    }
    @Test void originalDistanceBankUsesStrictOrderedBoundaries(){
        assertEquals(GreeneWeaponProgram.DEATH_FLOWER,GreeneWeaponProgram.onFoot(Math.nextUp(60D)));
        assertEquals(GreeneWeaponProgram.RIFLE,GreeneWeaponProgram.onFoot(60));
        assertEquals(GreeneWeaponProgram.RIFLE,GreeneWeaponProgram.onFoot(Math.nextUp(40D)));
        assertEquals(GreeneWeaponProgram.SHOTGUN,GreeneWeaponProgram.onFoot(40));
        assertEquals(GreeneWeaponProgram.SHOTGUN,GreeneWeaponProgram.onFoot(Math.nextUp(25D)));
        assertEquals(GreeneWeaponProgram.INSECT_SWARM,GreeneWeaponProgram.onFoot(25));
        assertEquals(GreeneWeaponProgram.INSECT_SWARM,GreeneWeaponProgram.onFoot(Math.nextUp(1D)));
        assertNull(GreeneWeaponProgram.onFoot(1));
    }
    @Test void originalParentGateAndInvalidDistancesCannotChooseAWeapon(){
        assertEquals(GreeneWeaponProgram.DEATH_FLOWER,GreeneWeaponProgram.onFoot(Math.nextDown(150D)));
        for(double distance:new double[]{-1,0,150,175,Double.NaN,Double.POSITIVE_INFINITY})assertNull(GreeneWeaponProgram.onFoot(distance));
    }
    @Test void shotgunTimerIsFromEntryAndOtherProgramsDoNotInventReloadTimers(){
        assertEquals(54,GreeneWeaponProgram.SHOTGUN.shootGateTicks());
        assertEquals(47,GreeneWeaponProgram.SHOTGUN.action().duration());
        for(var program:List.of(GreeneWeaponProgram.DEATH_FLOWER,GreeneWeaponProgram.RIFLE,GreeneWeaponProgram.INSECT_SWARM))assertEquals(0,program.shootGateTicks());
        var situation=new EncounterTactics.Situation(30,0,1,false,1,0,EncounterMove.VOLLEY);
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE,situation,m->false));
        assertEquals(EncounterMove.HOWL,EncounterTactics.choose(com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE,situation,m->m==EncounterMove.HOWL));
    }
    private static int countShots(GreeneWeaponProgram program){
        var clocks=new GreeneWeaponCadence[10];for(int i=0;i<10;i++)clocks[i]=new GreeneWeaponCadence();int result=0;
        for(int age=0;age<program.action().duration();age++)for(int slot=1;slot<=10;slot++){
            GreeneWeaponProgram.Shot request=null;for(var shot:program.shots())if(shot.slot()==slot&&shot.active(age))request=shot;
            if(clocks[slot-1].tick(.05,request!=null,request==null?0:request.ammo().fireRate,()->true))result++;
        }
        return result;
    }
}
