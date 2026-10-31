package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.entity.BlackwatchSoldier;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.RegularSoldier;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

/** The real production EntityJoin installation gate; no duplicated type-selection test model. */
class VehicleHullAttackGoalTest {
    private static boolean supports(Class<?> type){
        try{
            var entry=Class.forName("com.codex.blacklight.military.vehicle.VehicleHullAttackGoal");
            return (Boolean)entry.getMethod("supports",Class.class).invoke(null,new Object[]{type});
        }catch(InvocationTargetException failure){throw new AssertionError("production mechanical-goal installation gate failed",failure.getCause());}
        catch(ReflectiveOperationException missing){throw new AssertionError("production mechanical-goal installation gate missing",missing);}
    }
    @Test void actualExistingMilitaryAndInfectedCreatureFamiliesAreSupported(){
        for(var type:new Class<?>[]{MilitarySoldier.class,RegularSoldier.class,BlackwatchSoldier.class,BlacklightHunter.class,BlacklightMutant.class,BlacklightInfectedHost.class})
            assertTrue(supports(type),"actual supported entity family: "+type.getName());
    }
    @Test void ordinaryVanillaMobsPlayersAndMechanicalEntitiesAreRejected(){
        for(var type:new Class<?>[]{Zombie.class,Player.class,MilitaryVehicle.class,Mob.class,LivingEntity.class,Entity.class})
            assertFalse(supports(type),"installation must stay narrow rather than extending every mob or treating vehicles as living targets: "+type.getName());
    }
    @Test void absentTypeCannotInstallOrAcquireAMechanicalEnemy(){assertFalse(supports(null));}
}
