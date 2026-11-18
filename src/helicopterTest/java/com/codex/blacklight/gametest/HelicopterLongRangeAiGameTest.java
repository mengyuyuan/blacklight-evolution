package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Natural six-person rappel followed by remote acquisition/combat. No forced
 * phase, direct combat tick/fire, seeded bank draw or invulnerable receiver. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterLongRangeAiGameTest {
    @GameTest(template="helicopter_arena",batch="airLongAiAcquisition",timeoutTicks=2220)
    public static void aiAcquiresBeyondOldLeashThenDamagesGroundEnemy(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,342048);var level=f.level;
        for(int cx=(f.x-80)>>4;cx<=(f.x+80)>>4;cx++)for(int cz=-5;cz<=13;cz++){
            long key=ChunkPos.asLong(cx,cz);f.forced.putIfAbsent(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);
        }
        for(int dx=-64;dx<=64;dx++)for(int z=-64;z<=200;z++){
            level.setBlock(new BlockPos(f.x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=81;y<=120;y++){var p=new BlockPos(f.x+dx,y,z);if(!level.getBlockState(p).isAir())level.setBlock(p,Blocks.AIR.defaultBlockState(),2);}
        }
        var craft=f.craft(0);
        var target=ModEntities.COMMON_INFECTED.get().create(level);var anchor=new Vec3(f.x,81,130);
        target.setPos(anchor);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        f.entities.add(target);
        int[] ticks={0},observedShots={0};double[] acquiredAt={-1},farthestShot={0};boolean[] done={false},ready={false};var tactics=new java.util.LinkedHashSet<String>();
        var phases=java.util.EnumSet.noneOf(BlackwatchHelicopter.Phase.class);var landed=new java.util.HashSet<java.util.UUID>();
        h.onEachTick(()->{
            if(done[0])return;
            if(!ready[0]){
                for(int z=0;z<=144;z+=16)if(!level.isPositionEntityTicking(new BlockPos(f.x,81,z)))return;
                h.assertTrue(level.addFreshEntity(target),"persistent far target enters actual ticking sections");target.setHealth(target.getMaxHealth());
                for(int i=0;i<2;i++){
                    var crew=ModEntities.BLACKWATCH_SOLDIER.get().create(level);crew.setPos(craft.position());crew.setPersistenceRequired();crew.setAutomaticUnitJoin(false);
                    crew.initializeNativeLoadout("long-air-qa:"+i);h.assertTrue(level.addFreshEntity(crew),"real crew enters world");f.entities.add(crew);
                    h.assertTrue(crew.startRiding(craft,true),"two real crew board");
                }
                h.assertTrue(craft.deployRappellingTo(new Vec3(f.x,81,0)),"public natural rappel mission accepts its supported site");
                h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.SPIN_UP,"actual grounded rotor startup");
                var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);caller.setPos(f.x-10,81,0);caller.setAutomaticUnitJoin(false);
                h.assertTrue(level.addFreshEntity(caller),"real caller enters world");craft.combat().supportCall(caller);caller.discard();
                h.assertTrue(craft.combat().start(),"undesignated support request starts with operational crew");ready[0]=true;
            }
            int t=++ticks[0];phases.add(craft.phase());
            if(!target.isAlive()||target.isRemoved()||level.getEntity(target.getUUID())!=target){done[0]=true;f.close();h.fail("persistent remote AI target vanished at tick="+t);return;}
            target.setPos(anchor);target.setDeltaMovement(Vec3.ZERO);
            for(var troop:level.getEntitiesOfClass(MilitarySoldier.class,new net.minecraft.world.phys.AABB(f.x-100,60,-100,f.x+100,150,210),s->{var data=s.getPersistentData();return data.hasUUID("HelicopterDeliveredBy")&&data.getUUID("HelicopterDeliveredBy").equals(craft.getUUID());})){
                if(!f.entities.contains(troop))f.entities.add(troop);
                if(!com.codex.blacklight.military.aircraft.HelicopterRappel.active(troop)&&troop.onGround()&&!troop.isNoAi()&&!troop.isNoGravity())landed.add(troop.getUUID());
            }
            if(target.getUUID().equals(craft.combat().targetId())&&acquiredAt[0]<0)acquiredAt[0]=craft.position().distanceTo(target.position());
            tactics.add(craft.combat().tactic());
            if(craft.combat().shots()>observedShots[0]){observedShots[0]=craft.combat().shots();farthestShot[0]=Math.max(farthestShot[0],craft.position().distanceTo(target.position()));}
            var damage=target.getLastDamageSource();
            if(damage!=null&&craft.combat().operator()!=null&&damage.getEntity()==craft.combat().operator()&&craft.combat().operator().getVehicle()==craft
                    &&(damage.getDirectEntity()==craft||damage.getDirectEntity() instanceof HellfireBlastEntity)){
                done[0]=true;
                try(f){
                    h.assertTrue(acquiredAt[0]>120,"undesignated target was actually acquired beyond both old 64 search and 96 retention limits: "+acquiredAt[0]);
                    h.assertTrue(target.getHealth()<target.getMaxHealth(),"aircraft operator caused accepted health damage");
                    h.assertTrue(observedShots[0]>0,"actual combat produced a weapon shot");
                    h.assertTrue(landed.size()>=6,"real six-person rappel completed before distant combat");
                    h.assertTrue(phases.containsAll(java.util.List.of(BlackwatchHelicopter.Phase.SPIN_UP,BlackwatchHelicopter.Phase.CLIMB,BlackwatchHelicopter.Phase.APPROACH,
                            BlackwatchHelicopter.Phase.DESCEND,BlackwatchHelicopter.Phase.UNLOAD,BlackwatchHelicopter.Phase.DEPART,BlackwatchHelicopter.Phase.PURSUIT,BlackwatchHelicopter.Phase.STRAFE)),"full natural mission phases observed: "+phases);
                    com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_LONG_AI_VERIFIED acquireDistance={} farthestShotDistance={} hitDistance={} weapon={} health={} tactics={} phases={} landed={}",
                            acquiredAt[0],farthestShot[0],craft.position().distanceTo(target.position()),damage.getDirectEntity()==craft?"30mm":"Hellfire",target.getHealth(),tactics,phases,landed.size());
                }
                h.succeed();
            }
        });
        h.runAfterDelay(2200,()->{if(!done[0]){done[0]=true;String message="AI far acquisition/combat failed: ready="+ready[0]+" ticks="+ticks[0]+" acquire="+acquiredAt[0]+" shots="+observedShots[0]+" farthestShot="+farthestShot[0]+" phase="+craft.phase()+" hp="+target.getHealth()+" tactics="+tactics+" phases="+phases+" landed="+landed.size();f.close();h.fail(message);}});
    }
}
