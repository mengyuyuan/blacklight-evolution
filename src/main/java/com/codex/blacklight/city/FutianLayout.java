package com.codex.blacklight.city;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Actual street/building positions stay fixed; infection varies with the world seed. */
public final class FutianLayout {
    public record Port(BlockPos pos,Direction facing){}
    public record Site(String name,int minX,int minZ,int maxX,int maxZ,List<Port> ports){
        public int cellX(){return (minX+maxX)/2/FutianMap.CELL;}
        public int cellZ(){return (minZ+maxZ)/2/FutianMap.CELL;}
    }
    private static final class Sites {static final List<Site> ALL=load();}
    public static boolean sanctuary(int x,int z){return x>=320&&x<=1000&&z<=1000;}
    public static Site hive(long seed,int index){
        return Sites.ALL.get(index*4+(int)Math.floorMod(CityLayout.hash(seed,113+index,743),4));
    }
    public static CityLayout.Zone zone(long seed,int x,int z){
        if(!FutianMap.inside(x,z)||sanctuary(x,z))return CityLayout.Zone.NORMAL;
        int distance=99;
        for(int i=0;i<2;i++){var h=hive(seed,i);distance=Math.min(distance,Math.abs(x/FutianMap.CELL-h.cellX())+Math.abs(z/FutianMap.CELL-h.cellZ()));}
        if(distance<=1)return CityLayout.Zone.INFECTED;
        if(distance==2)return Math.floorMod(CityLayout.hash(seed,x/FutianMap.CELL,z/FutianMap.CELL),3)==0?CityLayout.Zone.CONFLICT:CityLayout.Zone.EDGE;
        return CityLayout.Zone.NORMAL;
    }
    public static BlockState state(long seed,int x,int y,int z){return overlay(seed,x,y,z,FutianMap.state(x,y,z));}
    public static BlockState overlay(long seed,int x,int y,int z,BlockState base){
        base=FutianCivicCentre.state(x,y,z,base);
        if(y<63||y>72||!FutianMap.inside(x,z)||sanctuary(x,z))return base;
        for(int i=0;i<2;i++){
            var h=hive(seed,i);
            if(x<h.minX||x>h.maxX||z<h.minZ||z>h.maxZ)continue;
            if(y==65)for(var port:h.ports)if(port.pos.getX()==x&&port.pos.getZ()==z)
                return CityBlocks.HATCH.get().defaultBlockState().setValue(CityHatchBlock.FACING,port.facing);
        }
        if(zone(seed,x,z)!=CityLayout.Zone.INFECTED)return base;
        double tissue=CityLayout.tissue(seed,x,z);
        if(y==63){
            if(tissue>.48&&(base.is(Blocks.GRASS_BLOCK)||base.is(Blocks.DIRT)||base.is(Blocks.MOSS_BLOCK)))return CityBlocks.FLESH.get().defaultBlockState();
        }else if(y<64+tissue*9&&base.isSolid()&&!base.hasBlockEntity()&&base.getFluidState().isEmpty()){
            long hash=CityLayout.hash(seed,x,z);
            return (Math.floorMod(hash,17)==0?CityBlocks.SAC:Math.floorMod(hash,9)==0?CityBlocks.VEIN:CityBlocks.FLESH).get().defaultBlockState();
        }
        return base;
    }
    private static List<Site> load(){
        try(var stream=FutianLayout.class.getResourceAsStream("/data/blacklight/city/futian/hives.json")){
            if(stream==null)throw new IllegalStateException("Missing Futian hive sites");
            var result=new ArrayList<Site>();
            for(var e:JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()){
                var j=e.getAsJsonObject();var bounds=j.getAsJsonArray("bounds");var ports=new ArrayList<Port>();
                for(var p:j.getAsJsonArray("ports")){var a=p.getAsJsonObject();ports.add(new Port(new BlockPos(a.get("x").getAsInt(),a.get("y").getAsInt(),a.get("z").getAsInt()),Direction.byName(a.get("facing").getAsString())));}
                if(ports.size()!=7||ports.stream().anyMatch(p->p.facing==null))throw new IllegalStateException("Invalid Futian hive");
                result.add(new Site(j.get("name").getAsString(),bounds.get(0).getAsInt(),bounds.get(1).getAsInt(),bounds.get(2).getAsInt(),bounds.get(3).getAsInt(),List.copyOf(ports)));
            }
            if(result.size()!=8)throw new IllegalStateException("Expected four west and four east hive sites");
            return List.copyOf(result);
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private FutianLayout(){}
}
