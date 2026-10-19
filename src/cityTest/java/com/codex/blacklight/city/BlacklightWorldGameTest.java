package com.codex.blacklight.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class BlacklightWorldGameTest {
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void lowUrbanLandStaysDryButReservoirAndUnmappedOceanStayWet(GameTestHelper h){
        long seed=h.getLevel().getSeed();
        for(int[] p:new int[][]{{-10880,3900},{350,1800},{1450,800},{-10804,3868},{-10816,3888},{-10832,3904}}){
            var c=WorldTerrain.column(seed,p[0],p[1]);
            h.assertTrue(c.water==0&&c.ground>=64,"mapped urban plot misclassified as coast "+p[0]+","+p[1]);
            h.assertTrue(!c.state(c.ground).is(Blocks.SAND),"urban ground still sand");
        }
        for(int[] p:new int[][]{{-17197,-8714},{500000,500000}}){
            var c=WorldTerrain.column(seed,p[0],p[1]);
            h.assertTrue(c.water>c.ground&&c.state(c.water).is(Blocks.WATER),"water lost while repairing lowland "+p[0]+","+p[1]);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void landmarkCrownsHaveNarrowSilhouettesAndSolidApex(GameTestHelper h){
        long seed=h.getLevel().getSeed();
        for(int[] p:new int[][]{{242,1710,1},{-10938,3809,2}}){
            var f=WorldAtlas.tile(p[0],p[1]).features.stream().filter(a->a.landmark==p[2]).findFirst().orElseThrow();
            int middle=f.base+f.height/2,crown=f.base+(int)(f.height*.97);int wide=0,narrow=0;
            for(int x=(int)Math.floor(f.x0);x<=Math.ceil(f.x1);x++){
                var c=WorldTerrain.column(seed,x,p[1]);
                if(!c.state(middle-middle%5+f.base%5).isAir())wide++;
                if(!c.state(crown-crown%5+f.base%5).isAir())narrow++;
            }
            h.assertTrue(wide>20&&narrow<wide/2,"landmark still extrudes straight upwards: "+p[2]+" widths="+wide+"/"+narrow);
            h.assertTrue(!WorldTerrain.column(seed,p[0],p[1]).state(f.base+f.height).isAir(),"landmark apex missing");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void lowCoreBorderHasSolidGroundAndNoSeaRing(GameTestHelper h){
        long seed=h.getLevel().getSeed();
        for(int z=640;z<880;z+=16)for(int x=1438;x<=1443;x++){
            var c=WorldTerrain.column(seed,x,z);
            h.assertTrue(!c.state(62).isAir()&&c.state(62).getFluidState().isEmpty(),"gap under core boundary");
            if(x>=1440)h.assertTrue(c.water==0&&c.ground>=64,"sea ring beside core");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void atlasExpansionInvalidatesOnlyTheOldPregenPlan(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);
        CityPregenerator.start(world,3);var data=CityGenerationData.get(world);data.cursor=12345;
        var saved=CityGenerationData.load(data.save(new net.minecraft.nbt.CompoundTag(),world.registryAccess()),world.registryAccess());
        h.assertTrue(saved.cursor==12345&&saved.mode==3&&saved.atlasFingerprint.equals(WorldAtlas.fingerprint()),"world plan progress/fingerprint lost");
        data.atlasFingerprint="previous-atlas";CityPregenerator.tick(world);
        h.assertTrue(!data.running&&!CityPregenerator.hasTicket(world)&&data.cursor==12345&&!data.failure.isEmpty(),"atlas update reused old cursor or ticket");
        CityPregenerator.start(world,1);CityPregenerator.pause(world);h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void beaconTravelsToWorldAndReturnsToSavedDimension(GameTestHelper h){
        var origin=h.getLevel();
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"world-travel-qa");
        var player=new net.minecraft.server.level.ServerPlayer(origin.getServer(),origin,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        // Keep real player/teleport state and a real channel; discard only outbound packets.
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(origin.getServer(),connection,player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
        };
        var ground=h.absolutePos(new BlockPos(3,1,3));origin.setBlock(ground,Blocks.STONE.defaultBlockState(),3);
        origin.setBlock(ground.above(),Blocks.AIR.defaultBlockState(),3);origin.setBlock(ground.above(2),Blocks.AIR.defaultBlockState(),3);
        player.setPos(ground.getX()+.5,ground.getY()+1,ground.getZ()+.5);
        h.assertTrue(CityTravel.travel(player)==1,"beacon request rejected");
        int[] stage={0};boolean[] closed={false};
        var destination=new HydraDestination();
        h.onEachTick(()->{
            if(closed[0])return;
            // Exercise the production tick handler; travel is asynchronous since the
            // hydra audit, and must wait for both terrain and entity storage.
            CityTravel.transitLanding(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            if(stage[0]==0&&player.serverLevel().dimension().equals(CityTravel.WORLD)){
                h.assertTrue(player.getPersistentData().getCompound("BlacklightCityReturn").getString("dimension").equals(origin.dimension().location().toString()),"return dimension not saved");
                player.getCooldowns().removeCooldown(CityBlocks.TRANSIT.get());
                h.assertTrue(CityTravel.travel(player)==1,"return request rejected");stage[0]=1;
            }else if(stage[0]==1&&player.serverLevel()==origin){
                h.assertTrue(!player.getPersistentData().contains("BlacklightCityReturn"),"return record not consumed");
                h.assertTrue(player.position().distanceToSqr(ground.getX()+.5,ground.getY()+1,ground.getZ()+.5)<.01,"return position lost");
                stage[0]=2;
            }else if(stage[0]==2&&destination.prepare(origin)){
                var entrance=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HYDRA.get().create(origin);
                entrance.setBodySize(1.6F);entrance.plant(player.getUUID());entrance.moveTo(player.position());
                origin.addFreshEntity(entrance);
                h.assertTrue(CityTravel.transferFromHydra(player,entrance,ground.above(),1.6F),"hydra transfer rejected prepared destination");
                h.assertTrue(player.serverLevel().dimension().equals(CityTravel.WORLD),"new hydra still routed to legacy Futian");
                var carrier=HydraTransport.carrier(player);
                h.assertTrue(carrier!=null&&carrier.action()==7&&!player.isPassenger(),"arrival animation missing or replaced by riding");
                h.assertTrue(entrance.link().dimension().equals(CityTravel.WORLD)&&entrance.link().endpoint().equals(carrier.getUUID()),"outbound link not attached to world endpoint");
                var restored=HydraLink.load(carrier.link().save());
                h.assertTrue(restored.dimension().equals(origin.dimension())&&restored.endpoint().equals(entrance.getUUID()),"return endpoint lost original identity");
                carrier.discard();entrance.discard();destination.release();
                closed[0]=true;player.discard();channel.finishAndReleaseAll();h.succeed();
            }
        });
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void worldDimensionIsIndependentAndCoreUnchanged(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);
        var old=h.getLevel().getServer().getLevel(CityTravel.CITY);
        h.assertTrue(world!=null&&old!=null&&world!=old,"new/legacy dimensions missing or aliased");
        h.assertTrue(world.getChunkSource().getGenerator() instanceof BlacklightWorldGenerator,"wrong world generator");
        h.assertTrue(world.getHeight()==1536&&old.getHeight()==320,"dimension heights not isolated");
        CityPregenerator.pause(world);
        for(int[] p:new int[][]{{670,820},{670,615},{0,0},{1439,1231},{463,171}}){
            var c=WorldTerrain.column(world.getSeed(),p[0],p[1]);
            for(int y=0;y<320;y++)h.assertTrue(c.state(y).equals(FutianLayout.state(world.getSeed(),p[0],y,p[1])),"refined Futian core changed");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=1000)
    public static void geographicChunksMatchColumnsAndAllHeightmaps(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);var generator=world.getChunkSource().getGenerator();
        int[][] points={{-10000,-5000},{17000,-1000},{115000,-45000},{19000,-2000},{-1,0},{1440,1232},{1023,1024},{-1025,-1024},{-30000,-25000},{25000,-10000}};
        long start=System.nanoTime();
        for(var p:points){
            var cp=new ChunkPos(Math.floorDiv(p[0],16),Math.floorDiv(p[1],16));
            var chunk=new ProtoChunk(cp,UpgradeData.EMPTY,world,world.registryAccess().registryOrThrow(Registries.BIOME),null);
            generator.fillFromNoise(net.minecraft.world.level.levelgen.blending.Blender.empty(),world.getChunkSource().randomState(),world.structureManager(),chunk).join();
            for(int dz=0;dz<16;dz+=3)for(int dx=0;dx<16;dx+=3){
                int x=cp.getMinBlockX()+dx,z=cp.getMinBlockZ()+dz;var column=WorldTerrain.column(world.getSeed(),x,z);
                int[] tops=new int[Heightmap.Types.values().length];
                for(int y=0;y<world.getHeight();y++){
                    var state=chunk.getBlockState(new BlockPos(x,y,z));
                    h.assertTrue(state.equals(column.state(y)),"chunk/column mismatch "+x+","+y+","+z);
                    for(var type:Heightmap.Types.values())if(type.isOpaque().test(state))tops[type.ordinal()]=y+1;
                }
                for(var type:Heightmap.Types.values())h.assertTrue(generator.getBaseHeight(x,z,type,world,world.getChunkSource().randomState())==tops[type.ordinal()],"height mismatch "+type);
            }
        }
        System.out.printf(java.util.Locale.ROOT,"WORLD_FILL_QA chunks=%d elapsed_ms=%.3f%n",points.length,(System.nanoTime()-start)/1e6);
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600)
    public static void distantLiveChunkKeepsPlayerEdits(GameTestHelper h){
        var world=h.getLevel().getServer().getLevel(CityTravel.WORLD);
        var p=new BlockPos(-10000,1400,-5000);world.getChunkAt(p);
        h.assertTrue(world.setBlock(p,Blocks.DIAMOND_BLOCK.defaultBlockState(),3),"marker not placed");
        var generator=world.getChunkSource().getGenerator();
        generator.getBaseColumn(p.getX(),p.getZ(),world,world.getChunkSource().randomState());
        h.assertTrue(world.getBlockState(p).is(Blocks.DIAMOND_BLOCK),"generator rewrote existing world");
        world.setBlock(p,Blocks.AIR.defaultBlockState(),3);h.succeed();
    }
}
