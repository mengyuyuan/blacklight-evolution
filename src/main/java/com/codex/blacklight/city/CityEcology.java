package com.codex.blacklight.city;

import com.codex.blacklight.infection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import java.util.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Common ecology for authored and future atlas cities; no terrain generation or map mutation. */
@EventBusSubscriber(modid="blacklight")
public final class CityEcology {
    private static final Map<ServerPlayer,com.codex.blacklight.network.DistrictZonePayload> SENT=new WeakHashMap<>();
    private CityEcology(){}
    public static String atlasCityId(int x,int z){return WorldAtlas.regionId(x,z);}
    public static String cityId(ServerLevel level,BlockPos pos){
        if(level.dimension().equals(CityTravel.WORLD))return atlasCityId(pos.getX(),pos.getZ());
        if(level.dimension().equals(CityTravel.CITY))return "legacy_city";
        return level.dimension().location().toString();
    }
    public static CityEcologyData.Key key(ServerLevel level,BlockPos pos){
        return CityEcologyData.key(cityId(level,pos),pos.getX(),pos.getZ());
    }
    public static int authoredStage(ServerLevel level,BlockPos pos){
        if(!CityTravel.isCity(level.dimension()))return 0;
        var zone=level.dimension().equals(CityTravel.WORLD)?WorldTerrain.zone(level.getSeed(),pos.getX(),pos.getZ())
                :CitySystem.futian(level)?FutianLayout.zone(level.getSeed(),pos.getX(),pos.getZ())
                :CityLayout.zone(level.getSeed(),Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64));
        return switch(zone){case INFECTED->2;case CONFLICT,EDGE->1;default->0;};
    }
    public static void advance(ServerLevel level,BlockPos pos){
        advanceBatch(level,List.of(pos));
    }
    /** One snapshot for all occupied cells: player count and visitation order cannot accelerate spread. */
    public static void advanceBatch(ServerLevel level,Collection<BlockPos> occupied){
        if(!WorldOutbreakSystem.active(level))return;
        var cells=new LinkedHashMap<CityEcologyData.Key,BlockPos>();
        for(var pos:occupied)for(int x:new int[]{-96,0,96})for(int z:new int[]{-96,0,96}){
            var near=pos.offset(x,0,z);
            if(level.getChunkSource().getChunkNow(near.getX()>>4,near.getZ()>>4)!=null
                    &&level.isPositionEntityTicking(near)&&!WorldOutbreakSystem.sanctuary(level,near))cells.putIfAbsent(key(level,near),near);
        }
        if(cells.isEmpty())return;
        var data=CityEcologyData.get(level);
        // Capture neighbour state before publishing any observation in this batch.
        var before=new HashMap<CityEcologyData.Key,DistrictThreat.Zone>();
        for(var k:cells.keySet())for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
            var other=new CityEcologyData.Key(k.city(),k.x()+d[0],k.z()+d[1]);var history=data.get(other);
            if(cells.containsKey(other)&&history!=null&&history.observed())before.put(other,history.zone());
        }
        var counts=new HashMap<CityEcologyData.Key,int[]>();
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&BlacklightInfectionSystem.isInfected(mob)
                &&level.isPositionEntityTicking(mob.blockPosition())){
            var k=key(level,mob.blockPosition());if(!cells.containsKey(k))continue;
            var c=counts.computeIfAbsent(k,v->new int[4]);c[0]++;
            if(mob instanceof com.codex.blacklight.entity.BlacklightInfectedHost host){
                if(host.tissueAge()>=TissueGrowth.MATURITY_TICKS)c[1]++;
                if(host.isEvolved())c[2]++;
            }
        }
        if(BuildingInfectionConfig.ENABLED.get())for(var colony:BuildingInfectionData.get(level).colonies){
            var k=key(level,colony.origin);
            if(!colony.dead&&cells.containsKey(k)&&level.isPositionEntityTicking(colony.origin)
                    &&level.getChunkSource().getChunkNow(colony.origin.getX()>>4,colony.origin.getZ()>>4)!=null
                    &&level.getBlockState(colony.origin).is(com.codex.blacklight.registry.ModBlocks.BUILDING_CORE.get()))
                counts.computeIfAbsent(k,v->new int[4])[3]+=1+colony.stage;
        }
        if(CityTravel.isCity(level.dimension())){
            var city=CityData.get(level);
            for(int hive=0;hive<2;hive++){
                var port=CitySystem.port(level,hive,0);var k=key(level,port);
                if(cells.containsKey(k)&&!city.destroyed(hive)&&CityHiveSupply.ready(city,hive)
                        &&level.isPositionEntityTicking(port)&&level.getChunkSource().getChunkNow(port.getX()>>4,port.getZ()>>4)!=null
                        &&level.getBlockState(port).is(CityBlocks.HATCH.get()))counts.computeIfAbsent(k,v->new int[4])[3]+=4;
            }
        }
        for(var cell:cells.entrySet()){
            var k=cell.getKey();int red=0,yellow=0;
            for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
                var zone=before.get(new CityEcologyData.Key(k.city(),k.x()+d[0],k.z()+d[1]));
                if(zone==DistrictThreat.Zone.RED)red++;else if(zone==DistrictThreat.Zone.YELLOW)yellow++;
            }
            var c=counts.getOrDefault(k,new int[4]);
            data.observe(k,level.getGameTime(),OutbreakConfig.INTERVAL.get(),authoredStage(level,cell.getValue()),OutbreakConfig.DAY_TICKS.get(),new DistrictThreat.Signal(c[0],c[1],c[2],c[3],red,yellow));
        }
    }
    public static int stage(ServerLevel level,BlockPos pos){
        int global=WorldOutbreakSystem.stage(level);
        var district=CityEcologyData.get(level).get(key(level,pos));
        return district==null?DistrictThreat.stage(zone(level,pos),global,authoredStage(level,pos))
                :district.stage(global,authoredStage(level,pos),OutbreakConfig.DAY_TICKS.get());
    }
    public static DistrictThreat.Zone zone(ServerLevel level,BlockPos pos){
        if(!WorldOutbreakSystem.active(level)||WorldOutbreakSystem.sanctuary(level,pos))return DistrictThreat.Zone.GREEN;
        var district=CityEcologyData.get(level).get(key(level,pos));
        if(district!=null&&district.observed())return district.zone();
        int local=district==null?authoredStage(level,pos):district.stage(WorldOutbreakSystem.stage(level),authoredStage(level,pos),OutbreakConfig.DAY_TICKS.get());
        return DistrictThreat.zone(DistrictThreat.Zone.GREEN,DistrictThreat.seed(local));
    }
    public static int populationCap(ServerLevel level,BlockPos pos){return DistrictThreat.populationCap(zone(level,pos),OutbreakConfig.LOCAL_CAP.get());}
    public static boolean ambientAllowed(ServerLevel level,BlockPos pos,int roll){return Math.floorMod(roll,100)<DistrictThreat.ambientChance(zone(level,pos));}
    public static void release(ServerLevel level,BlockPos pos){
        if(!WorldOutbreakSystem.sanctuary(level,pos))CityEcologyData.get(level).release(key(level,pos));
    }
    public static void cleared(ServerLevel level,BlockPos pos){
        if(CityTravel.isCity(level.dimension()))CityEcologyData.get(level).clear(key(level,pos),OutbreakConfig.DAY_TICKS.get()*3L);
    }
    public static void synchronize(ServerPlayer player){
        var level=player.serverLevel();
        var state=new com.codex.blacklight.network.DistrictZonePayload(level.dimension().location(),WorldOutbreakSystem.active(level),
                zone(level,player.blockPosition()).ordinal(),stage(level,player.blockPosition()));
        if(!state.equals(SENT.get(player))){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,state);SENT.put(player,state);}
    }
    @SubscribeEvent public static void status(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level&&level.getGameTime()%40==0)for(var player:level.players())synchronize(player);
    }
}
