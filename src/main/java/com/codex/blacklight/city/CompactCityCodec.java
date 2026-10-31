package com.codex.blacklight.city;

import java.io.*;
import java.util.*;

/** Engine-independent lossless storage for existing municipal chunks. No generation rules are changed. */
public final class CompactCityCodec {
    public static final int HEIGHT=1536,GROUP_SIZE=32,MAX_GROUP_BYTES=48*1024*1024;
    private static final int MAGIC=0x43475031; // CGP1
    private static final Set<String> AIR=Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air");
    public record State(String name,Map<String,String> properties){
        public State {properties=Collections.unmodifiableMap(new TreeMap<>(properties));}
    }
    public record SourceSection(int y,State[] palette,long[] data){
        int paletteIndex(int position){
            if(palette.length==1)return 0;
            int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.length-1)),per=64/bits;
            int index=(int)((data[position/per]>>>((position%per)*bits))&((1L<<bits)-1));
            if(index>=palette.length)throw new IllegalArgumentException("Source palette index out of bounds");return index;
        }
    }
    public record SourceChunk(int slot,short[] tops,short[] bottoms,SourceSection[] sections){}
    public record Chunk(int slot,short[] tops,short[] bottoms,short[][] cells){}
    public record Group(State[] palette,Map<Integer,Chunk> chunks){}
    private CompactCityCodec(){}
    public static SourceChunk readLegacy(int slot,byte[] bytes)throws IOException{
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(slot<0||slot>=1024||in.readInt()!=0x4c414333)throw new IOException("Invalid legacy city chunk");
            int count=in.readUnsignedShort();if(count<1||count>HEIGHT/16)throw new IOException("Invalid legacy section count");
            short[] tops=new short[256],bottoms=new short[256];for(int i=0;i<256;i++)tops[i]=in.readShort();for(int i=0;i<256;i++)bottoms[i]=in.readShort();
            var sections=new SourceSection[count];int previous=-1;
            for(int i=0;i<count;i++){
                int y=in.readUnsignedByte(),size=in.readUnsignedShort();if(y<=previous||y>=HEIGHT/16||size<1||size>4096)throw new IOException("Invalid legacy section");previous=y;
                var palette=new State[size];for(int p=0;p<size;p++)palette[p]=readState(in);
                int n=in.readUnsignedShort(),bits=Math.max(4,32-Integer.numberOfLeadingZeros(size-1)),expected=size==1?0:(4096+64/bits-1)/(64/bits);
                if(n!=expected)throw new IOException("Invalid legacy packed cells");long[] data=new long[n];for(int v=0;v<n;v++)data[v]=in.readLong();
                sections[i]=new SourceSection(y,palette,data);
            }
            if(in.available()!=0)throw new IOException("Trailing legacy city data");return new SourceChunk(slot,tops,bottoms,sections);
        }
    }
    public static State readState(DataInputStream in)throws IOException{
        String name=in.readUTF();int count=in.readUnsignedByte();if(count>32||!name.matches("[a-z0-9_.-]+:[a-z0-9/_.-]+"))throw new IOException("Invalid compact block state");
        var properties=new TreeMap<String,String>();for(int i=0;i<count;i++)if(properties.put(in.readUTF(),in.readUTF())!=null)throw new IOException("Duplicate block property");
        return new State(name,properties);
    }
    private static void writeState(DataOutputStream out,State state)throws IOException{
        out.writeUTF(state.name);out.writeByte(state.properties.size());for(var property:state.properties.entrySet()){out.writeUTF(property.getKey());out.writeUTF(property.getValue());}
    }
    public static byte[] encode(List<SourceChunk> source)throws IOException{
        if(source.isEmpty()||source.size()>GROUP_SIZE)throw new IOException("Invalid compact group size");
        var states=new LinkedHashMap<State,Integer>();var slots=new HashSet<Integer>();int group=source.get(0).slot/GROUP_SIZE;
        for(var chunk:source){
            if(chunk.slot<0||chunk.slot>=1024||chunk.slot/GROUP_SIZE!=group||!slots.add(chunk.slot))throw new IOException("Mismatched compact group slots");
            for(var section:chunk.sections)for(var state:section.palette)states.computeIfAbsent(state,s->states.size());
        }
        if(states.size()>4096)throw new IOException("Oversized compact group palette");
        var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);out.writeInt(MAGIC);out.writeShort(states.size());
        for(var state:states.keySet())writeState(out,state);out.writeByte(source.size());
        for(var chunk:source){
            out.writeShort(chunk.slot);out.writeByte(chunk.sections.length);for(var section:chunk.sections)out.writeByte(section.y);
            var remaps=new int[chunk.sections.length][];
            for(int s=0;s<remaps.length;s++){var section=chunk.sections[s];remaps[s]=new int[section.palette.length];for(int i=0;i<remaps[s].length;i++)remaps[s][i]=states.get(section.palette[i]);}
            for(int first=0;first<chunk.sections.length;first+=8){
                var lengths=new ByteArrayOutputStream();var ids=new ByteArrayOutputStream();var values=new DataOutputStream(ids);
                int end=Math.min(first+8,chunk.sections.length),runs=0;
                for(int column=0;column<256;column++){
                    int previous=-1,length=0;
                    for(int s=first;s<end;s++){
                        var section=chunk.sections[s];
                        for(int y=0;y<16;){
                            int id=remaps[s][section.paletteIndex(y*256+column)],step=section.palette.length==1?16:1;
                            if(previous!=id&&length>0){lengths.write(length);values.writeShort(previous);runs++;length=0;}
                            previous=id;length+=step;y+=step;
                        }
                    }
                    if(length>0){lengths.write(length);values.writeShort(previous);runs++;}
                }
                if(runs>32768)throw new IOException("Too many column runs");out.writeShort(runs);lengths.writeTo(out);ids.writeTo(out);
            }
            if(buffer.size()>MAX_GROUP_BYTES)throw new IOException("Compact group exceeds memory budget");
        }
        out.flush();return buffer.toByteArray();
    }
    public static Group decode(byte[] data)throws IOException{
        if(data.length>MAX_GROUP_BYTES)throw new IOException("Compact group exceeds memory budget");
        try(var in=new DataInputStream(new ByteArrayInputStream(data))){
            if(in.readInt()!=MAGIC)throw new IOException("Unsupported compact group");
            int size=in.readUnsignedShort();if(size<1||size>4096)throw new IOException("Invalid compact group palette");
            State[] palette=new State[size];boolean[] air=new boolean[size];for(int i=0;i<size;i++){palette[i]=readState(in);air[i]=AIR.contains(palette[i].name);}
            int count=in.readUnsignedByte();if(count<1||count>GROUP_SIZE)throw new IOException("Invalid compact group count");
            var chunks=new LinkedHashMap<Integer,Chunk>();int group=-1;
            for(int c=0;c<count;c++){
                int slot=in.readUnsignedShort(),n=in.readUnsignedByte();if(slot>=1024||n<1||n>HEIGHT/16||chunks.containsKey(slot))throw new IOException("Invalid compact chunk");
                if(group<0)group=slot/GROUP_SIZE;else if(slot/GROUP_SIZE!=group)throw new IOException("Mixed compact group coordinates");
                int[] ys=new int[n];short[][] cells=new short[HEIGHT/16][];int previous=-1;
                for(int s=0;s<n;s++){
                    int y=in.readUnsignedByte();if(y<=previous||y>=HEIGHT/16)throw new IOException("Invalid compact section Y");previous=y;ys[s]=y;cells[y]=new short[]{-1};
                }
                short[] tops=new short[256],bottoms=new short[256];Arrays.fill(bottoms,(short)HEIGHT);
                for(int first=0;first<n;first+=8){
                    int runs=in.readUnsignedShort(),sections=Math.min(8,n-first),height=sections*16;
                    if(runs<256||runs>height*256)throw new IOException("Invalid column run count");
                    byte[] lengths=in.readNBytes(runs);if(lengths.length!=runs)throw new EOFException();
                    int column=0,at=0;
                    for(int r=0;r<runs;r++){
                        int length=lengths[r]&255,id=in.readUnsignedShort();
                        if(column>=256||length==0||length>height-at||id>=size)throw new IOException("Invalid compact column run");
                        while(length>0){
                            int section=ys[first+(at>>>4)],offset=at&15,part=Math.min(length,16-offset);
                            short[] values=cells[section];
                            if(values.length==1&&values[0]==-1)values[0]=(short)id;
                            if(values.length==1&&values[0]!=(short)id){short prior=values[0];values=new short[4096];Arrays.fill(values,prior);cells[section]=values;}
                            if(values.length!=1)for(int y=offset;y<offset+part;y++)values[y*256+column]=(short)id;
                            if(!air[id]){tops[column]=(short)Math.max(tops[column],section*16+offset+part-1);bottoms[column]=(short)Math.min(bottoms[column],section*16+offset);}
                            at+=part;length-=part;
                        }
                        if(at==height){column++;at=0;}
                    }
                    if(column!=256||at!=0)throw new IOException("Incomplete compact columns");
                }
                for(int col=0;col<256;col++)if(bottoms[col]>=HEIGHT||tops[col]<bottoms[col])throw new IOException("Empty municipal column");
                chunks.put(slot,new Chunk(slot,tops,bottoms,cells));
            }
            if(in.available()!=0)throw new IOException("Trailing compact group bytes");return new Group(palette,Map.copyOf(chunks));
        }
    }
    /** Exhaustive source-to-result equality, including heights and all explicit/absent sections. */
    public static void verify(List<SourceChunk> source,Group decoded)throws IOException{
        if(source.size()!=decoded.chunks.size())throw new IOException("Compact coverage changed");
        var ids=new HashMap<State,Integer>();for(int i=0;i<decoded.palette.length;i++)if(ids.put(decoded.palette[i],i)!=null)throw new IOException("Duplicate compact block state");
        for(var old:source){
            Chunk next=decoded.chunks.get(old.slot);
            if(next==null||!Arrays.equals(old.tops,next.tops)||!Arrays.equals(old.bottoms,next.bottoms))throw new IOException("Compact city height changed at slot "+old.slot);
            var stored=new HashSet<Integer>();
            for(var section:old.sections){
                stored.add(section.y);short[] cells=next.cells[section.y];if(cells==null)throw new IOException("Lost compact section");
                int[] expected=new int[section.palette.length];for(int p=0;p<expected.length;p++){var id=ids.get(section.palette[p]);if(id==null)throw new IOException("Lost compact palette value");expected[p]=id;}
                for(int at=0;at<4096;at++)if(expected[section.paletteIndex(at)]!=Short.toUnsignedInt(cells[cells.length==1?0:at]))throw new IOException("Compact block changed at slot "+old.slot+" section "+section.y+" cell "+at);
            }
            for(int y=0;y<HEIGHT/16;y++)if(next.cells[y]!=null&&!stored.contains(y))throw new IOException("Added compact section");
        }
    }
}
