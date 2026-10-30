package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.ModEntities;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.*;

/** Real EntityJoin, vanilla target selectors and natural server AI; never calls vehicle.hurt. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleHullAttackGameTest {
    private static boolean handles(Mob actor){
        try{return (Boolean)Class.forName("com.codex.blacklight.military.vehicle.VehicleHullAttackGoal").getMethod("handles",Mob.class).invoke(null,actor);}
        catch(InvocationTargetException failure){throw new GameTestAssertException("real hull-target entry failed: "+failure.getCause());}
        catch(ReflectiveOperationException failure){throw new GameTestAssertException("real hull-target entry missing: "+failure);}
    }
    private static void durable(LivingEntity entity){
        if(entity instanceof Mob mob)mob.setPersistenceRequired();
        var health=entity.getAttribute(Attributes.MAX_HEALTH);if(health!=null){health.setBaseValue(400);entity.setHealth(entity.getMaxHealth());}
    }
    private static MilitaryVehicle tank(MilitaryVehicleGameTest.Fixture f){
        var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);return vehicle;
    }
    private static LivingEntity villager(MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle){
        var target=EntityType.VILLAGER.create(f.level);durable(target);target.setNoAi(true);target.setPos(vehicle.position());f.level.addFreshEntity(target);f.entities.add(target);
        if(!target.startRiding(vehicle))throw new GameTestAssertException("actual villager could not physically board");return target;
    }
    private static ServerPlayer player(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle){
        var target=MilitaryVehicleGameTest.mockPlayer(h);target.setGameMode(GameType.SURVIVAL);durable(target);target.setPos(vehicle.position());f.entities.add(target);
        h.assertTrue(target.startRiding(vehicle),"actual survival player physically occupies the real vehicle");return target;
    }
    private static Mob actor(MilitaryVehicleGameTest.Fixture f,String kind){
        Mob actor=switch(kind){
            case "hunter"->ModEntities.BLACKLIGHT_HUNTER.get().create(f.level);
            case "host"->{var source=EntityType.ZOMBIE.create(f.level);var host=ModEntities.INFECTED_HOST.get().create(f.level);host.inherit(source);source.discard();yield host;}
            case "brute"->ModEntities.BLACKLIGHT_BRUTE.get().create(f.level);
            default->throw new GameTestAssertException("unknown actual fixture species "+kind);
        };
        actor.setPersistenceRequired();actor.setPos(f.x+5,81,2);f.level.addFreshEntity(actor);f.entities.add(actor);return actor;
    }
    private static String diagnostic(Mob actor,LivingEntity target,MilitaryVehicle vehicle){
        return "actor="+actor.getType()+" alive="+actor.isAlive()+" noAi="+actor.isNoAi()+" ticks="+actor.tickCount+" entityTicking="+((net.minecraft.server.level.ServerLevel)actor.level()).isPositionEntityTicking(actor.blockPosition())+" canAttack="+actor.canAttack(target)+" currentTarget="+(actor.getTarget()==null?"none":actor.getTarget().getUUID())
                +" expected="+target.getUUID()+" targetAlive="+target.isAlive()+" targetRemoved="+target.isRemoved()+" riding="+(target.getVehicle()==vehicle)
                +" distance="+actor.distanceTo(target)+" LOS="+actor.hasLineOfSight(target)+" actorPos="+actor.position()+" targetPos="+target.position()+" hullHp="+vehicle.health();
    }
    private static void fail(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,String reason){f.close();h.assertTrue(false,reason);}
    private static void awaitTarget(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Mob actor,LivingEntity target,MilitaryVehicle vehicle,int remaining,Runnable ready){
        if(actor.getTarget()==target){ready.run();return;}
        if(remaining==0){fail(h,f,"existing natural target selector must acquire its already-legal actual passenger: "+diagnostic(actor,target,vehicle));return;}
        h.runAfterDelay(1,()->awaitTarget(h,f,actor,target,vehicle,remaining-1,ready));
    }
    private static void awaitDamage(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Mob actor,LivingEntity target,MilitaryVehicle vehicle,float before,int remaining,Runnable ready){
        if(vehicle.health()<before&&actor.getTarget()==target&&target.getVehicle()==vehicle&&target.isAlive()){ready.run();return;}
        if(remaining==0){fail(h,f,"natural AI must damage actual mechanical durability instead of pursuing or hitting only the seat occupant: "+diagnostic(actor,target,vehicle));return;}
        h.runAfterDelay(1,()->awaitDamage(h,f,actor,target,vehicle,before,remaining-1,ready));
    }
    private static void species(GameTestHelper h,int x,String kind,boolean playerTarget){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);var vehicle=tank(f);LivingEntity target=playerTarget?player(h,f,vehicle):villager(f,vehicle);float before=vehicle.health();var actor=actor(f,kind);
        h.runAfterDelay(20,()->awaitTarget(h,f,actor,target,vehicle,60,()->awaitDamage(h,f,actor,target,vehicle,before,100,()->{try(f){
            h.assertTrue(handles(actor),"actual joined creature is handled by the installed production hull goal");
            h.assertTrue(actor.goalSelector.getAvailableGoals().stream().anyMatch(wrapped->wrapped.getGoal().getClass().getName().equals("com.codex.blacklight.military.vehicle.VehicleHullAttackGoal")),"EntityJoin installed the actual mechanical attack goal without a test adding it");
            h.assertTrue(actor.getTarget()==target&&target.getVehicle()==vehicle,"living target identity and its original actual passenger are preserved");
            h.assertTrue(vehicle.health()>0&&!vehicle.isRemoved(),"the same actual chassis remains alive with reduced mechanical durability");
        }h.succeed();})));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=240)
    public static void naturalHunterTargetsActualMountedVillagerAndHitsMechanicalHull(GameTestHelper h){species(h,401000,"hunter",false);}
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=240)
    public static void naturalGroundHostTargetsActualMountedVillagerAndHitsMechanicalHull(GameTestHelper h){species(h,402000,"host",false);}
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=240)
    public static void naturalBruteTargetsActualMountedSurvivalPlayerAndHitsMechanicalHull(GameTestHelper h){species(h,403000,"brute",true);}

    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=560)
    public static void actualDismountAndPassengerDeathCancelPendingMechanicalAttacks(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,404000);var vehicle=tank(f);var target=villager(f,vehicle);var actor=actor(f,"hunter");float initial=vehicle.health();
        h.runAfterDelay(20,()->awaitTarget(h,f,actor,target,vehicle,180,()->awaitDamage(h,f,actor,target,vehicle,initial,80,()->{
            target.stopRiding();target.setPos(f.x+7,81,2);target.setInvulnerable(true);float afterExit=vehicle.health();
            h.assertTrue(!handles(actor),"leaving the actual seat immediately removes mechanical target handling");
            // Entity.removePassenger imposes a real 60-tick boarding cooldown; do not force-mount.
            h.runAfterDelay(65,()->{
                h.assertTrue(vehicle.health()==afterExit,"a previously active AI attack cannot keep damaging an empty chassis after actual dismount");
                h.assertTrue(target.isAlive()&&f.level.getEntity(target.getUUID())==target,"the original dismounted target survives the isolated cancellation window without conversion: "+diagnostic(actor,target,vehicle));
                target.setInvulnerable(false);target.setPos(vehicle.position());h.assertTrue(target.startRiding(vehicle),"the same actual living villager reboards after the ordinary cooldown without cloning: "+diagnostic(actor,target,vehicle));
                awaitTarget(h,f,actor,target,vehicle,40,()->awaitDamage(h,f,actor,target,vehicle,afterExit,80,()->{
                    target.setHealth(0);float afterDeath=vehicle.health();h.assertTrue(!handles(actor),"actual passenger death immediately cancels mechanical targeting");
                    h.runAfterDelay(25,()->{try(f){h.assertTrue(vehicle.health()==afterDeath,"dead seat identity never leaves a continuing mechanical attack");}h.succeed();});
                }));
            });
        })));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=260)
    public static void actualPassengerDimensionChangeCancelsMechanicalAttackWithoutFollowingOrLoadingOldTarget(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,405000);var vehicle=tank(f);var target=player(h,f,vehicle);var actor=actor(f,"brute");float initial=vehicle.health();
        h.runAfterDelay(20,()->awaitTarget(h,f,actor,target,vehicle,60,()->awaitDamage(h,f,actor,target,vehicle,initial,100,()->{
            var dimension=f.level.dimension().equals(Level.NETHER)?Level.OVERWORLD:Level.NETHER;var elsewhere=f.level.getServer().getLevel(dimension);
            h.assertTrue(elsewhere!=null&&elsewhere!=f.level,"fixture has a real distinct server dimension");target.teleportTo(elsewhere,0.5,100,0.5,0,0);float before=vehicle.health();
            h.assertTrue(target.level()!=f.level&&target.getVehicle()!=vehicle&&!handles(actor),"actual server dimension transfer invalidates the original passenger-to-hull association");
            h.runAfterDelay(25,()->{try(f){h.assertTrue(vehicle.health()==before&&actor.level()==f.level,"the original loaded actor stops mechanical damage and remains in its actual dimension");}h.succeed();});
        })));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=180)
    public static void alliedCrewAndEmptyChassisDoNotBecomeNewEnemies(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,406000);var vehicle=tank(f);var crew=ModEntities.MILITARY_SOLDIER.get().create(f.level);crew.setAutomaticUnitJoin(false);crew.setPersistenceRequired();crew.setNoAi(true);crew.setPos(vehicle.position());f.level.addFreshEntity(crew);f.entities.add(crew);
        h.assertTrue(crew.startRiding(vehicle),"actual regular-army crew boards its original faction chassis");
        var allied=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);allied.setAutomaticUnitJoin(false);allied.setPersistenceRequired();allied.setPos(f.x+5,81,2);f.level.addFreshEntity(allied);f.entities.add(allied);float initial=vehicle.health();
        h.runAfterDelay(45,()->{
            h.assertTrue(allied.isAlliedTo(crew)&&!allied.validTarget(crew)&&allied.getTarget()!=crew&&!handles(allied),"existing Army/BW alliance remains authoritative for a real mounted crew member");
            h.assertTrue(vehicle.health()==initial,"a friendly occupied chassis receives no new proactive attacks");allied.discard();crew.discard();
            var hunter=actor(f,"hunter");
            h.runAfterDelay(45,()->{try(f){h.assertTrue(vehicle.getPassengers().isEmpty()&&hunter.getTarget()==null&&!handles(hunter)&&vehicle.health()==initial,"an actual empty chassis never becomes a new living target or proactive enemy");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6hullai",timeoutTicks=260)
    public static void actualInfantryKeepsLegalMountedInfectedTargetAndSpendsFiniteRifleAmmo(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,407000);var vehicle=tank(f);var source=EntityType.COW.create(f.level);var target=ModEntities.INFECTED_HOST.get().create(f.level);target.inherit(source);source.discard();durable(target);target.setNoAi(true);target.setPos(vehicle.position());f.level.addFreshEntity(target);f.entities.add(target);
        h.assertTrue(target.startRiding(vehicle)&&BlacklightInfectionSystem.isInfected(target),"actual mature infected host physically occupies a seat and is already a legal infection enemy");
        var actor=ModEntities.MILITARY_SOLDIER.get().create(f.level);actor.setAutomaticUnitJoin(false);actor.setPersistenceRequired();actor.setPos(f.x+7,81,2);actor.initializeWeapon();int ammunition=actor.reserveAmmo()+actor.magazineAmmo();f.level.addFreshEntity(actor);f.entities.add(actor);float initial=vehicle.health();
        h.runAfterDelay(20,()->awaitTarget(h,f,actor,target,vehicle,60,()->awaitDamage(h,f,actor,target,vehicle,initial,120,()->{try(f){
            h.assertTrue(actor.validTarget(target)&&actor.getTarget()==target,"actual infantry target remains the same already-legal living infected passenger");
            h.assertTrue(actor.reserveAmmo()+actor.magazineAmmo()<ammunition,"real mechanical damage uses and consumes the original finite rifle stock");
        }h.succeed();})));
    }
    private VehicleHullAttackGameTest(){}
}
