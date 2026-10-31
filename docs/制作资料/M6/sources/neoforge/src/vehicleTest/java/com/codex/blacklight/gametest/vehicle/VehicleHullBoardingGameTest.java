package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.VehicleHullAttackGoal;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

/** Natural old windups must release a living passenger when its real mechanical goal takes over. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleHullBoardingGameTest {
    @SuppressWarnings("unchecked") private static int mutantPending(BlacklightMutant actor){
        try{
            var field=BlacklightMutant.class.getDeclaredField("ATTACK");field.setAccessible(true);
            return actor.getEntityData().get((EntityDataAccessor<Integer>)field.get(null));
        }catch(ReflectiveOperationException failure){throw new GameTestAssertException("cannot observe the actual existing mutant windup: "+failure);}
    }
    private static boolean pending(Mob actor){
        if(actor instanceof BlacklightHunter hunter)return hunter.committedAttack();
        if(actor instanceof BlacklightMutant mutant)return mutantPending(mutant)>0;
        return actor instanceof BlacklightInfectedHost host&&host.tissueStrikeElapsed(0)>=0;
    }
    private static String diagnostic(Mob actor,LivingEntity target,MilitaryVehicle vehicle){
        return "actor="+actor.getType()+" pending="+pending(actor)+" target="+(actor.getTarget()==target)
                +" targetAlive="+target.isAlive()+" riding="+(target.getVehicle()==vehicle)+" handled="+VehicleHullAttackGoal.handles(actor)
                +" actorPos="+actor.position()+" targetPos="+target.position()+" health="+target.getHealth()+"/"+target.getMaxHealth()+" hull="+vehicle.health();
    }
    private static void fail(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,String reason){f.close();h.assertTrue(false,reason);}
    private static void awaitWindup(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Mob actor,LivingEntity target,MilitaryVehicle vehicle,int remaining){
        if(actor.getTarget()!=target||!pending(actor)){
            if(remaining==0){fail(h,f,"existing AI must naturally acquire and begin its own original attack before boarding: "+diagnostic(actor,target,vehicle));return;}
            h.runAfterDelay(1,()->awaitWindup(h,f,actor,target,vehicle,remaining-1));return;
        }
        h.assertTrue(target.getVehicle()==null,"the same real target starts outside a vehicle; the test never sets an AI target or attack state");
        target.setPos(vehicle.position());
        h.assertTrue(target.startRiding(vehicle),"the actual living target boards through the ordinary seat entry during its attacker's real windup");
        float fraction=target.getHealth()/target.getMaxHealth(),health=vehicle.health();
        h.runAfterDelay(2,()->{
            if(pending(actor)){fail(h,f,"old pending living-target attack must cancel when the real mechanical goal owns the mounted target: "+diagnostic(actor,target,vehicle));return;}
            h.runAfterDelay(65,()->{try(f){
                h.assertTrue(actor.getTarget()==target&&target.getVehicle()==vehicle,"the original living target remains authoritative after boarding");
                h.assertTrue(Math.abs(target.getHealth()/target.getMaxHealth()-fraction)<.0001,"the old pending strike cannot damage or grapple the real passenger through the chassis: "+diagnostic(actor,target,vehicle));
                h.assertTrue(vehicle.health()<health&&vehicle.health()>0,"the actual creature instead approaches and damages the same mechanical durability: "+diagnostic(actor,target,vehicle));
            }h.succeed();});
        });
    }
    private static void scenario(GameTestHelper h,int x,String kind){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);vehicle.setPos(f.x-6,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
        LivingEntity target;
        if(kind.equals("brute")){
            var player=MilitaryVehicleGameTest.mockPlayer(h);player.setGameMode(GameType.SURVIVAL);target=player;f.entities.add(player);
        }else{
            var villager=EntityType.VILLAGER.create(f.level);villager.setNoAi(true);villager.setPersistenceRequired();f.level.addFreshEntity(villager);f.entities.add(villager);target=villager;
        }
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);target.setHealth(target.getMaxHealth());target.setPos(f.x+.5,81,.5);
        Mob actor;
        if(kind.equals("hunter"))actor=ModEntities.BLACKLIGHT_HUNTER.get().create(f.level);
        else if(kind.equals("brute"))actor=ModEntities.BLACKLIGHT_BRUTE.get().create(f.level);
        else{
            var cow=EntityType.COW.create(f.level);var host=ModEntities.INFECTED_HOST.get().create(f.level);host.inherit(cow);cow.discard();
            host.advanceTissue(816000);actor=host;
        }
        actor.setPersistenceRequired();actor.setPos(f.x+(kind.equals("host")?4.5:3),81,.5);
        // Real attribute, deliberately small so a successful repeated mechanical attack preserves this test's chassis.
        actor.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        f.level.addFreshEntity(actor);f.entities.add(actor);
        h.runAfterDelay(1,()->awaitWindup(h,f,actor,target,vehicle,120));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6boarding",timeoutTicks=260)
    public static void actualHunterWindupCancelsWhenItsNaturalVillagerTargetBoards(GameTestHelper h){scenario(h,411000,"hunter");}
    @GameTest(template="blackwatch_arena",batch="vehicle6boarding",timeoutTicks=260)
    public static void actualBruteWindupCancelsWhenItsNaturalSurvivalPlayerTargetBoards(GameTestHelper h){scenario(h,412000,"brute");}
    @GameTest(template="blackwatch_arena",batch="vehicle6boarding",timeoutTicks=260)
    public static void actualMatureHostTendrilWindupCancelsWhenItsNaturalVillagerTargetBoards(GameTestHelper h){scenario(h,413000,"host");}
    private VehicleHullBoardingGameTest(){}
}
