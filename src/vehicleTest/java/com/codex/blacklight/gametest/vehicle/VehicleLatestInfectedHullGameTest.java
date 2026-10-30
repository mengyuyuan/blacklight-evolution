package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.Map;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.*;

/** Latest-author species/controllers against real joined chassis; no direct vehicle damage or target injection. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleLatestInfectedHullGameTest {
    static MilitaryVehicleGameTest.Fixture readyFixture(GameTestHelper h,int x){
        var f=new MilitaryVehicleGameTest.Fixture(h,x);
        // Local readiness halo: real ticking sections need their real neighbouring full chunks.
        // Preserve all previous forced-ticket ownership so close() restores this bounded fixture.
        for(int cx=((x-10)>>4)-2;cx<=((x+10)>>4)+2;cx++)for(int cz=-3;cz<=3;cz++){
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            f.forced.putIfAbsent(key,f.level.getForcedChunks().contains(key));
            f.level.setChunkForced(cx,cz,true);f.level.getChunk(cx,cz);
        }
        return f;
    }
    private static BlacklightInfectedHost host(MilitaryVehicleGameTest.Fixture f,boolean evolved){
        var source=(evolved?EntityType.ZOMBIE:EntityType.WARDEN).create(f.level);
        var host=ModEntities.INFECTED_HOST.get().create(f.level);host.inherit(source);source.discard();
        host.setPersistenceRequired();host.setNoAi(true);host.setPos(f.x+6,81,2);
        if(evolved){
            // The author's prepared mature lineage, followed by its actual checked metamorphosis.
            var genes=new TissueInheritance(Map.of(TissueCore.BLADE,new TissueInheritance.Organ(900,0,0,0,1,81)),6,2100,true,null,1,6);
            var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",TissueCore.BLADE.key());
            tag.putLong("BlacklightTissueAge",504000);tag.putInt("BlacklightTissueNutrition",100);
            tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(genes));host.load(tag);
        }
        f.level.addFreshEntity(host);f.entities.add(host);
        if(evolved){
            if(!host.beginEvolvedMetamorphosis())throw new GameTestAssertException("prepared mature lineage must qualify for actual author metamorphosis");
            for(int tick=0;tick<200;tick++)host.tick();
            if(!host.isEvolved()||host.isMetamorphosing())throw new GameTestAssertException("actual metamorphosis must finish before testing its natural combat controller");
        }
        // Bound the prepared Warden's damage, not the production balance, so the observation
        // outlives SONIC contact without legitimately destroying its 240-HP test chassis.
        if(!evolved)host.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
        host.setNoAi(false);return host;
    }
    private static String diagnostic(Mob actor,LivingEntity passenger,MilitaryVehicle vehicle){
        String action=actor instanceof BlacklightInfectedHost host?" native="+host.nativeAction()+" evolved="+host.evolvedAction()+" engaged="+host.evolvedEngaged()+" organ="+host.organActionCore():"";
        return " actor="+actor.getType()+" actorTicks="+actor.tickCount+" pos="+actor.position()+" target="+(actor.getTarget()==null?"none":actor.getTarget().getUUID())
                +" passenger="+passenger.getUUID()+" passengerHp="+passenger.getHealth()+" riding="+(passenger.getVehicle()==vehicle)+" hullHp="+vehicle.health()+action;
    }
    private static void observe(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,Mob actor,LivingEntity passenger,MilitaryVehicle vehicle,float occupantHp,float hullHp,int remaining,boolean[] acquired){
        if(actor.getTarget()==passenger)acquired[0]=true;
        if(passenger.getHealth()!=occupantHp||!passenger.isAlive()||passenger.getVehicle()!=vehicle){
            try(f){h.assertTrue(false,"natural infected combat must not bypass the occupied APC hull to injure or evict its protected original passenger;"+diagnostic(actor,passenger,vehicle));}return;
        }
        if(remaining==0){try(f){
            h.assertTrue(acquired[0],"author natural selector acquires the same lawful passenger without injected targets;"+diagnostic(actor,passenger,vehicle));
            h.assertTrue(vehicle.health()<hullHp&&vehicle.health()>0,"natural AI must damage the real mechanical hull while keeping its original occupant unharmed;"+diagnostic(actor,passenger,vehicle));
            h.assertTrue(actor.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal().getClass().getName().equals("com.codex.blacklight.military.vehicle.VehicleHullAttackGoal")),"actual EntityJoin installs the hull goal for this latest author species");
            if(actor instanceof BlacklightInfectedHost host)h.assertTrue(host.nativeAction().isEmpty()&&host.evolvedAction().isEmpty()&&host.organActionCore()==null,"mounted mechanical pursuit releases pending native/evolved/organ actions");
        }h.succeed();return;}
        h.runAfterDelay(1,()->observe(h,f,actor,passenger,vehicle,occupantHp,hullHp,remaining-1,acquired));
    }
    private static void occupiedApc(GameTestHelper h,int x,String kind){
        var f=readyFixture(h,x);
        VehicleTestReadiness.await(h,f,()->{
            var vehicle=ModEntities.MILITARY_APC.get().create(f.level);vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
            var passenger=EntityType.VILLAGER.create(f.level);passenger.setPersistenceRequired();passenger.setNoAi(true);
            passenger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);passenger.setHealth(400);
            passenger.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
            passenger.setPos(vehicle.position());f.level.addFreshEntity(passenger);f.entities.add(passenger);
            h.assertTrue(passenger.startRiding(vehicle),"actual villager physically boards the enclosed APC");
            Mob actor;
            if(kind.equals("common")){actor=ModEntities.COMMON_INFECTED.get().create(f.level);actor.setPersistenceRequired();actor.setPos(f.x+5,81,2);f.level.addFreshEntity(actor);f.entities.add(actor);}
            else actor=host(f,kind.equals("evolved"));
            // Observe well past SONIC's contact tick=30, not merely its first mechanical hit.
            observe(h,f,actor,passenger,vehicle,passenger.getHealth(),vehicle.health(),150,new boolean[1]);
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6latestinfected",timeoutTicks=260)
    public static void nativeSonicHostCannotBypassTheActualOccupiedApcHull(GameTestHelper h){occupiedApc(h,426000,"sonic");}
    @GameTest(template="blackwatch_arena",batch="vehicle6latestinfected",timeoutTicks=260)
    public static void authorEvolvedCombatYieldsToTheActualOccupiedApcHull(GameTestHelper h){occupiedApc(h,427000,"evolved");}
    @GameTest(template="blackwatch_arena",batch="vehicle6latestinfected",timeoutTicks=260)
    public static void authorCommonInfectedNaturallyAttacksTheActualOccupiedApcHull(GameTestHelper h){occupiedApc(h,428000,"common");}
    private static void boardDuringNativeWindup(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,BlacklightInfectedHost actor,LivingEntity passenger,MilitaryVehicle vehicle,int remaining){
        if(actor.nativeAction().equals("SONIC")&&actor.getTarget()==passenger){
            h.assertTrue(passenger.getVehicle()==null,"the actual native windup began against an unmounted lawful target");
            h.assertTrue(passenger.startRiding(vehicle),"the same actual original target boards its actual nearby APC during the natural native windup");
            observe(h,f,actor,passenger,vehicle,passenger.getHealth(),vehicle.health(),150,new boolean[]{true});return;
        }
        if(remaining==0){try(f){h.assertTrue(false,"author natural SONIC must begin before the ordinary boarding transition;"+diagnostic(actor,passenger,vehicle));}return;}
        h.runAfterDelay(1,()->boardDuringNativeWindup(h,f,actor,passenger,vehicle,remaining-1));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6latestboarding",timeoutTicks=430)
    public static void actualBoardingInterruptsAnAlreadyStartedNativeSonicWindup(GameTestHelper h){
        var f=readyFixture(h,430000);
        VehicleTestReadiness.await(h,f,()->{
            var vehicle=ModEntities.MILITARY_APC.get().create(f.level);vehicle.setPos(f.x+.5,81,.5);f.level.addFreshEntity(vehicle);f.entities.add(vehicle);
            var passenger=EntityType.VILLAGER.create(f.level);passenger.setPersistenceRequired();passenger.setNoAi(true);
            passenger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);passenger.setHealth(400);
            passenger.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
            passenger.setPos(f.x+.5,81,4.4);f.level.addFreshEntity(passenger);f.entities.add(passenger);
            var actor=host(f,false);actor.setPos(f.x+9,81,2);
            boardDuringNativeWindup(h,f,actor,passenger,vehicle,160);
        });
    }
    private VehicleLatestInfectedHullGameTest(){}
}
