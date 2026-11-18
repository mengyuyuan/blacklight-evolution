package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.registry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterVehicleCombatGameTest {
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final int x;final List<Entity> actors=new ArrayList<>();final Map<Long,Boolean> forced=new LinkedHashMap<>();
        boolean initialized,done,projectileSeen,passengerHurt,cycleRestored;long start;BlackwatchHelicopter attacker,airTarget,friend;MilitaryVehicle tank;ServerPlayer occupant;float health;
        Boolean selectedRocket;boolean sortieStopped;int previousGunShots,missileLaunchAge=-1;final List<Integer> gunShotAges=new ArrayList<>();String previousMissileState="";
        Fixture(GameTestHelper h,int index){this.h=h;level=h.getLevel();x=448000+index*256;
            for(int cx=(x>>4)-3;cx<=(x>>4)+3;cx++)for(int cz=-3;cz<=3;cz++){long id=ChunkPos.asLong(cx,cz);forced.put(id,level.getForcedChunks().contains(id));level.setChunkForced(cx,cz,true);}
            for(int cx=(x>>4)-4;cx<=(x>>4)+4;cx++)for(int cz=-4;cz<=4;cz++)level.getChunk(cx,cz);
            for(int dx=-40;dx<=40;dx++)for(int z=-40;z<=40;z++)level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
        }
        boolean ready(){if(initialized)return true;if(!level.isPositionEntityTicking(new BlockPos(x,100,0))||!level.isPositionEntityTicking(new BlockPos(x+48,100,0))){java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);return false;}initialized=true;start=level.getGameTime();return true;}
        int age(){return (int)(level.getGameTime()-start);}
        <T extends Entity>T add(T actor,Vec3 at){actor.setPos(at);level.addFreshEntity(actor);actors.add(actor);return actor;}
        ServerPlayer player(boolean infected){
            var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"air-hull-qa");var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
            var p=new ServerPlayer(level.getServer(),level,profile,cookie.clientInformation());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);level.getServer().getPlayerList().placeNewPlayer(connection,p,cookie);
            actors.add(p);p.setGameMode(GameType.SURVIVAL);p.setPos(x+10,81,0);if(infected)infect(p);return p;
        }
        void infect(ServerPlayer p){var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);p.setData(ModAttachments.PLAYER_STATE,state);}
        MilitarySoldier soldier(Vec3 at){var s=add(ModEntities.BLACKWATCH_SOLDIER.get().create(level),at);s.setPersistenceRequired();s.setAutomaticUnitJoin(false);return s;}
        void target(boolean air){target(air,true);}
        void target(boolean air,boolean infected){occupant=player(infected);
            if(air){airTarget=add(ModEntities.BLACKWATCH_HELICOPTER.get().create(level),new Vec3(x+10,90,0));h.assertTrue(occupant.startRiding(airTarget,true),"actual helicopter occupant");airTarget.positionRider(occupant);health=airTarget.health();}
            else{tank=add(ModEntities.BLACKWATCH_TANK.get().create(level),new Vec3(x+10,81,0));h.assertTrue(occupant.startRiding(tank,true),"actual tank occupant");health=tank.health();}
        }
        void sortie(boolean rocket){sortie(rocket,true);}
        void sortie(boolean rocket,boolean designate){selectedRocket=rocket;attacker=add(ModEntities.BLACKWATCH_HELICOPTER.get().create(level),new Vec3(x-18,104,0));
            for(int i=0;i<2;i++){var pilot=soldier(attacker.position());h.assertTrue(pilot.startRiding(attacker,true),"actual AI pilot");}
            var caller=soldier(new Vec3(x-15,81,-15));if(designate)caller.setTarget(occupant);
            h.assertTrue(attacker.deployTo(new Vec3(x-14,81,0)),"supported escort sortie");attacker.combat().supportCall(caller);caller.discard();
            var tag=new CompoundTag();attacker.saveWithoutId(tag);tag.putInt("FlightPhase",BlackwatchHelicopter.Phase.PURSUIT.ordinal());attacker.load(tag);
        }
        // Fixture isolates one real weapon by keeping the other on cooldown. Empty
        // player stores no longer disable an original infinite-ammo AI weapon.
        void isolateWeapon(){if(attacker==null||selectedRocket==null||done)return;
            var tag=new CompoundTag();attacker.saveWithoutId(tag);tag.getCompound("AirCombat").putInt(selectedRocket?"GunWait":"RocketWait",selectedRocket?30:60);attacker.load(tag);
        }
        float hullHealth(){return tank==null?airTarget.health():tank.health();}
        void observe(){projectileSeen|=!level.getEntitiesOfClass(VehicleOrdnanceProjectile.class,new AABB(x-80,65,-80,x+80,135,80)).isEmpty();passengerHurt|=occupant.getLastDamageSource()!=null;}
        void close(){done=true;for(var actor:actors)if(actor instanceof ServerPlayer p)level.getServer().getPlayerList().remove(p);else actor.discard();for(var actor:level.getEntitiesOfClass(Entity.class,new AABB(x-100,60,-100,x+100,150,100),e->!(e instanceof net.minecraft.world.entity.player.Player)))actor.discard();forced.forEach((id,before)->{var c=new ChunkPos(id);level.setChunkForced(c.x,c.z,before);});}
        void pass(){close();h.succeed();}
    }
    private static void run(GameTestHelper h,int index,Consumer<Fixture> body){var f=new Fixture(h,index);h.onEachTick(()->{if(f.done)return;try{if(f.ready()){body.accept(f);f.isolateWeapon();}}catch(RuntimeException ex){f.close();throw ex;}});}
    private static void attack(GameTestHelper h,int index,boolean air,boolean rocket){attack(h,index,air,rocket,true);}
    private static void attack(GameTestHelper h,int index,boolean air,boolean rocket,boolean infected){run(h,index,f->{
        if(f.attacker==null){f.target(air,infected);f.sortie(rocket);
            
        }f.observe();
        if(!infected&&rocket&&f.projectileSeen&&!f.sortieStopped){f.attacker.combat().finish();f.sortieStopped=true;}
        if(f.hullHealth()<f.health){
            double expected=rocket?(air?20:10):(air?4:.4);h.assertTrue(Math.abs(f.health-f.hullHealth()-expected)<.01,"one direct hit applies weapon damage/armor exactly once, expected="+expected+" actual="+(f.health-f.hullHealth()));
            h.assertTrue(!f.passengerHurt,"hull absorbs direct and explosive contact without hurting seated occupant");
            h.assertTrue(rocket?f.projectileSeen&&f.attacker.combat().rocketShots()==1&&f.attacker.combat().gunShots()==0:f.attacker.combat().gunShots()==1&&f.attacker.combat().rocketShots()==0,"actual selected armament fired once");
            h.assertTrue(f.attacker.combat().rounds()==240&&f.attacker.combat().rockets()==8,"AI shots preserve finite player stores");
            if(!infected){h.assertTrue(!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(f.occupant),"radio target remains healthy");h.assertTrue(f.attacker.combat().operator().getTarget()==null,"designation is not copied into a soldier's independent target selector");}
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("{} target={} weapon={} hullLoss={} rounds={} rockets={} projectile={} occupantHurt={}",infected?"HELI_HULL_VERIFIED":"HELI_DESIGNATED_HULL_VERIFIED",air?"helicopter":"tank",rocket?"rocket":"gun",f.health-f.hullHealth(),f.attacker.combat().rounds(),f.attacker.combat().rockets(),f.projectileSeen,f.passengerHurt);f.pass();
        }else if(f.age()>650){h.assertTrue(false,"no actual hull damage: phase="+f.attacker.phase()+" ammo="+f.attacker.combat().rounds()+"/"+f.attacker.combat().rockets()+" target="+f.attacker.combat().targetId()+" aircraft="+f.attacker.position());}
    });}
    @GameTest(template="helicopter_arena",batch="airHullTankGun",timeoutTicks=900) public static void aiGunDamagesActualArmoredHullWithoutBypassingIt(GameTestHelper h){attack(h,0,false,false);}
    @GameTest(template="helicopter_arena",batch="airHullDiagonalSweep",timeoutTicks=450)
    public static void fourRealGuidedMissilesCannotTunnelThroughTheAirframeFromDiagonalApproaches(GameTestHelper h){
        int[] completed={0};VehicleOrdnanceProjectile[] shot={null};run(h,16,f->{
            if(f.attacker==null){f.target(true);f.sortie(true);}
            // Keep the real flight/crew alive through all four impacts while this
            // test launches its own projectiles. Gun isolation is applied by run().
            var cooldown=new CompoundTag();f.attacker.saveWithoutId(cooldown);cooldown.getCompound("AirCombat").putInt("RocketWait",60);f.attacker.load(cooldown);
            f.observe();if(f.hullHealth()<f.health-completed[0]*20-.01){completed[0]++;h.assertTrue(Math.abs(f.health-f.hullHealth()-completed[0]*20)<.01&&!f.passengerHurt,"one real body impact per diagonal with seated occupant protected");h.assertTrue(shot[0]!=null&&shot[0].isRemoved(),"actual missile removed by native impact");shot[0]=null;}
            if(completed[0]==4){com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_AIR_SWEEP_VERIFIED diagonals={} hullLoss={} occupantHurt={}",completed[0],f.health-f.hullHealth(),f.passengerHurt);f.pass();return;}
            h.assertTrue(f.age()<300,"diagonal sweep failed before fixture timeout: completed="+completed[0]+" loss="+(f.health-f.hullHealth())+" missile="+(shot[0]==null?"none":shot[0].position()+" removed="+shot[0].isRemoved()+" age="+shot[0].flightAge())+" operator="+f.attacker.combat().operator()+" phase="+f.attacker.phase());
            if(shot[0]!=null&&shot[0].isRemoved())h.assertTrue(false,"missile removed without hull damage: completed="+completed[0]+" age="+shot[0].flightAge()+" at="+shot[0].position()+" operator="+f.attacker.combat().operator()+" phase="+f.attacker.phase());
            if(shot[0]==null){double angle=Math.toRadians(45+90*completed[0]);Vec3 center=f.airTarget.getBoundingBox().getCenter(),at=center.add(24*Math.cos(angle),12,24*Math.sin(angle));
                var missile=new VehicleOrdnanceProjectile(f.level,f.attacker.combat().operator(),f.attacker,"helicopter_hellfire");missile.setPos(at);missile.launchHellfire(center.subtract(at).normalize(),Vec3.ZERO,f.occupant,center,false);h.assertTrue(f.level.addFreshEntity(missile),"real diagonal missile created");f.actors.add(missile);shot[0]=missile;
            }
        });
    }
    @GameTest(template="helicopter_arena",batch="airHullTankRocket",timeoutTicks=900) public static void aiRocketHitsActualTankAndRespectsItsArmor(GameTestHelper h){attack(h,1,false,true);}
    @GameTest(template="helicopter_arena",batch="airHullHeliGun",timeoutTicks=900) public static void aiGunHitsEnemyAircraftBodyInsteadOfItsPassenger(GameTestHelper h){attack(h,2,true,false);}
    @GameTest(template="helicopter_arena",batch="airHullHeliRocket",timeoutTicks=900) public static void aiRocketDamagesEnemyAircraftWithoutSplashingItsSeatedPilot(GameTestHelper h){attack(h,3,true,true);}
    @GameTest(template="helicopter_arena",batch="airHullDesignatedTankGun",timeoutTicks=900) public static void radioDesignatedHealthyTankCrewAllowsGunDamage(GameTestHelper h){attack(h,7,false,false,false);}
    @GameTest(template="helicopter_arena",batch="airHullDesignatedTankRocket",timeoutTicks=900) public static void finalRocketDamagesRadioDesignatedHealthyTankCrewHull(GameTestHelper h){attack(h,8,false,true,false);}
    @GameTest(template="helicopter_arena",batch="airHullDesignatedHeliGun",timeoutTicks=900) public static void radioDesignatedHealthyAircraftCrewAllowsGunDamage(GameTestHelper h){attack(h,9,true,false,false);}
    @GameTest(template="helicopter_arena",batch="airHullDesignatedHeliRocket",timeoutTicks=900) public static void finalRocketDamagesRadioDesignatedHealthyAircraftHull(GameTestHelper h){attack(h,10,true,true,false);}
    private static void neutral(GameTestHelper h,int index,boolean air){run(h,index,f->{
        if(f.attacker==null){f.target(air,false);f.sortie(false,false);}
        if(f.age()==180){h.assertTrue(f.attacker.combat().targetId()==null&&f.attacker.combat().shots()==0&&f.attacker.combat().shots()==0&&f.hullHealth()==f.health,"healthy unreported occupants do not become proactive enemies");f.pass();}
    });}
    @GameTest(template="helicopter_arena",batch="airHullNeutralTank",timeoutTicks=500) public static void healthyUnreportedTankIsNotAcquired(GameTestHelper h){neutral(h,11,false);}
    @GameTest(template="helicopter_arena",batch="airHullNeutralHeli",timeoutTicks=500) public static void healthyUnreportedAircraftIsNotAcquired(GameTestHelper h){neutral(h,12,true);}
    private static boolean damage(Fixture f,MilitarySoldier actor){return f.tank==null?f.airTarget.hurt(actor.damageSources().mobAttack(actor),4):com.codex.blacklight.military.vehicle.VehicleDamageBridge.hurtPhysical(actor,f.tank,4,null);}
    private static void designationBoundary(GameTestHelper h,int index,boolean air){run(h,index,f->{
        f.target(air,false);f.sortie(false);var operator=f.attacker.combat().operator();
        var other=(MilitarySoldier)f.attacker.getPassengers().stream().filter(p->p!=operator).findFirst().orElseThrow();
        var caller=f.soldier(new Vec3(f.x-15,81,-15));
        h.assertTrue(!damage(f,other)&&!damage(f,caller),"another crew member or unrelated soldier cannot borrow an aircraft designation");
        h.assertTrue(damage(f,operator),"actual designated weapon operator can damage the enemy hull");
        f.attacker.combat().supportCall(caller);h.assertTrue(!damage(f,operator),"a new call without an opponent revokes previous designation at impact");
        caller.setTarget(f.occupant);f.attacker.combat().supportCall(caller);caller.setTarget(null);
        f.occupant.setGameMode(GameType.CREATIVE);h.assertTrue(!damage(f,operator),"designation cannot bypass creative protection");f.occupant.setGameMode(GameType.SURVIVAL);
        var ally=f.soldier(f.occupant.position());h.assertTrue(ally.startRiding(air?f.airTarget:f.tank,true),"actual friendly copassenger");
        h.assertTrue(!damage(f,operator),"designation cannot bypass allied crew protection");ally.stopRiding();
        operator.stopRiding();h.assertTrue(!damage(f,operator),"a former pilot cannot retain the aircraft's target authority after dismounting");f.pass();
    });}
    @GameTest(template="helicopter_arena",batch="airHullDesignationTankBoundary",timeoutTicks=300) public static void tankDesignationBelongsToActualOperatorAndCanBeRevoked(GameTestHelper h){designationBoundary(h,13,false);}
    @GameTest(template="helicopter_arena",batch="airHullDesignationHeliBoundary",timeoutTicks=300) public static void aircraftDesignationBelongsToActualOperatorAndCanBeRevoked(GameTestHelper h){designationBoundary(h,14,true);}
    @GameTest(template="helicopter_arena",batch="airSourceGunCycle",timeoutTicks=700)
    public static void actualGunFireUsesSourceBurstRestAndPreservesRestOnReload(GameTestHelper h){run(h,15,f->{
        if(f.attacker==null){f.target(false);f.sortie(false);}
        int rounds=f.attacker.combat().rounds(),gunShots=f.attacker.combat().gunShots();
        if(gunShots>f.previousGunShots){h.assertTrue(gunShots==f.previousGunShots+1,"one native round per firing tick");f.gunShotAges.add(f.age());}
        f.previousGunShots=gunShots;
        if(!f.gunShotAges.isEmpty()){
            int first=f.gunShotAges.get(0),elapsed=f.age()-first;
            h.assertTrue(f.gunShotAges.stream().noneMatch(t->t-first>=80&&t-first<160),"no accepted gunshot during the source four-second rest");
            if(elapsed==100){var tag=new CompoundTag();f.attacker.saveWithoutId(tag);h.assertTrue(tag.getCompound("AirCombat").getInt("GunRestTicks")==60,"rest counter includes elapsed native ticks");f.attacker.load(tag);f.cycleRestored=true;}
            long before=f.gunShotAges.stream().filter(t->t-first<80).count(),after=f.gunShotAges.stream().filter(t->t-first>=160).count();
            if(before>=8&&after>=2){h.assertTrue(f.cycleRestored&&f.hullHealth()<f.health,"actual hull was hit across persisted burst/rest cycle");
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_GUN_CYCLE_VERIFIED first={} initialShots={} resumeAfter={} restored={} rounds={}",first,before,f.gunShotAges.stream().filter(t->t-first>=160).findFirst().orElseThrow()-first,f.cycleRestored,rounds);f.pass();}
        }
        if(!f.done&&f.age()>500)h.assertTrue(false,"native source gun cycle failed to fire/resume: shots="+f.gunShotAges+" phase="+f.attacker.phase());
    });}
    @GameTest(template="helicopter_arena",batch="airHullMixed",timeoutTicks=500)
    public static void alliedCrewSharingTheEnemyTankPreventsFire(GameTestHelper h){run(h,4,f->{
        if(f.attacker==null){f.target(false);var ally=f.soldier(f.tank.position());ally.setNoAi(true);h.assertTrue(ally.startRiding(f.tank,true),"real allied copassenger");f.sortie(false);}
        if(f.age()==220){h.assertTrue(f.attacker.phase()==BlackwatchHelicopter.Phase.STRAFE&&f.attacker.combat().targetId()!=null,"actual firing pass acquired");h.assertTrue(f.attacker.combat().shots()==0&&f.hullHealth()==f.health,"mixed friendly crew blocks gunfire without spending ammunition");f.pass();}
    });}
    @GameTest(template="helicopter_arena",batch="airHullSplash",timeoutTicks=1050)
    public static void nearbyFriendlyAircraftSuppressesRocketUntilItLeaves(GameTestHelper h){run(h,5,f->{
        if(f.attacker==null){f.target(false);f.friend=f.add(ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level),f.tank.position().add(0,0,5));f.sortie(true);}
        String state=f.attacker.combat().tactic()+"/"+f.attacker.combat().maneuverStage()+"/"+f.attacker.combat().hellfireAttack().stage()+"/"+f.attacker.combat().rocketShots();
        if(!state.equals(f.previousMissileState)||f.age()%100==0||f.age()==430||f.age()==650){f.previousMissileState=state;var trace=new CompoundTag();f.attacker.combat().save(trace);
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_FRIEND_TIMELINE age={} tick={} state={} pos={} target={} friendRemoved={} data={}",f.age(),f.attacker.tickCount,state,f.attacker.position(),f.tank.position(),f.friend.isRemoved(),trace);}
        if(f.age()==430){h.assertTrue(f.attacker.phase()==BlackwatchHelicopter.Phase.STRAFE&&!f.attacker.combat().hellfireAttack().hovering(),"friendly hull prevents an unusable missile hover");h.assertTrue(f.attacker.combat().rocketShots()==0&&f.hullHealth()==f.health,"nearby friendly hull prevents splash and ammo debit");f.friend.discard();}
        f.observe();if(f.attacker.combat().rocketShots()>0&&f.missileLaunchAge<0)f.missileLaunchAge=f.age();
        // Source circle/simple may finish its 200-tick opportunity, then the
        // hover is bounded by 100 ticks. Launch and native guided-flight damage
        // are different observations: the missile has 200 ticks of lifetime.
        if(f.age()>430&&f.hullHealth()<f.health){h.assertTrue(f.missileLaunchAge>=430&&f.projectileSeen,"same native sortie launches a real missile after friendly departure and damages the hull");
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_FRIEND_RECOVERY_VERIFIED departure=430 launch={} impact={} flightTicks={} hullLoss={}",f.missileLaunchAge,f.age(),f.age()-f.missileLaunchAge,f.health-f.hullHealth());f.pass();}
        else if(f.missileLaunchAge<0&&f.age()>730)h.assertTrue(false,"missile did not launch within one source maneuver plus hover after friendly departure: "+f.previousMissileState);
        else if(f.missileLaunchAge>=0&&f.age()-f.missileLaunchAge>com.codex.blacklight.military.aircraft.HelicopterMissileFlight.LIFETIME_TICKS+com.codex.blacklight.military.aircraft.HelicopterBlastWave.DAMAGE_END+2)h.assertTrue(false,"launched missile did not actually damage the hull within native flight lifetime: launch="+f.missileLaunchAge+" now="+f.age());
    });}
    @GameTest(template="helicopter_arena",batch="airHullProtection",timeoutTicks=300)
    public static void aircraftDamageChecksActualCrewInsteadOfOnlySavedOwnership(GameTestHelper h){run(h,6,f->{
        if(f.occupant==null){f.occupant=f.player(false);f.airTarget=f.add(ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level),new Vec3(f.x+10,81,0));h.assertTrue(f.occupant.startRiding(f.airTarget,true),"actual owned aircraft");}
        if(f.age()==20){var actor=f.soldier(new Vec3(f.x-10,81,0));var damage=actor.damageSources().mobAttack(actor);float health=f.airTarget.health();
            h.assertTrue(f.airTarget.pilot().owned()&&!f.airTarget.hurt(damage,4),"healthy occupant is not an enemy merely because the helicopter is owned");
            f.infect(f.occupant);h.assertTrue(f.airTarget.hurt(damage,4)&&f.airTarget.health()==health-4,"actual infected enemy permits hull damage");
            f.occupant.setGameMode(GameType.CREATIVE);h.assertTrue(!f.airTarget.hurt(damage,4),"creative occupant protection retained");f.occupant.setGameMode(GameType.SURVIVAL);
            var ally=f.soldier(f.airTarget.position());h.assertTrue(ally.startRiding(f.airTarget,true),"real allied copilot");h.assertTrue(!f.airTarget.hurt(damage,4),"mixed allied crew protects the hull at the damage boundary");ally.stopRiding();f.occupant.stopRiding();
            h.assertTrue(f.airTarget.pilot().owned()&&!f.airTarget.hurt(damage,4),"old owner UUID does not make an empty aircraft a proactive target");f.pass();
        }
    });}
}
