package com.codex.blacklight.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Writes only the requested chunk during worldgen. No cross-chunk placement or regeneration. */
public final class CityChunkGenerator extends FlatLevelSource {
    public static final Codec<CityChunkGenerator> CODEC=RecordCodecBuilder.create(i->i.group(
        FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(CityChunkGenerator::settings),
        com.mojang.serialization.Codec.BOOL.optionalFieldOf("futian",false).forGetter(CityChunkGenerator::futian)).apply(i,CityChunkGenerator::new));
    private volatile long seed;
    private final boolean futian;
    public CityChunkGenerator(FlatLevelGeneratorSettings settings){this(settings,false);}
    public CityChunkGenerator(FlatLevelGeneratorSettings settings,boolean futian){super(settings);this.futian=futian;}
    public boolean futian(){return futian;}
    @Override protected Codec<? extends ChunkGenerator> codec(){return CODEC;}
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,RandomState random,long seed){this.seed=seed;return super.createState(lookup,random,seed);}
    @Override public void applyBiomeDecoration(WorldGenLevel level,ChunkAccess chunk,StructureManager structures){}
    @Override public int getMinY(){return 0;}
    @Override public int getGenDepth(){return futian?320:256;}
    @Override public int getSeaLevel(){return 60;}
    @Override public int getSpawnHeight(LevelHeightAccessor h){return 64;}
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(java.util.concurrent.Executor executor,Blender b,RandomState r,StructureManager m,ChunkAccess chunk){
        if(futian)return fillFutian(chunk);
        var pos=new BlockPos.MutableBlockPos();
        var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
            int x=chunk.getPos().getMinBlockX()+dx,z=chunk.getPos().getMinBlockZ()+dz;
            int top=top(seed,x,z);
            var lot=CityLayout.inside(x,z)?CityLayout.lot(seed,x/64,z/64):null;
            long hash=CityLayout.hash(seed,x,z);
            for(int y=Math.max(0,chunk.getMinBuildHeight());y<=Math.min(top,chunk.getMaxBuildHeight()-1);y++){
                BlockState state=state(seed,x,y,z,lot,hash);if(state.isAir())continue;
                chunk.setBlockState(pos.set(x,y,z),state,false);surface.update(dx,y,dz,state);floor.update(dx,y,dz,state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor h,RandomState random){
        if(futian){
            var column=FutianLayout.column(seed,x,z);
            for(int y=Math.min(column.top,h.getMaxBuildHeight()-1);y>=h.getMinBuildHeight();y--)
                if(type.isOpaque().test(column.state(y)))return y+1;
            return h.getMinBuildHeight();
        }
        for(int y=top(seed,x,z);y>=h.getMinBuildHeight();y--)if(type.isOpaque().test(state(seed,x,y,z)))return y+1;
        return h.getMinBuildHeight();
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor h,RandomState random){
        BlockState[] a=new BlockState[h.getHeight()];
        var column=futian?FutianLayout.column(seed,x,z):null;
        for(int i=0;i<a.length;i++)a[i]=column!=null?column.state(h.getMinBuildHeight()+i):state(seed,x,h.getMinBuildHeight()+i,z);
        return new NoiseColumn(h.getMinBuildHeight(),a);
    }
    private CompletableFuture<ChunkAccess> fillFutian(ChunkAccess chunk){
        int bx=chunk.getPos().getMinBlockX(),bz=chunk.getPos().getMinBlockZ();
        var tile=FutianMap.inside(bx,bz)?FutianMap.tile(bx>>4,bz>>4):null;
        var position=new BlockPos.MutableBlockPos();
        var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            var column=new FutianLayout.Column(seed,bx+x,bz+z,tile);
            int top=column.top;
            for(int y=Math.max(0,chunk.getMinBuildHeight());y<=Math.min(top,chunk.getMaxBuildHeight()-1);y++){
                var state=column.state(y);
                if(state.isAir())continue;
                chunk.setBlockState(position.set(bx+x,y,bz+z),state,false);surface.update(x,y,z,state);floor.update(x,y,z,state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    public static int top(long seed,int x,int z){return CityLayout.inside(x,z)?Math.max(74,66+CityLayout.lot(seed,x/64,z/64).floors()*5+5):63;}
    private static BlockState b(Block b){return b.defaultBlockState();}
    public static BlockState state(long seed,int x,int y,int z){
        return state(seed,x,y,z,CityLayout.inside(x,z)?CityLayout.lot(seed,x/64,z/64):null,CityLayout.hash(seed,x,z));
    }
    private static BlockState state(long seed,int x,int y,int z,CityLayout.Lot lot,long h){
        if(y<0)return b(Blocks.VOID_AIR);if(y==0)return b(Blocks.BEDROCK);if(y<60)return b(Blocks.STONE);if(y<63)return b(Blocks.DIRT);
        if(!CityLayout.inside(x,z))return b(y==63?Blocks.GRASS_BLOCK:Blocks.AIR);
        int u=x%64,v=z%64;
        boolean road=u<8||v<8,walk=u<12||v<12||u>60||v>60;
        if(y==63){
            if(road){boolean line=(u==3||v==3)&&Math.floorMod(u<8?z:x,8)<5;
                boolean crossing=(u>=9&&u<=17&&v<8&&u%2==0)||(v>=9&&v<=17&&u<8&&v%2==0);
                return b(line?Blocks.YELLOW_CONCRETE:crossing?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE);}
            if(lot.zone()==CityLayout.Zone.INFECTED&&CityLayout.tissue(seed,x,z)>.48)return CityBlocks.FLESH.get().defaultBlockState();
            return b(walk?Blocks.SMOOTH_STONE:lot.style()==0?Blocks.GRASS_BLOCK:Blocks.POLISHED_ANDESITE);
        }
        if(y>63&&u==10&&(v==22||v==54))return b(y<69?Blocks.IRON_BARS:y==69?Blocks.SEA_LANTERN:Blocks.AIR);
        if(road)return b(Blocks.AIR);
        if(lot.style()==0){
            if(u>=29&&u<=39&&v>=29&&v<=39){if(y==64&&(u==29||u==39||v==29||v==39))return b(Blocks.STONE_BRICKS);if(y==64)return b(Blocks.WATER);if(u==34&&v==34&&y<=67)return b(Blocks.QUARTZ_PILLAR);}
            int treeDistance=Math.min(Math.abs(u-20),Math.abs(u-50))+Math.min(Math.abs(v-20),Math.abs(v-50));
            if(treeDistance==0&&y<69)return b(Blocks.OAK_LOG);
            if(treeDistance<4&&y>=68&&y<=71)return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            if(y==64&&((u==24||u==44)&&v>=26&&v<=29))return b(Blocks.SMOOTH_STONE_SLAB);
            if(y==64&&(u==34||v==34))return b(Blocks.SMOOTH_STONE_SLAB);
            return b(Blocks.AIR);
        }
        int roof=64+lot.floors()*5;
        if(u>=14&&u<=50&&v>=14&&v<=50){
            boolean wall=u==14||u==50||v==14||v==50;
            if(y==roof)return u==48&&v==49?Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH):b(Blocks.SMOOTH_STONE);
            if(y==roof+1&&wall)return b(Blocks.STONE_BRICK_WALL);
            if(y>roof){
                if(u>=20&&u<=25&&v>=20&&v<=25&&y<=roof+3)return b(Blocks.LIGHT_GRAY_CONCRETE);
                if(u>=39&&u<=43&&v>=39&&v<=43&&y<=roof+4)return b(y==roof+4?Blocks.CUT_COPPER:Blocks.DARK_OAK_PLANKS);
                return b(Blocks.AIR);
            }
            if(wall){
                if(v==14&&u>=31&&u<=33&&y<=66)return b(Blocks.AIR);
                boolean hatch=(v==14||v==50)&&(u==20||u==32||u==44)||(u==14||u==50)&&v==32;
                if(lot.hive()&&hatch&&y==65)return CityBlocks.HATCH.get().defaultBlockState().setValue(CityHatchBlock.FACING,v==14?Direction.NORTH:v==50?Direction.SOUTH:u==14?Direction.WEST:Direction.EAST);
                if(lot.zone()==CityLayout.Zone.INFECTED&&y<67+(int)(CityLayout.tissue(seed,x,z)*15))return Math.floorMod(h,13)==0?CityBlocks.SAC.get().defaultBlockState():Math.floorMod(h,7)==0?CityBlocks.VEIN.get().defaultBlockState():CityBlocks.FLESH.get().defaultBlockState();
                if(lot.style()==3&&v==14&&((u==32&&y>=69&&y<=73)||(y==71&&u>=30&&u<=34)))return b(Blocks.RED_CONCRETE);
                int facadeCoordinate=(v==14||v==50)?u:v;
                boolean window=(y-64)%5>=1&&(y-64)%5<=3&&(facadeCoordinate-14)%6!=0;
                if(window)return b(lot.style()==1?Blocks.CYAN_STAINED_GLASS:Blocks.LIGHT_BLUE_STAINED_GLASS);
                return b(lot.style()==2?Blocks.BRICKS:lot.style()==3?Blocks.WHITE_CONCRETE:lot.style()==4?Blocks.LIGHT_GRAY_CONCRETE:Blocks.GRAY_CONCRETE);
            }
            // A service ladder connects every floor with a continuous clear shaft.
            if(u==48&&v==49)return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);
            if((y-64)%5==0)return b(Blocks.SMOOTH_STONE);
            if((y-64)%5==4&&(u==22||u==42)&&(v==22||v==42))return b(Blocks.SEA_LANTERN);
            if((y-64)%5==1&&u==20&&v>=22&&v<=26)return b(Blocks.OAK_SLAB);
            return b(Blocks.AIR);
        }
        if(lot.hive())for(int[] port:CityLayout.HATCHES)if(Math.abs(u-port[0])+Math.abs(v-port[1])<=5)return b(Blocks.AIR);
        if(y==64&&lot.zone()!=CityLayout.Zone.NORMAL){double tissue=CityLayout.tissue(seed,x,z);if(tissue>.46&&tissue<.51)return CityBlocks.LAYER.get().defaultBlockState();}
        if(lot.hive()&&y<=67&&((u==13||u==51)&&v>=16&&v<=48))return CityBlocks.FIBER.get().defaultBlockState();
        return b(Blocks.AIR);
    }
}
