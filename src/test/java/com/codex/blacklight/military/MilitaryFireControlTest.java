package com.codex.blacklight.military;

import com.codex.blacklight.firearms.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryFireControlTest {
    private static List<Integer> shots(WeaponDefinition weapon){
        var plan=MilitaryFireControl.plan(weapon,MilitaryCombatProfile.of(MilitaryFaction.BLACKWATCH,false));
        var state=new AmmoState(weapon.capacity(),0,0);var result=new ArrayList<Integer>();
        for(int tick=0;tick<plan.cycleTicks();tick++)if(plan.canShoot(tick,state,tick)){
            result.add(tick);state=state.fire(weapon,tick);
        }
        return result;
    }
    @Test void firearmRateDrivesShotsInsideTheAnimationWindow(){
        assertEquals(List.of(12,16,20),shots(WeaponCatalog.RIFLE));
        assertEquals(List.of(12,15,18,21),shots(WeaponCatalog.CARBINE));
        assertEquals(List.of(12,15,18,21,24,27,30,33,36,39),shots(WeaponCatalog.MACHINE_GUN));
        assertEquals(List.of(12),shots(WeaponCatalog.GRENADE));
    }
    @Test void delayedReadinessDoesNotLoseTheWholeBurst(){
        var p=MilitaryFireControl.plan(WeaponCatalog.RIFLE,MilitaryCombatProfile.of(MilitaryFaction.BLACKWATCH,false));
        assertTrue(p.canShoot(13,new AmmoState(5,0,13),13));
        assertFalse(p.canShoot(22,new AmmoState(5,0,13),22));
        assertEquals(MilitaryFireControl.RECOVER,p.phase(22));
    }
    @Test void cooldownAndTacticalRecoveryAreNeverReload(){
        var weapon=WeaponCatalog.RIFLE;var state=new AmmoState(1,20,0).fire(weapon,100);
        assertFalse(MilitaryFireControl.reloading(state,weapon,-1,101));
        var loaded=state.reload(weapon,104);
        assertTrue(MilitaryFireControl.reloading(loaded,weapon,104,105));
        assertFalse(MilitaryFireControl.reloading(loaded,weapon,104,144));
        assertEquals(20,loaded.magazine());assertEquals(0,loaded.reserve());
    }
    @Test void meleeOnlyContactsDuringTheRecoveredEventWindow(){
        for(int tick=0;tick<MilitaryFireControl.MELEE_TICKS;tick++)
            assertEquals(tick==9||tick==10,MilitaryFireControl.meleeContact(tick));
    }
}
