package com.codex.blacklight.city;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Immutable, offline OSM scenery. Bounded cache; no world access, entities or network requests. */
public final class FutianMap {
    public static final int WIDTH=1440, DEPTH=1232, HEIGHT=320, CELL=192;
    private static final String ROOT="/data/blacklight/city/futian/";
    private static final Map<Long,Tile> CACHE=new LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Tile> entry){return size()>32;}
    };
    private static final class Palette { static final BlockState[] STATES=loadPalette(); }
    public record Tile(short[] blocks,short[] tops){
        public BlockState state(int x,int y,int z){return y<63||y>=HEIGHT?Blocks.AIR.defaultBlockState():Palette.STATES[Short.toUnsignedInt(blocks[(y-63)*256+z*16+x])];}
        public int top(int x,int z){return tops[z*16+x];}
    }
    public static boolean inside(int x,int z){return x>=0&&z>=0&&x<WIDTH&&z<DEPTH;}
    public static synchronized Tile tile(int cx,int cz){
        long key=((long)cx<<32)^(cz&0xffffffffL);
        return CACHE.computeIfAbsent(key,k->load(cx,cz));
    }
    public static BlockState state(int x,int y,int z){
        if(y<0||y>=HEIGHT)return Blocks.VOID_AIR.defaultBlockState();
        if(y==0)return Blocks.BEDROCK.defaultBlockState();
        if(y<60)return Blocks.STONE.defaultBlockState();
        if(y<63)return Blocks.DIRT.defaultBlockState();
        if(!inside(x,z))return (y==63?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState();
        return tile(x>>4,z>>4).state(x&15,y,z&15);
    }
    private static Tile load(int x,int z){
        short[] blocks=new short[(HEIGHT-63)*256],tops=new short[256];Arrays.fill(tops,(short)63);
        try(var raw=FutianMap.class.getResourceAsStream(ROOT+x+"_"+z+".bin.gz")){
            if(raw==null)throw new IOException("Missing Futian chunk "+x+","+z);
            try(var in=new DataInputStream(new GZIPInputStream(raw))){
                if(in.readInt()!=0x42464331)throw new IOException("Invalid Futian chunk header");
                for(int column=0;column<256;column++){
                    int runs=in.readUnsignedShort();if(runs>HEIGHT)throw new IOException("Too many runs");
                    int previous=63;
                    for(int r=0;r<runs;r++){
                        int start=in.readUnsignedShort(),length=in.readUnsignedShort(),id=in.readUnsignedShort();
                        if(start<previous||length==0||start+length>HEIGHT||id>=Palette.STATES.length)throw new IOException("Invalid Futian run");
                        previous=start+length;
                        for(int y=start;y<previous;y++)blocks[(y-63)*256+column]=(short)id;
                        if(!Palette.STATES[id].isAir())tops[column]=(short)(previous-1);
                    }
                }
                if(in.read()!=-1)throw new IOException("Trailing Futian data");
            }
            return new Tile(blocks,tops);
        }catch(IOException ex){throw new IllegalStateException("Cannot load bundled Futian scenery",ex);}
    }
    private static BlockState[] loadPalette(){
        try(var stream=FutianMap.class.getResourceAsStream(ROOT+"map.json")){
            if(stream==null)throw new IOException("Missing Futian palette");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var array=json.getAsJsonArray("palette");var states=new BlockState[array.size()];
            for(int i=0;i<array.size();i++){
                var entry=array.get(i).getAsJsonObject();var id=new ResourceLocation(entry.get("Name").getAsString());
                if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Unsupported block "+id);
                var block=BuiltInRegistries.BLOCK.get(id);var state=block.defaultBlockState();
                if(entry.has("Properties"))for(var property:entry.getAsJsonObject("Properties").entrySet()){
                    var key=block.getStateDefinition().getProperty(property.getKey());
                    if(key==null)throw new IOException("Unsupported property "+id+"/"+property.getKey());
                    state=set(state,key,property.getValue().getAsString());
                }
                states[i]=state;
            }
            return states;
        }catch(IOException ex){throw new IllegalStateException("Cannot load Futian palette",ex);}
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Invalid block property "+value)));
    }
    private FutianMap(){}
}
