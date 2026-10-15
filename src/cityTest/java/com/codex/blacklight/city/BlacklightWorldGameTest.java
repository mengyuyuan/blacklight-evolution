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
        try {
            h.assertTrue(CityTravel.travel(player)==1&&player.serverLevel().dimension().equals(CityTravel.WORLD),"beacon did not enter Blacklight world");
            h.assertTrue(player.getPersistentData().getCompound("BlacklightCityReturn").getString("dimension").equals(origin.dimension().location().toString()),"return dimension not saved");
            player.getCooldowns().removeCooldown(CityBlocks.TRANSIT.get());
            h.assertTrue(CityTravel.travel(player)==1&&player.serverLevel()==origin,"beacon failed to return");
            h.assertTrue(!player.getPersistentData().contains("BlacklightCityReturn"),"return record not consumed");
            h.succeed();
        } finally {player.discard();channel.finishAndReleaseAll();}
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
