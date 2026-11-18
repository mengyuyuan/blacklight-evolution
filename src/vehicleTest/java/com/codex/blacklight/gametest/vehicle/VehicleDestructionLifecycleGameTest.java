package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;

/** Real lethal damage, sound-event callbacks and Entity.load. Written, not run. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="blacklight")
public final class VehicleDestructionLifecycleGameTest {
    private static final class Probe {
        final MilitaryVehicle actor;int sounds;Runnable callback;
        Probe(MilitaryVehicle actor){this.actor=actor;}
    }
    private static final Map<MilitaryVehicle,Probe> PROBES=new IdentityHashMap<>();
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void sound(net.minecraftforge.event.PlayLevelSoundEvent.AtPosition event){
        if(event.getLevel().isClientSide||event.getSound()==null||!event.getSound().value().getLocation().toString().equals("minecraft:block.anvil.break"))return;
        for(var probe:List.copyOf(PROBES.values()))if(probe.actor.level()==event.getLevel()
                &&probe.actor.position().add(0,1,0).distanceToSqr(event.getPosition())<.0001){
            probe.sounds++;var callback=probe.callback;probe.callback=null;if(callback!=null)callback.run();
        }
    }
    private static CompoundTag save(MilitaryVehicle actor){return actor.saveWithoutId(new CompoundTag());}
    private static MilitaryVehicle create(MilitaryVehicleGameTest.Fixture f,MilitaryFaction faction,String chassis){
        var actor=VehicleRegistry.type(faction,chassis).create(f.level);actor.setPos(f.x+.5,81,.5);
        f.level.addFreshEntity(actor);f.entities.add(actor);return actor;
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_destruction",timeoutTicks=250)
    public static void allSixCommittedWrecksRetireAfterReloadWithoutReplayingEffects(GameTestHelper h){
        var fixture=new MilitaryVehicleGameTest.Fixture(h,386000);
        VehicleTestReadiness.run(h,fixture,()->{
            try{
                for(var faction:MilitaryFaction.values())for(String chassis:new String[]{"truck","apc","tank"}){
                    var actor=create(fixture,faction,chassis);var probe=new Probe(actor);PROBES.put(actor,probe);
                    CompoundTag[] committed={null};probe.callback=()->committed[0]=save(actor);
                    h.assertTrue(actor.hurt(fixture.level.damageSources().generic(),actor.maxHealth()+1),"real lethal hull damage accepted");
                    h.assertTrue(actor.isRemoved()&&probe.sounds==1&&committed[0]!=null,"one destruction sound and a callback snapshot for "+faction+" "+chassis);
                    h.assertTrue(committed[0].getBoolean("VehicleDestructionCommitted")&&committed[0].getFloat("VehicleHealth")==0,"commit is durable before external effects");
                    h.assertTrue(!actor.hurt(fixture.level.damageSources().generic(),1)&&probe.sounds==1,"repeat damage cannot replay effects");
                    PROBES.remove(actor);
                    var restored=VehicleRegistry.type(faction,chassis).create(fixture.level);restored.load(committed[0]);fixture.entities.add(restored);
                    var resumed=new Probe(restored);PROBES.put(restored,resumed);restored.tick();
                    h.assertTrue(restored.isRemoved()&&resumed.sounds==0,"saved committed shell is silently retired, never a second wreck");
                    PROBES.remove(restored);
                }
            }finally{PROBES.clear();}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_destruction",timeoutTicks=250)
    public static void healthySameObjectLoadInsideDestructionSoundCancelsOldRemoval(GameTestHelper h){
        var fixture=new MilitaryVehicleGameTest.Fixture(h,387000);
        VehicleTestReadiness.run(h,fixture,()->{
            var actor=fixture.truck();var healthy=save(actor);var probe=new Probe(actor);PROBES.put(actor,probe);
            try{
                probe.callback=()->actor.load(healthy);
                actor.hurt(fixture.level.damageSources().generic(),actor.maxHealth()+1);
                h.assertTrue(probe.sounds==1&&!actor.isRemoved()&&actor.health()==actor.maxHealth(),"old destruction cannot discard a callback-restored healthy vehicle");
                actor.tick();h.assertTrue(!actor.isRemoved()&&!save(actor).getBoolean("VehicleDestructionCommitted")&&probe.sounds==1,"replacement keeps its healthy lifecycle on the following tick");
            }finally{PROBES.remove(actor);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_destruction",timeoutTicks=250)
    public static void legacyZeroHealthWithoutReceiptFinishesOnceAndWritesTheNewReceipt(GameTestHelper h){
        var fixture=new MilitaryVehicleGameTest.Fixture(h,388000);
        VehicleTestReadiness.run(h,fixture,()->{
            var actor=fixture.truck();var legacy=save(actor);legacy.remove("VehicleDestructionCommitted");legacy.putFloat("VehicleHealth",0);actor.load(legacy);
            var probe=new Probe(actor);PROBES.put(actor,probe);CompoundTag[] snapshot={null};probe.callback=()->snapshot[0]=save(actor);
            try{
                actor.tick();h.assertTrue(actor.isRemoved()&&probe.sounds==1&&snapshot[0]!=null&&snapshot[0].getBoolean("VehicleDestructionCommitted"),"old unmarked dead hull retains one migration completion and now records it");
            }finally{PROBES.remove(actor);}
        });
    }
}
