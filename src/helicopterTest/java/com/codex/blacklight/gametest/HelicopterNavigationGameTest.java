package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.aircraft.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterNavigationGameTest {
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final int x;final List<Entity> entities=new ArrayList<>();final Map<Long,Boolean> forced=new HashMap<>();
        Fixture(GameTestHelper h,int x){this.level=h.getLevel();this.x=x;
            for(int cx=(x-64)>>4;cx<=(x+64)>>4;cx++)for(int cz=-4;cz<=4;cz++){long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=-58;dx<=58;dx++)for(int z=-58;z<=58;z++){
                level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int y=81;y<=135;y++){var pos=new BlockPos(x+dx,y,z);if(!level.isEmptyBlock(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
            }
            h.runAfterDelay(20,()->{for(var old:level.getEntitiesOfClass(Entity.class,new AABB(x-60,75,-60,x+60,140,60),e->!(e instanceof net.minecraft.world.entity.player.Player)&&!entities.contains(e)))old.discard();});
        }
        void wall(int from,int to,int top){for(int z=from;z<=to;z++)for(int y=81;y<=top;y++)for(int dx=0;dx<=1;dx++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.STONE.defaultBlockState(),2);}
        BlackwatchHelicopter craft(){
            var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(x-24,97,0);level.addFreshEntity(craft);entities.add(craft);
            for(int i=0;i<6;i++){var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);soldier.setPos(craft.position());soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();soldier.setNoAi(i>=2);soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);soldier.initializeNativeLoadout("air-route:"+i);level.addFreshEntity(soldier);entities.add(soldier);if(!soldier.startRiding(craft,true))throw new IllegalStateException("crew mount");}
            return craft;
        }
        public void close(){entities.forEach(Entity::discard);forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    private static void collisionInvariant(GameTestHelper h,BlackwatchHelicopter craft){
        h.assertTrue(craft.clearAt(craft.position(),craft.getYRot()),"real hull and rotor stay clear: phase="+craft.phase()+", position="+craft.position()+", yaw="+craft.getYRot()+", nearby="+craft.level().getEntities(craft,craft.getBoundingBox().inflate(1),e->e instanceof net.minecraft.world.entity.LivingEntity&&!craft.hasPassenger(e)).stream().map(e->e.getType()+"@"+e.position()).toList());
        h.assertTrue(HelicopterNavigation.nodesUsed(h.getLevel())<=HelicopterNavigation.NODES_PER_LEVEL,"all aircraft share the level search budget");
    }
    @GameTest(template="helicopter_arena",batch="airRouteTall",timeoutTicks=1800)
    public static void loadedAircraftRoutesAroundTowerAndDeliversItsOriginalTroops(GameTestHelper h){
        var f=new Fixture(h,271000);f.wall(-12,12,134);var craft=f.craft();var crew=List.copyOf(craft.getPassengers());double[] lateral={0};int forced=f.level.getForcedChunks().size();
        h.runAfterDelay(40,()->h.assertTrue(craft.deployTo(new Vec3(f.x+24,81,0)),"supported destination"));
        h.onEachTick(()->{if(craft.missionAge()>0){collisionInvariant(h,craft);lateral[0]=Math.max(lateral[0],Math.abs(craft.getZ()));}});
        h.succeedWhen(()->{h.assertTrue(crew.subList(2,6).stream().noneMatch(Entity::isPassenger),"actual troops reach destination");
            h.assertTrue(craft.getX()>f.x+16&&lateral[0]>19&&craft.navigation().searches()>0,"real route bends around the full rotor-width tower");
            h.assertTrue(crew.stream().allMatch(Entity::isAlive)&&craft.health()==360,"navigation preserves original craft and real crew");h.assertTrue(f.level.getForcedChunks().size()==forced,"planning never force-loads chunks");f.close();});
    }
    @GameTest(template="helicopter_arena",batch="airRouteClimb",timeoutTicks=1600)
    public static void actualAircraftClimbsOverLowWallWhenSidePassagesAreTooFar(GameTestHelper h){
        var f=new Fixture(h,272000);f.wall(-56,56,100);var craft=f.craft();double[] maximum={97};
        h.runAfterDelay(40,()->h.assertTrue(craft.deployTo(new Vec3(f.x+24,81,0)),"supported destination"));
        h.onEachTick(()->{if(craft.missionAge()>0){collisionInvariant(h,craft);maximum[0]=Math.max(maximum[0],craft.getY());}});
        h.succeedWhen(()->{h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.UNLOAD,"aircraft reaches the actual landing phase: "+craft.phase()+" / "+craft.position());h.assertTrue(maximum[0]>100.9&&craft.navigation().searches()>0,"three-dimensional route climbed above the barrier");f.close();});
    }
    @GameTest(template="helicopter_arena",batch="airRouteChange",timeoutTicks=2200)
    public static void buildingInsertedAheadInvalidatesCachedRouteAndFindsAnotherPassage(GameTestHelper h){
        var f=new Fixture(h,273000);f.wall(-12,12,134);var craft=f.craft();boolean[] changed={false};int[] side={0};double[] opposite={0};
        h.runAfterDelay(40,()->h.assertTrue(craft.deployTo(new Vec3(f.x+24,81,0)),"supported destination"));
        h.onEachTick(()->{if(craft.missionAge()==0)return;collisionInvariant(h,craft);
            if(!changed[0]&&Math.abs(craft.getZ())>8&&craft.getX()<f.x-7.2){changed[0]=true;side[0]=craft.getZ()>0?1:-1;if(side[0]>0)f.wall(12,56,134);else f.wall(-56,-12,134);}
            if(changed[0])opposite[0]=Math.max(opposite[0],-side[0]*craft.getZ());
        });
        h.succeedWhen(()->{h.assertTrue(craft.phase()==BlackwatchHelicopter.Phase.UNLOAD,"replanned aircraft reaches landing: "+craft.phase()+" / "+craft.position()+" / "+craft.navigation().status());
            h.assertTrue(changed[0]&&opposite[0]>19&&craft.navigation().searches()>=2&&craft.navigation().invalidations()>0,"new actual wall forces a different valid passage");f.close();});
    }
    @GameTest(template="helicopter_arena",batch="airRouteUnavailable",timeoutTicks=100)
    public static void unloadedTerrainAndFluidAreRejectedWithoutChunkCreation(GameTestHelper h){
        var f=new Fixture(h,274000);var craft=f.craft();
        h.runAfterDelay(40,()->{try(f){
            Vec3 unknown=new Vec3(f.x+4000,97,4000);int cx=(int)unknown.x>>4,cz=(int)unknown.z>>4;h.assertTrue(f.level.getChunkSource().getChunkNow(cx,cz)==null,"unknown destination starts unloaded");int forced=f.level.getForcedChunks().size();
            h.assertTrue(!craft.clearAirRoute(craft.position(),unknown,craft.position(),0),"unknown air corridor rejected");
            h.assertTrue(f.level.getChunkSource().getChunkNow(cx,cz)==null&&f.level.getForcedChunks().size()==forced,"route query neither loads nor forces terrain");
            var point=craft.position().add(0,0,10);f.level.setBlock(BlockPos.containing(point.add(0,1,0)),Blocks.WATER.defaultBlockState(),2);
            h.assertTrue(!craft.clearAirRoute(point,point,craft.position(),0),"water is not flyable air");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airRoutePersistence",timeoutTicks=100)
    public static void storedMissionNeverRestoresAnObsoleteWorldRoute(GameTestHelper h){
        var f=new Fixture(h,275000);f.wall(-12,12,134);var craft=f.craft();
        h.runAfterDelay(40,()->{try(f){
            h.assertTrue(craft.deployTo(new Vec3(f.x+24,81,0)),"supported mission");craft.setFlightPhase(BlackwatchHelicopter.Phase.APPROACH);
            craft.setPos(f.x-16,97,0);craft.navigation().guide(new Vec3(f.x+24,97,0));h.assertTrue(craft.navigation().searching(),"actual obstacle starts a pending search");
            var tag=new net.minecraft.nbt.CompoundTag();craft.saveWithoutId(tag);craft.load(tag);
            h.assertTrue(craft.navigation().status()==HelicopterNavigation.Status.DIRECT&&craft.navigation().waypoints().isEmpty(),"reload discards queued geometry and route references");
            h.assertTrue(craft.landing().equals(new Vec3(f.x+24,81,0))&&craft.getPassengers().size()==6,"durable mission and actual crew remain");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airRouteBudget",timeoutTicks=100)
    public static void concurrentAircraftShareOneWorldBudgetThatRefillsNextTick(GameTestHelper h){
        var f=new Fixture(h,276000);f.wall(-56,56,134);var aircraft=new ArrayList<BlackwatchHelicopter>();
        for(int i=0;i<5;i++){var craft=f.craft();craft.setPos(f.x-16,97,(i-2)*18);aircraft.add(craft);}
        h.runAfterDelay(40,()->{
            for(var craft:aircraft){craft.navigation().guide(new Vec3(f.x+24,97,craft.getZ()));h.assertTrue(craft.navigation().searching(),"each actual blocked corridor starts its own search");}
            for(var craft:aircraft)craft.navigation().guide(new Vec3(f.x+24,97,craft.getZ()));
            h.assertTrue(HelicopterNavigation.nodesUsed(f.level)==24,"five aircraft together consume only the 24-node world allowance");
            h.assertTrue(aircraft.get(4).navigation().expanded()==0,"last aircraft holds rather than exceeding this tick's budget");
        });
        h.runAfterDelay(41,()->{try(f){var waiting=aircraft.get(4);waiting.navigation().guide(new Vec3(f.x+24,97,waiting.getZ()));
            h.assertTrue(waiting.navigation().expanded()==6&&HelicopterNavigation.nodesUsed(f.level)==6,"new server tick replenishes the shared allowance");
        }h.succeed();});
    }
}
