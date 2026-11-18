package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterGameTest {
    private static void arena(GameTestHelper h){
        // Batches reuse the same disposable space; remove aircraft that already departed a previous template.
        Vec3 center=h.absoluteVec(new Vec3(24,12,16));
        for(var entity:h.getLevel().getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(center,center).inflate(128),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
        for(int x=0;x<48;x++)for(int z=0;z<32;z++)h.setBlock(x,1,z,Blocks.SMOOTH_STONE);
    }
    private static BlackwatchHelicopter craft(GameTestHelper h,Vec3 at){
        var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());craft.setPos(h.absoluteVec(at));h.getLevel().addFreshEntity(craft);return craft;
    }
    private static List<BlackwatchSoldier> crew(GameTestHelper h,BlackwatchHelicopter craft){
        var list=new ArrayList<BlackwatchSoldier>();
        for(int i=0;i<6;i++){
            var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());soldier.setPos(craft.position());soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();
            soldier.initializeNativeLoadout("helicopter-test:"+i);soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);
            h.getLevel().addFreshEntity(soldier);h.assertTrue(soldier.startRiding(craft,true),"native passenger mounted");list.add(soldier);
        }
        return list;
    }
    @GameTest(template="helicopter_arena",batch="airDelivery",timeoutTicks=1400)
    public static void realCrewArriveLandAndUnloadWithoutDuplication(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,18,16));var crew=crew(h,craft);Vec3 landing=h.absoluteVec(new Vec3(35,2,16));
        crew.subList(2,6).forEach(s->s.setNoAi(true)); // A stationary delivered troop must occupy its exit, not be overlapped by later passengers.
        h.assertTrue(craft.deployTo(landing),"supported dry landing accepted");var phases=EnumSet.noneOf(BlackwatchHelicopter.Phase.class);boolean[] deliveryVerified={false};
        h.onEachTick(()->{
            // The next batch may clear these shared fixtures immediately after success (Forge).
            if(deliveryVerified[0])return;
            phases.add(craft.phase());
            for(int i=2;i<6;i++)if(!crew.get(i).isPassenger()){
                h.assertTrue(crew.get(i).isAlive()&&crew.get(i).getY()>=landing.y-.1,"crew are not killed or placed underground");
                h.assertTrue(crew.get(i).getY()<landing.y+1,"no aerial dismount masquerading as a landing");
            }
        });
        h.succeedWhen(()->{
            h.assertTrue(phases.containsAll(List.of(BlackwatchHelicopter.Phase.APPROACH,BlackwatchHelicopter.Phase.DESCEND,BlackwatchHelicopter.Phase.UNLOAD)),"actual flight phases observed");
            h.assertTrue(crew.subList(2,6).stream().allMatch(s->!s.isPassenger()&&s.isAlive()),"the four original troop entities survived delivery");
            for(int i=2;i<6;i++)for(int j=i+1;j<6;j++)h.assertTrue(!crew.get(i).getBoundingBox().intersects(crew.get(j).getBoundingBox()),"stationary dismounted troops have distinct unobstructed exits");
            h.assertTrue(crew.get(0).getVehicle()==craft&&crew.get(1).getVehicle()==craft,"pilots remain aboard");
            h.assertTrue(crew.stream().map(Entity::getUUID).distinct().count()==6,"unique physical occupants");
            h.assertTrue(crew.subList(2,6).stream().allMatch(MilitarySoldier::hasBattleAmmunition),"finite issued weapons reach the landing zone");
            deliveryVerified[0]=true;
        });
    }
    @GameTest(template="helicopter_arena",batch="airSite",timeoutTicks=100)
    public static void rotorClearanceUnsupportedWaterAndOccupiedSitesAreRejected(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,16,16));Vec3 pad=h.absoluteVec(new Vec3(34,2,16));
        h.assertTrue(craft.safeLanding(pad),"empty supported pad accepted");
        h.setBlock(34,1,16,Blocks.AIR);h.assertTrue(!craft.safeLanding(pad),"missing center support rejected");
        h.setBlock(34,1,16,Blocks.WATER);h.assertTrue(!craft.safeLanding(pad),"water rejected");h.setBlock(34,1,16,Blocks.STONE);
        h.setBlock(39,6,16,Blocks.STONE);h.assertTrue(!craft.safeLanding(pad),"outer rotor blade clearance participates");h.setBlock(39,6,16,Blocks.AIR);
        // A fresh combined Forge suite can place this arena outside entity-ticking chunks.
        // A constructed but hidden entity is not an occupied landing pad; make the native fixture queryable first.
        var chunk=new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(pad));
        boolean forced=h.getLevel().getForcedChunks().contains(chunk.toLong());h.getLevel().setChunkForced(chunk.x,chunk.z,true);
        var guard=ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());guard.setNoAi(true);guard.setPersistenceRequired();guard.setAutomaticUnitJoin(false);guard.setPos(pad);
        h.assertTrue(h.getLevel().addFreshEntity(guard),"native fixture guard is accepted by the server");
        // Chunk visibility updates asynchronously in a fresh Forge world; wait for the explicit native precondition.
        h.succeedWhen(()->{
            boolean lookup=h.getLevel().getEntity(guard.getUUID())==guard;
            boolean query=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,guard.getBoundingBox().inflate(1)).contains(guard);
            h.assertTrue(lookup&&guard.isAlive()&&query,"fixture guard query: lookup="+lookup+" query="+query+" alive="+guard.isAlive()+" removal="+guard.getRemovalReason()+" position="+guard.position());
            h.assertTrue(!craft.safeLanding(pad),"landing on a living entity rejected");
            guard.discard();h.getLevel().setChunkForced(chunk.x,chunk.z,forced);
        });

    }
    @GameTest(template="helicopter_arena",batch="airTakeoff",timeoutTicks=300)
    public static void GroundTakeoffSpinsRotorsBeforeLiftingTheRealCrew(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,2,16));crew(h,craft);double ground=craft.getY();
        h.assertTrue(craft.deployTo(h.absoluteVec(new Vec3(35,2,16))),"ground mission accepted");
        h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.SPIN_UP&&craft.rotor()==0,"ground start begins with stopped rotors");
        h.runAfterDelay(40,()->h.assertTrue(Math.abs(craft.getY()-ground)<.01&&craft.rotor()<1,"no lift before spool-up finishes"));
        h.succeedWhen(()->h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.CLIMB&&craft.getY()>ground+2&&craft.getPassengers().size()==6,"full rotor lift carries the real crew off the pad"));
    }
    @GameTest(template="helicopter_arena",batch="airRequest",timeoutTicks=20)
    public static void officerSupportRequestCreatesOneCrewedRappelTransport(GameTestHelper h){
        arena(h);var officer=ModEntities.BLACKWATCH_OFFICER.get().create(h.getLevel());officer.setPos(h.absoluteVec(new Vec3(24,2,16)));officer.setAutomaticUnitJoin(false);h.getLevel().addFreshEntity(officer);
        boolean accepted=com.codex.blacklight.military.aircraft.HelicopterSupport.request(officer);
        h.assertTrue(accepted,"actual dispatcher failed: "+officer.getPersistentData().getString("AirSupportLastResult"));
        h.runAfterDelay(2,()->{
        var aircraft=h.getLevel().getEntitiesOfClass(BlackwatchHelicopter.class,officer.getBoundingBox().inflate(96));
        h.assertTrue(aircraft.size()==1&&aircraft.get(0).getPassengers().size()==2,"one aircraft starts with two real flight crew and a budgeted source deployment: "+aircraft.size()+" / "+aircraft.stream().map(c->c.getPassengers().size()).toList());
        h.assertTrue(aircraft.get(0).deployment().enabled()&&aircraft.get(0).getPassengers().stream().noneMatch(p->p.getPersistentData().getBoolean("HelicopterTroop")),"source six-person manifest is scheduled at the hover site, not stuffed into four cabin seats");
        h.assertTrue(!com.codex.blacklight.military.aircraft.HelicopterSupport.request(officer),"same radio caller cannot duplicate the dispatch");
        for(var passenger:List.copyOf(aircraft.get(0).getPassengers()))passenger.discard();aircraft.get(0).discard();h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airPilots",timeoutTicks=40)
    public static void LostPilotsStopAutonomousFlightInsteadOfFlyingAnEmptyCockpit(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,18,16));var crew=crew(h,craft);h.assertTrue(craft.deployTo(h.absoluteVec(new Vec3(34,2,16))),"mission staged");
        crew.get(0).discard();crew.get(1).discard();
        h.succeedWhen(()->h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.CRASH,"both missing pilots disable flight"));
    }
    @GameTest(template="helicopter_arena",batch="airWeapons",timeoutTicks=70)
    public static void firearmBulletsAndRealRocketsDamageTheMechanicalHull(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(25,8,16));
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.fromString("f063cae0-1fe0-46e6-a291-b2c97111a8f8"),"HelicopterWeaponQA"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(9,8,16)));
        com.codex.blacklight.firearms.FirearmsCombat.fire(player,com.codex.blacklight.firearms.WeaponCatalog.RIFLE,new Vec3(1,0,0),1);
        float afterBullet=craft.health();h.assertTrue(afterBullet<360,"real ordinary hitscan includes aircraft");
        var rocket=new com.codex.blacklight.firearms.OrdnanceProjectile(h.getLevel(),player,com.codex.blacklight.firearms.WeaponDefinition.Kind.ROCKET);
        rocket.setPos(craft.position().add(-6,1.2,0));rocket.setDeltaMovement(1,0,0);h.getLevel().addFreshEntity(rocket);
        h.succeedWhen(()->h.assertTrue(craft.health()<afterBullet,"real rocket impact damages the hull"));
    }
    @GameTest(template="helicopter_arena",batch="airBlocked",timeoutTicks=1050)
    public static void NewlyBlockedLandingAbortsWithTroopsStillAboard(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,18,16));var crew=crew(h,craft);Vec3 pad=h.absoluteVec(new Vec3(34,2,16));
        h.assertTrue(craft.deployTo(pad),"clear initial pad");
        h.runAfterDelay(90,()->h.setBlock(34,2,16,Blocks.STONE));
        h.succeedWhen(()->{
            h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.DEPART||craft.phase()==BlackwatchHelicopter.Phase.RETURN,"unsafe landing aborted: "+craft.phase()+" / "+craft.missionAge()+" / crew="+craft.getPassengers().size()+" / health="+craft.health()+" / position="+craft.position());
            h.assertTrue(crew.stream().allMatch(s->s.getVehicle()==craft),"aborted transport cannot dump soldiers in midair");
        });
    }
    @GameTest(template="helicopter_arena",batch="airPersistence",timeoutTicks=20)
    public static void MissionAndDamagePersistWithoutBiologicalIdentity(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(10,18,16));h.assertTrue(craft.deployTo(h.absoluteVec(new Vec3(34,2,16))),"mission created");
        h.assertTrue(craft.hurt(craft.damageSources().generic(),37),"mechanical damage accepted");
        var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.health()==323&&restored.phase()==craft.phase()&&restored.landing().equals(craft.landing()),"saved mission and durability restore");
        tag.putDouble("LandingX",Double.NaN);restored.load(tag);h.assertTrue(restored.phase()==BlackwatchHelicopter.Phase.IDLE,"bad destination cannot drive invalid movement");
        h.assertTrue(!(((Object)craft) instanceof net.minecraft.world.entity.LivingEntity),"aircraft cannot enter biological infection/consume identity");h.succeed();
    }
    @GameTest(template="helicopter_arena",batch="airCrash",timeoutTicks=180)
    public static void FatalDamageFallsToGroundBeforeDestruction(GameTestHelper h){
        arena(h);var craft=craft(h,new Vec3(22,13,16));
        h.assertTrue(craft.hurt(craft.damageSources().generic(),400),"fatal damage accepted");
        h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.CRASH&&!craft.isRemoved(),"fatal impact begins a real crash");
        h.succeedWhen(()->{h.assertTrue(craft.isRemoved(),"crash completes");h.assertTrue(craft.getY()<h.absoluteVec(new Vec3(22,4,16)).y,"wreck reached ground");});
    }
}
