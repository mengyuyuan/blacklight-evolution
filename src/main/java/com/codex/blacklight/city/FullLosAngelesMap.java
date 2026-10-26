package com.codex.blacklight.city;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import com.codex.blacklight.util.CompactStreams;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Whole municipal Arnis scenery. Only the requested region/chunk is decoded. */
final class FullLosAngelesMap {
    private static final String COMPACT_ROOT="/data/blacklight/city/los_angeles_compact/";
    private static final String ROOT=FullLosAngelesMap.class.getResource(COMPACT_ROOT+"index.json")!=null?COMPACT_ROOT:"/data/blacklight/city/los_angeles_full/";
    private static final int HEIGHT=1536,REGION_HEADER=8+1024*8;
    private static final double R=6371000.0;
    private static final BoundedLoadingCache<Long,byte[]> REGIONS=new BoundedLoadingCache<>(()->4);
    private static final BoundedLoadingCache<Long,TemplateChunk> CHUNKS=new BoundedLoadingCache<>(()->64);
    private record GroupKey(long region,int group){}
    private static final BoundedLoadingCache<GroupKey,Map<Integer,TemplateChunk>> GROUPS=new BoundedLoadingCache<>(()->4);
    private static final java.util.concurrent.Semaphore GROUP_LOADS=new java.util.concurrent.Semaphore(2);
    private static final Map<String,BlockState> STATES=new ConcurrentHashMap<>();
    private static final Set<String> FORBIDDEN=Set.of("command_block","chain_command_block","repeating_command_block","structure_block","jigsaw","spawner");
    private record Region(int x,int z,int bytes,byte[] mask){}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static final class Metadata {
        static final Metadata INSTANCE=new Metadata();
        final double originLat,originLon,cos,originY,minX,minZ,maxX,maxZ;
        final Map<Long,Region> regions;
        final boolean compact;
        Metadata(){
            try(var stream=resource("index.json");var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                int schema=json.get("schema").getAsInt();compact=schema==3;
                if(schema!=2&&schema!=3||!json.get("complete").getAsBoolean())throw new IOException("Incomplete whole-city Arnis dataset");
                if(compact&&(!"COL-RLE1".equals(json.get("chunkEncoding").getAsString())||json.get("groupSize").getAsInt()!=CompactCityCodec.GROUP_SIZE))throw new IOException("Unsupported compact municipality");
                var projection=json.getAsJsonObject("projection");if(projection.get("earthRadius").getAsDouble()!=R)throw new IOException("Arnis projection radius mismatch");originLat=projection.get("originLat").getAsDouble();originLon=projection.get("originLon").getAsDouble();
                cos=Math.cos(Math.toRadians(originLat));originY=R*Math.log(Math.tan(Math.PI/4+Math.toRadians(originLat)/2));
                var bounds=json.getAsJsonArray("worldBounds");minX=Math.floor(bounds.get(0).getAsDouble())-1;minZ=Math.floor(bounds.get(1).getAsDouble())-1;maxX=Math.ceil(bounds.get(2).getAsDouble())+1;maxZ=Math.ceil(bounds.get(3).getAsDouble())+1;
                var masks=new HashMap<Long,byte[]>();
                try(var in=new DataInputStream(new BufferedInputStream(resource("coverage.bin")))){
                    if(in.readInt()!=0x4c41434d||in.readInt()!=1)throw new IOException("Invalid municipal coverage");
                    int count=in.readInt();if(count<1||count>100_000)throw new IOException("Invalid coverage count");
                    for(int i=0;i<count;i++){int x=in.readInt(),z=in.readInt();byte[] mask=in.readNBytes(128);if(mask.length!=128)throw new EOFException();masks.put(key(x,z),mask);}
                    if(in.read()!=-1)throw new IOException("Trailing coverage data");
                }
                var entries=new HashMap<Long,Region>();
                for(var entry:json.getAsJsonArray("regions")){
                    var object=entry.getAsJsonObject();int x=object.get("x").getAsInt(),z=object.get("z").getAsInt(),bytes=object.get("bytes").getAsInt();long k=key(x,z);
                    if(bytes<REGION_HEADER||bytes>256*1024*1024||!masks.containsKey(k)||entries.containsKey(k))throw new IOException("Invalid Arnis region index");
                    entries.put(k,new Region(x,z,bytes,masks.get(k)));
                }
                if(entries.size()!=masks.size())throw new IOException("Whole-city regions do not cover the municipality");regions=Map.copyOf(entries);
            }catch(IOException e){throw new IllegalStateException("Cannot load whole Los Angeles scenery index",e);}
        }
    }
    static Column column(int worldX,int worldZ){
        if(!CityGenerationProfile.usesArchive())return null;
        var m=Metadata.INSTANCE;
        if(worldX<m.minX||worldX>m.maxX||worldZ<m.minZ||worldZ>m.maxZ)return null;
        double lon=WorldAtlas.ORIGIN_LON+(worldX+.5)/WorldAtlas.LON_METRES;lon-=360*Math.floor((lon+180)/360);
        double lat=WorldAtlas.ORIGIN_LAT-(worldZ+.5)/WorldAtlas.LAT_METRES;
        int x=(int)Math.floor(R*Math.toRadians(lon-m.originLon)*m.cos);
        int z=(int)Math.floor((m.originY-R*Math.log(Math.tan(Math.PI/4+Math.toRadians(lat)/2)))*m.cos);
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16),slot=Math.floorMod(cz,32)*32+Math.floorMod(cx,32);
        var region=m.regions.get(key(Math.floorDiv(cx,32),Math.floorDiv(cz,32)));
        if(region==null||(region.mask[slot>>>3]&(1<<(slot&7)))==0)return null;
        return new Column(CHUNKS.get(key(cx,cz),FullLosAngelesMap::load),Math.floorMod(x,16),Math.floorMod(z,16));
    }
    record Column(TemplateChunk chunk,int x,int z){
        int top(){return chunk.tops[z*16+x];}
        int bottom(){return chunk.bottoms[z*16+x];}
        BlockState state(int y){return chunk.state(x,y,z);}
    }
    private record Section(BlockState[] palette,long[] data,int bits,int perLong){
        BlockState state(int index){return palette.length==1?palette[0]:palette[(int)((data[index/perLong]>>>((index%perLong)*bits))&((1L<<bits)-1))];}
    }
    static final class TemplateChunk {
        final Section[] sections=new Section[HEIGHT/16];final short[] tops=new short[256],bottoms=new short[256];
        private short[][] compactCells;private BlockState[] compactPalette;
        TemplateChunk(CompactCityCodec.Chunk chunk,BlockState[] palette){
            System.arraycopy(chunk.tops(),0,tops,0,256);System.arraycopy(chunk.bottoms(),0,bottoms,0,256);
            compactCells=chunk.cells();compactPalette=palette;
        }
        TemplateChunk(DataInputStream in)throws IOException{
            if(in.readInt()!=0x4c414333)throw new IOException("Invalid Arnis chunk header");int count=in.readUnsignedShort();
            if(count>sections.length)throw new IOException("Too many Arnis sections");
            for(int i=0;i<256;i++)tops[i]=in.readShort();for(int i=0;i<256;i++)bottoms[i]=in.readShort();
            for(int i=0;i<256;i++)if(bottoms[i]<0||tops[i]<bottoms[i]||tops[i]>=HEIGHT)throw new IOException("Invalid municipal column heights");
            for(int s=0;s<count;s++){
                int y=in.readUnsignedByte(),size=in.readUnsignedShort();if(y>=sections.length||sections[y]!=null||size==0||size>4096)throw new IOException("Invalid Arnis section");
                var palette=new BlockState[size];for(int i=0;i<size;i++)palette[i]=FullLosAngelesMap.state(in);
                int bits=Math.max(4,32-Integer.numberOfLeadingZeros(size-1)),per=64/bits,n=in.readUnsignedShort(),expected=size==1?0:(4096+per-1)/per;
                if(n!=expected)throw new IOException("Invalid Arnis block storage");long[] data=new long[n];for(int i=0;i<n;i++)data[i]=in.readLong();
                var section=new Section(palette,data,bits,per);sections[y]=section;

            }
            if(in.read()!=-1)throw new IOException("Trailing Arnis chunk data");
        }
        BlockState state(int x,int y,int z){
            if(y<0||y>=HEIGHT)return Blocks.VOID_AIR.defaultBlockState();if(y==0)return Blocks.BEDROCK.defaultBlockState();
            int bottom=bottoms[z*16+x];if(y<bottom-3)return Blocks.STONE.defaultBlockState();if(y<bottom)return Blocks.DIRT.defaultBlockState();
            if(compactCells!=null){var cells=compactCells[y>>>4];return cells==null?Blocks.AIR.defaultBlockState():compactPalette[Short.toUnsignedInt(cells[cells.length==1?0:(y&15)*256+z*16+x])];}
            var section=sections[y>>>4];return section==null?Blocks.AIR.defaultBlockState():section.state((y&15)*256+z*16+x);
        }
    }
    private static TemplateChunk load(long key){
        int cx=(int)(key>>32),cz=(int)key,rx=Math.floorDiv(cx,32),rz=Math.floorDiv(cz,32),slot=Math.floorMod(cz,32)*32+Math.floorMod(cx,32);
        var entry=Metadata.INSTANCE.regions.get(key(rx,rz));
        if(Metadata.INSTANCE.compact){
            var chunk=GROUPS.get(new GroupKey(key(rx,rz),slot/CompactCityCodec.GROUP_SIZE),FullLosAngelesMap::loadGroup).get(slot);
            if(chunk==null)throw new IllegalStateException("Missing compact municipal chunk "+cx+","+cz);return chunk;
        }
        byte[] region=region(key(rx,rz));
        try(var header=new DataInputStream(new ByteArrayInputStream(region))){
            if(header.readInt()!=0x4c415232||header.readInt()!=1024)throw new IOException("Invalid Arnis region header");header.skipNBytes(slot*8L);
            int offset=header.readInt(),length=header.readInt();if(offset<REGION_HEADER||length<1||(long)offset+length>region.length)throw new IOException("Missing municipal Arnis chunk");
            try(var in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(region,offset,length)))){return new TemplateChunk(in);}
        }catch(IOException e){throw new IllegalStateException("Cannot decode municipal Arnis chunk "+cx+","+cz,e);}
    }
    private static byte[] region(long key){
        return REGIONS.get(key,k->{
            var entry=Metadata.INSTANCE.regions.get(k);if(entry==null)throw new IllegalStateException("Missing municipal region index");
            try(var raw=resource(entry.x+"_"+entry.z+(Metadata.INSTANCE.compact?".larc":".lar"))){
                byte[] data=raw.readNBytes(entry.bytes+1);if(data.length!=entry.bytes)throw new IOException("Arnis region size mismatch");return data;
            }catch(IOException e){throw new IllegalStateException("Cannot read municipal Arnis region",e);}
        });
    }
    private static Map<Integer,TemplateChunk> loadGroup(GroupKey key){
        GROUP_LOADS.acquireUninterruptibly();
        try{
            byte[] region=region(key.region);var entry=Metadata.INSTANCE.regions.get(key.region);
            try(var header=new DataInputStream(new ByteArrayInputStream(region))){
                if(header.readInt()!=0x4c415234||header.readInt()!=1024)throw new IOException("Invalid compact region header");
                int first=key.group*CompactCityCodec.GROUP_SIZE;header.skipNBytes(first*8L);
                int offset=0,length=0;var slots=new HashSet<Integer>();
                for(int slot=first;slot<first+CompactCityCodec.GROUP_SIZE;slot++){
                    int at=header.readInt(),n=header.readInt();boolean required=(entry.mask[slot>>>3]&(1<<(slot&7)))!=0;
                    if(!required){if(at!=0||n!=0)throw new IOException("Compact region coverage changed");continue;}
                    if(at<REGION_HEADER||n<1||(long)at+n>region.length)throw new IOException("Invalid compact group extent");
                    if(offset==0){offset=at;length=n;}else if(at!=offset||n!=length)throw new IOException("Mixed compact group extents");slots.add(slot);
                }
                if(slots.isEmpty())throw new IOException("Missing compact group");
                var group=CompactCityCodec.decode(CompactStreams.read(new ByteArrayInputStream(region,offset,length),CompactCityCodec.MAX_GROUP_BYTES));
                if(!group.chunks().keySet().equals(slots))throw new IOException("Compact chunk coverage changed");
                var palette=new BlockState[group.palette().length];for(int i=0;i<palette.length;i++)palette[i]=state(group.palette()[i]);
                var chunks=new HashMap<Integer,TemplateChunk>();for(var chunk:group.chunks().values())chunks.put(chunk.slot(),new TemplateChunk(chunk,palette));
                return Map.copyOf(chunks);
            }
        }catch(IOException e){throw new IllegalStateException("Cannot decode compact municipal group "+key,e);}
        finally{GROUP_LOADS.release();}
    }
    private static InputStream resource(String name)throws IOException{
        var stream=FullLosAngelesMap.class.getResourceAsStream(ROOT+name);if(stream==null)throw new IOException("Missing bundled full-city scenery: "+name);return stream;
    }
    private static BlockState state(DataInputStream in)throws IOException{
        String name=in.readUTF();int count=in.readUnsignedByte();if(count>32||!name.startsWith("minecraft:")||FORBIDDEN.contains(name.substring(10)))throw new IOException("Invalid municipal block");
        var properties=new TreeMap<String,String>();for(int i=0;i<count;i++)properties.put(in.readUTF(),in.readUTF());String key=name+properties;
        var cached=STATES.get(key);if(cached!=null)return cached;var id=ResourceLocation.parse(name);
        if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Unsupported municipal block "+name);
        var block=BuiltInRegistries.BLOCK.get(id);var state=block.defaultBlockState();
        for(var entry:properties.entrySet()){
            var property=block.getStateDefinition().getProperty(entry.getKey());if(property==null)throw new IOException("Unsupported municipal property "+name+"/"+entry.getKey());
            state=set(state,property,entry.getValue());
        }
        if(STATES.size()<16384)STATES.putIfAbsent(key,state);return state;
    }
    private static BlockState state(CompactCityCodec.State encoded)throws IOException{
        String name=encoded.name();if(!name.startsWith("minecraft:")||FORBIDDEN.contains(name.substring(10)))throw new IOException("Invalid compact municipal block");
        var properties=encoded.properties();String key=name+properties;var existing=STATES.get(key);if(existing!=null)return existing;
        var id=ResourceLocation.parse(name);if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IOException("Unsupported compact municipal block "+name);
        var state=BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for(var property:properties.entrySet()){
            var definition=state.getBlock().getStateDefinition().getProperty(property.getKey());
            if(definition==null)throw new IOException("Unsupported compact block property "+name+"/"+property.getKey());state=set(state,definition,property.getValue());
        }
        if(STATES.size()<16384)STATES.putIfAbsent(key,state);return state;
    }
    private static <T extends Comparable<T>> BlockState set(BlockState state,Property<T> property,String value)throws IOException{
        var parsed=property.getValue(value);if(parsed.isEmpty())throw new IOException("Invalid municipal property value "+property.getName()+"="+value);return state.setValue(property,parsed.get());
    }
    private FullLosAngelesMap(){}
}
