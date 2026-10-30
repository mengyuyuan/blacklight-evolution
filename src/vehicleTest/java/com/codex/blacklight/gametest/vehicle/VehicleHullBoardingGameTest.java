package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.VehicleHullAttackGoal;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;

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
                +" actorPos="+actor.position()+" targetPos="+target.position()+" health="+target.getHealth()+"/"+target.getMaxHealth()+" hull="+vehicle.health()
                +" actorTicks="+actor.tickCount+" actorPublished="+(((net.minecraft.server.level.ServerLevel)actor.level()).getEntity(actor.getUUID())==actor)
                +" actorAlive="+actor.isAlive()+" actorRemoved="+actor.isRemoved()+" actorNoAi="+actor.isNoAi()+" actorRiding="+actor.isPassenger()
                +" actorTicking="+((net.minecraft.server.level.ServerLevel)actor.level()).isPositionEntityTicking(actor.blockPosition())
                +" actorLoaded="+com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(actor.level(),actor.getBoundingBox())
                +" stunned="+com.codex.blacklight.combat.ImpactControl.stunned(actor)+" consumed="+com.codex.blacklight.combat.ConsumeControl.ownsMotion(actor)
                +" alliedOrProtected="+com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(actor,target)+" canAttack="+actor.canAttack(target)
                +" actorWithinRestriction="+actor.isWithinRestriction()+" targetWithinRestriction="+actor.isWithinRestriction(target.blockPosition())
                +" targetSameLevel="+(target.level()==actor.level())+" targetRemoved="+target.isRemoved()
                +" targetCreative="+(target instanceof net.minecraft.world.entity.player.Player p&&p.isCreative())+" targetSpectator="+target.isSpectator()
                +" hullPublished="+(((net.minecraft.server.level.ServerLevel)actor.level()).getEntity(vehicle.getUUID())==vehicle)
                +" hullSameLevel="+(vehicle.level()==actor.level())+" hullRemoved="+vehicle.isRemoved()+" containsTarget="+vehicle.getPassengers().contains(target)
                +" hullProtected="+com.codex.blacklight.military.vehicle.VehicleDamageBridge.protectedHull(actor,vehicle)
                +" hullLoaded="+com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(actor.level(),vehicle.getBoundingBox())
                +" hullTicking="+((net.minecraft.server.level.ServerLevel)actor.level()).isPositionEntityTicking(vehicle.blockPosition());
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
        if(kind.equals("brute")){
            // This car occupies the neighbouring western section; FULL data alone does not
            // publish/tick its entity. Prepare only this fixture's bounded two-ring halo.
            for(int cx=((x-10)>>4)-2;cx<=((x+10)>>4)+2;cx++)for(int cz=-3;cz<=3;cz++){
                long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
                f.forced.putIfAbsent(key,f.level.getForcedChunks().contains(key));
                f.level.setChunkForced(cx,cz,true);f.level.getChunk(cx,cz);
            }
        }
        var vehicle=ModEntities.MILITARY_TANK.get().create(f.level);vehicle.setPos(f.x-6,81,.5);f.entities.add(vehicle);
        if(!f.level.addFreshEntity(vehicle)){
            fail(h,f,"native addFreshEntity rejected the actual fixture tank before any boarding attempt: uuid="+vehicle.getUUID()
                    +" existingUuid="+f.level.getEntity(vehicle.getUUID())+" removed="+vehicle.isRemoved()+" hp="+vehicle.health()
                    +" chunk="+vehicle.chunkPosition()+" chunkLoaded="+(f.level.getChunkSource().getChunkNow(vehicle.chunkPosition().x,vehicle.chunkPosition().z)!=null));return;
        }
        if(kind.equals("brute"))awaitPublishedChassis(h,f,vehicle,()->startActors(h,f,vehicle,kind));
        else startActors(h,f,vehicle,kind);
    }
    private static void awaitPublishedChassis(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle,Runnable ready){
        var checks=new java.util.concurrent.atomic.AtomicInteger();var published=new java.util.concurrent.atomic.AtomicBoolean();
        h.startSequence().thenWaitUntil(()->{
            for(int task=0;task<256&&f.level.getChunkSource().pollTask();task++){}
            if(f.level.getEntity(vehicle.getUUID())==vehicle&&f.level.isPositionEntityTicking(vehicle.blockPosition())){published.set(true);return;}
            if(checks.incrementAndGet()>=60)return;
            throw new GameTestAssertException("waiting for actual accepted chassis publication and entity ticking; check="+checks.get());
        }).thenExecute(()->{
            // thenWaitUntil assertions retry; bounded failure belongs in thenExecute so the
            // native sequence reports it permanently instead of retrying the failure forever.
            if(!published.get()){fail(h,f,"actual fixture chassis was accepted but not published and entity ticking within sixty bounded readiness checks: uuid="+vehicle.getUUID()
                    +" published="+(f.level.getEntity(vehicle.getUUID())==vehicle)+" ticking="+f.level.isPositionEntityTicking(vehicle.blockPosition())
                    +" removed="+vehicle.isRemoved()+" loaded="+com.codex.blacklight.military.vehicle.VehicleKinematics.loaded(f.level,vehicle.getBoundingBox()));return;}
            ready.run();
        });
    }
    private static void startActors(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle vehicle,String kind){
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
