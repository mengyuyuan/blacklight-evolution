package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Lethal entry, crash ticks, durable receipts and callback replacement. Not run. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class HelicopterDestructionLifecycleGameTest {
    private static final class Probe {
        final BlackwatchHelicopter actor;int sounds,blasts;Runnable callback;
        Probe(BlackwatchHelicopter actor){this.actor=actor;}
    }
    private static final Map<BlackwatchHelicopter,Probe> PROBES=new IdentityHashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void sound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event){
        if(event.getLevel().isClientSide||event.getSound()==null||!event.getSound().value().getLocation().toString().equals("minecraft:block.anvil.break"))return;
        for(var probe:List.copyOf(PROBES.values()))if(probe.actor.level()==event.getLevel()
                &&probe.actor.position().add(0,1,0).distanceToSqr(event.getPosition())<.0001){
            probe.sounds++;var callback=probe.callback;probe.callback=null;if(callback!=null)callback.run();
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Start event){
        var probe=PROBES.get(event.getExplosion().getDirectSourceEntity());if(probe!=null)probe.blasts++;
    }
    private static CompoundTag save(BlackwatchHelicopter actor){return actor.saveWithoutId(new CompoundTag());}
    private static BlackwatchHelicopter actor(GameTestHelper h,boolean floor){
        if(floor)for(int x=8;x<40;x++)for(int z=3;z<29;z++)h.setBlock(x,1,z,Blocks.STONE);
        var actor=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());actor.setPos(h.absoluteVec(new Vec3(24,floor?2:18,16)));
        h.getLevel().addFreshEntity(actor);return actor;
    }
    private static void done(BlackwatchHelicopter... actors){for(var actor:actors){PROBES.remove(actor);actor.discard();}}
    @GameTest(template="helicopter_arena",batch="airCrashLifecycle",timeoutTicks=40)
    public static void finalCrashReceiptIsSavedBeforeEffectsAndNeverReplaysAfterLoad(GameTestHelper h){
        var actor=actor(h,true);var probe=new Probe(actor);PROBES.put(actor,probe);CompoundTag[] snapshot={null};
        probe.callback=()->snapshot[0]=save(actor);
        try{
            h.assertTrue(VehicleKinematics.loaded(h.getLevel(),new net.minecraft.world.phys.AABB(actor.position().add(0,1,0),actor.position().add(0,1,0)).inflate(7)),"fixture has the complete physical blast neighborhood loaded");
            actor.hurt(h.getLevel().damageSources().generic(),400);actor.tick();
            h.assertTrue(actor.isRemoved()&&probe.sounds==1&&probe.blasts==1&&snapshot[0]!=null,"ground contact emits exactly one presentation and physical explosion");
            h.assertTrue(snapshot[0].getBoolean("CrashExplosionCommitted")&&snapshot[0].getFloat("MechanicalHealth")==0,"terminal receipt precedes the external sound callback");
            PROBES.remove(actor);var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());restored.load(snapshot[0]);
            var resumed=new Probe(restored);PROBES.put(restored,resumed);
            try{restored.tick();h.assertTrue(restored.isRemoved()&&resumed.sounds==0&&resumed.blasts==0,"restored terminal hull disappears without replaying either effect");}
            finally{done(restored);}
        }finally{done(actor);}h.succeed();
    }
    @GameTest(template="helicopter_arena",batch="airCrashLifecycle",timeoutTicks=40)
    public static void healthyReloadFromCrashSoundStopsTheOldPhysicalExplosionAndDiscard(GameTestHelper h){
        var actor=actor(h,true);var healthy=save(actor);var probe=new Probe(actor);PROBES.put(actor,probe);probe.callback=()->actor.load(healthy);
        try{
            actor.hurt(h.getLevel().damageSources().generic(),400);actor.tick();
            h.assertTrue(probe.sounds==1&&probe.blasts==0&&!actor.isRemoved()&&actor.health()==360,"callback replacement ends the old crash before blast and discard");
            actor.tick();h.assertTrue(!actor.isRemoved()&&actor.phase()==BlackwatchHelicopter.Phase.IDLE&&!save(actor).getBoolean("CrashExplosionCommitted"),"healthy replacement remains usable on its next tick");
        }finally{done(actor);}h.succeed();
    }
    @GameTest(template="helicopter_arena",batch="airCrashLifecycle",timeoutTicks=40)
    public static void blockedCrashStillTimesOutWithoutQueryingAnUnavailableBlastNeighborhood(GameTestHelper h){
        var actor=actor(h,false);var saved=save(actor);saved.putInt("FlightPhase",BlackwatchHelicopter.Phase.CRASH.ordinal());saved.putInt("PhaseAge",299);
        // Pilot loss can disable a mechanically healthy craft with no delivery mission.
        saved.remove("CrashExplosionCommitted");actor.load(saved);actor.setPos(actor.getX(),h.getLevel().getMaxBuildHeight()+2,actor.getZ());
        var probe=new Probe(actor);PROBES.put(actor,probe);
        try{
            h.assertTrue(actor.phase()==BlackwatchHelicopter.Phase.CRASH&&actor.health()==360,"reload retains a pending pilot-loss crash, even without home/landing");
            h.assertTrue(!VehicleKinematics.loaded(h.getLevel(),actor.getBoundingBox()),"world-height guard deterministically refuses movement, exercising the same early-return branch as an unloaded neighbor");
            actor.tick();h.assertTrue(!actor.isRemoved()&&probe.sounds==0,"existing 300-tick boundary remains strict");
            actor.tick();h.assertTrue(actor.isRemoved()&&probe.sounds==1&&probe.blasts==0,"tick301 completes visible wreck/impact audio without a physical query beyond the safe neighborhood");
        }finally{done(actor);}h.succeed();
    }
    @GameTest(template="helicopter_arena",batch="airCrashLifecycle",timeoutTicks=40)
    public static void unloadedPendingCrashResumesFallingWithoutPrematureExplosion(GameTestHelper h){
        var actor=actor(h,false);actor.hurt(h.getLevel().damageSources().generic(),400);var saved=save(actor);
        h.assertTrue(!saved.getBoolean("CrashExplosionCommitted"),"lethal damage only starts the fall");actor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());restored.load(saved);var probe=new Probe(restored);PROBES.put(restored,probe);
        try{
            double y=restored.getY();restored.tick();
            h.assertTrue(restored.phase()==BlackwatchHelicopter.Phase.CRASH&&!restored.isRemoved()&&restored.getY()<y&&probe.sounds==0&&probe.blasts==0,"one observed post-load update continues the real pending fall instead of replaying an impact");
        }finally{done(restored);}h.succeed();
    }
}
