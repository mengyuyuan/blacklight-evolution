package com.codex.blacklight.city;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.codex.blacklight.util.MapResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Immutable, offline OSM scenery. Bounded cache; no world access, entities or network requests. */
public final class LosAngelesMap {
    public static final int WIDTH=922, DEPTH=834, HEIGHT=512, GROUND=164, SHIFT=GROUND-63;
    private static final int X=WorldAtlas.worldX(-118.248),Z=WorldAtlas.worldZ(34.0595);
    private static final int WORLD_WIDTH=WorldAtlas.worldX(-118.238)-X,WORLD_DEPTH=WorldAtlas.worldZ(34.052)-Z;
    private static int localX(int x){return (int)((long)(x-X)*WIDTH/WORLD_WIDTH);}
    private static int localZ(int z){return (int)((long)(z-Z)*DEPTH/WORLD_DEPTH);}
    private static final String ROOT="/data/blacklight/city/los_angeles/";
    // Each decoded tile uses about 225 KiB; 32 tiles stay below 8 MiB.
    private static final BoundedLoadingCache<Long,Tile> CACHE=new BoundedLoadingCache<>(()->32);
    private static final class Palette { static final BlockState[] STATES=loadPalette(); }
    public record Tile(short[] blocks,short[] tops){
        public BlockState state(int x,int y,int z){return y<63||y>=HEIGHT?Blocks.AIR.defaultBlockState():Palette.STATES[Short.toUnsignedInt(blocks[(y-63)*256+z*16+x])];}
        public int top(int x,int z){return tops[z*16+x];}
    }
    public static boolean inside(int x,int z){return x>=X&&z>=Z&&x<X+WORLD_WIDTH&&z<Z+WORLD_DEPTH;}
    public static int top(int x,int z){
        int lx=localX(x),lz=localZ(z);return tile(lx>>4,lz>>4).top(lx&15,lz&15)+SHIFT;
    }
    public static Tile tile(int cx,int cz){
        long key=((long)cx<<32)^(cz&0xffffffffL);
        return CACHE.get(key,k->load(cx,cz));
    }
    public static BlockState state(int x,int y,int z){
        if(y<0||y>=WorldTerrain.HEIGHT)return Blocks.VOID_AIR.defaultBlockState();
        if(y==0)return Blocks.BEDROCK.defaultBlockState();
        if(y<GROUND-3)return Blocks.STONE.defaultBlockState();
        if(y<GROUND)return Blocks.DIRT.defaultBlockState();
        int lx=localX(x),lz=localZ(z);
        return tile(lx>>4,lz>>4).state(lx&15,y-SHIFT,lz&15);
    }
    private static Tile load(int x,int z){
        short[] blocks=new short[(HEIGHT-63)*256],tops=new short[256];Arrays.fill(tops,(short)63);
        try(var raw=MapResources.open(LosAngelesMap.class,ROOT+x+"_"+z+".bin.gz")){
            if(raw==null)throw new IOException("Missing Los Angeles chunk "+x+","+z);
            try(var in=new DataInputStream(raw)){
                if(in.readInt()!=0x42464331)throw new IOException("Invalid Los Angeles chunk header");
                for(int column=0;column<256;column++){
                    int runs=in.readUnsignedShort();if(runs>HEIGHT)throw new IOException("Too many runs");
                    int previous=63;
                    for(int r=0;r<runs;r++){
                        int start=in.readUnsignedShort(),length=in.readUnsignedShort(),id=in.readUnsignedShort();
                        if(start<previous||length==0||start+length>HEIGHT||id>=Palette.STATES.length)throw new IOException("Invalid Los Angeles run");
                        previous=start+length;
                        for(int y=start;y<previous;y++)blocks[(y-63)*256+column]=(short)id;
                        if(!Palette.STATES[id].isAir())tops[column]=(short)(previous-1);
                    }
                }
                if(in.read()!=-1)throw new IOException("Trailing Los Angeles data");
            }
            return new Tile(blocks,tops);
        }catch(IOException ex){throw new IllegalStateException("Cannot load bundled Los Angeles scenery",ex);}
    }
    private static BlockState[] loadPalette(){
        try(var stream=LosAngelesMap.class.getResourceAsStream(ROOT+"map.json")){
            if(stream==null)throw new IOException("Missing Los Angeles palette");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.get("schema").getAsInt()!=1||json.get("width").getAsInt()!=WIDTH
                    ||json.get("depth").getAsInt()!=DEPTH||json.get("height").getAsInt()!=HEIGHT
                    ||json.get("ground").getAsInt()!=63)
                throw new IOException("Los Angeles scenery metadata does not match its projection adapter");
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
        }catch(IOException ex){throw new IllegalStateException("Cannot load Los Angeles palette",ex);}
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value){
        return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Invalid block property "+value)));
    }
    private LosAngelesMap(){}
}
