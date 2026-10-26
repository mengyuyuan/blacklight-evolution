import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Offline, lossless block-state extraction. No game bootstrap, entities or block entities. */
public final class ArnisArchivePacker {
    static final int MAX_Y=1536,HEADER=8+1024*8;
    record State(String name,SortedMap<String,String> properties) {}
    record Section(int y,List<State> palette,byte[] longs) {}
    static final Set<String> FORBIDDEN=Set.of("command_block","chain_command_block","repeating_command_block","structure_block","jigsaw","spawner");
    static final Set<String> NAMES=new java.util.concurrent.ConcurrentSkipListSet<>(),STATES=new java.util.concurrent.ConcurrentSkipListSet<>();
    static Set<String> vanilla;
    static final java.util.concurrent.atomic.LongAdder totalChunks=new java.util.concurrent.atomic.LongAdder(),totalSections=new java.util.concurrent.atomic.LongAdder();
    static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    static String utf(DataInputStream in)throws IOException {
        int n=in.readUnsignedShort();return new String(in.readNBytes(n),java.nio.charset.StandardCharsets.UTF_8);
    }
    static int bounded(int n,int max)throws IOException{if(n<0||n>max)throw new IOException("NBT length out of bounds: "+n);return n;}
    static void skip(DataInputStream in,int type)throws IOException{
        switch(type){
            case 1->in.skipNBytes(1);case 2->in.skipNBytes(2);case 3,5->in.skipNBytes(4);case 4,6->in.skipNBytes(8);
            case 7->in.skipNBytes(bounded(in.readInt(),64*1024*1024));
            case 8->in.skipNBytes(in.readUnsignedShort());
            case 9->{int item=in.readUnsignedByte(),n=bounded(in.readInt(),1_000_000);for(int i=0;i<n;i++)skip(in,item);}
            case 10->{for(int t;(t=in.readUnsignedByte())!=0;){utf(in);skip(in,t);}}
            case 11->in.skipNBytes((long)bounded(in.readInt(),16*1024*1024)*4);
            case 12->in.skipNBytes((long)bounded(in.readInt(),8*1024*1024)*8);
            default->throw new IOException("Invalid NBT type "+type);
        }
    }
    static State state(DataInputStream in)throws IOException{
        String name=null;var properties=new TreeMap<String,String>();
        for(int t;(t=in.readUnsignedByte())!=0;){String field=utf(in);
            if(t==8&&field.equals("Name"))name=utf(in);
            else if(t==10&&field.equals("Properties")){
                for(int p;(p=in.readUnsignedByte())!=0;){String k=utf(in);if(p!=8)throw new IOException("Non-string block property");properties.put(k,utf(in));}
            }else skip(in,t);
        }
        if(name==null||!name.startsWith("minecraft:")||FORBIDDEN.contains(name.substring(10)))throw new IOException("Unexpected block: "+name);
        if(!vanilla.contains(name))throw new IOException("Unsupported Minecraft 1.21.1 block: "+name);
        if(properties.size()>32)throw new IOException("Too many block properties");
        NAMES.add(name);STATES.add(name+properties);return new State(name,properties);
    }
    static Object[] blockStates(DataInputStream in)throws IOException{
        List<State> palette=null;byte[] longs=new byte[0];
        for(int t;(t=in.readUnsignedByte())!=0;){String field=utf(in);
            if(t==9&&field.equals("palette")){
                int type=in.readUnsignedByte(),n=bounded(in.readInt(),4096);if(type!=10||n==0)throw new IOException("Invalid block palette");
                palette=new ArrayList<>(n);for(int i=0;i<n;i++)palette.add(state(in));
            }else if(t==12&&field.equals("data")){int n=bounded(in.readInt(),1024);longs=in.readNBytes(n*8);if(longs.length!=n*8)throw new EOFException();}
            else skip(in,t);
        }
        return new Object[]{palette,longs};
    }
    @SuppressWarnings("unchecked") static Section section(DataInputStream in)throws IOException{
        int y=Integer.MIN_VALUE;List<State> palette=null;byte[] longs=new byte[0];
        for(int t;(t=in.readUnsignedByte())!=0;){String field=utf(in);
            if(field.equals("Y")&&t==1)y=in.readByte();
            else if(field.equals("Y")&&t==3)y=in.readInt();
            else if(field.equals("block_states")&&t==10){var result=blockStates(in);palette=(List<State>)result[0];longs=(byte[])result[1];}
            else skip(in,t);
        }
        if(palette==null)return null;
        if(y>=MAX_Y/16&&!palette.stream().allMatch(s->s.name.equals("minecraft:air")||s.name.equals("minecraft:cave_air")||s.name.equals("minecraft:void_air")))throw new IOException("Arnis scenery exceeds the dimension ceiling at section "+y);
        if(y<0||y>=MAX_Y/16)return null;
        if(palette.stream().allMatch(s->s.name.equals("minecraft:air")||s.name.equals("minecraft:cave_air")||s.name.equals("minecraft:void_air")))return null;
        int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1)),per=64/bits;
        int expected=palette.size()==1?0:(4096+per-1)/per;
        if(longs.length!=expected*8)throw new IOException("Invalid block data length: "+longs.length+" expected "+expected*8);
        return new Section(y,palette,longs);
    }
    static List<Section> chunk(byte[] compressed,int compression)throws IOException{
        InputStream raw=new ByteArrayInputStream(compressed);
        InputStream inflated=compression==2?new InflaterInputStream(raw):compression==1?new GZIPInputStream(raw):compression==3?raw:null;
        if(inflated==null)throw new IOException("Unsupported Anvil compression "+compression);
        var result=new ArrayList<Section>();
        try(var in=new DataInputStream(new BufferedInputStream(inflated))){
            if(in.readUnsignedByte()!=10)throw new IOException("Invalid chunk root");utf(in);
            for(int t;(t=in.readUnsignedByte())!=0;){String name=utf(in);
                if(t==9&&name.equals("sections")){
                    int type=in.readUnsignedByte(),n=bounded(in.readInt(),512);if(type!=10)throw new IOException("Invalid sections list");
                    for(int i=0;i<n;i++){var s=section(in);if(s!=null)result.add(s);}
                }else skip(in,t);
            }
        }
        result.sort(Comparator.comparingInt(Section::y));int prior=-1;
        for(var s:result){if(s.y<=prior)throw new IOException("Duplicate section Y");prior=s.y;}
        return result;
    }
    static byte[] encode(List<Section> sections)throws IOException{
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){
            int[] tops=new int[256],bottoms=new int[256];Arrays.fill(bottoms,MAX_Y);
            for(var s:sections){
                boolean[] air=new boolean[s.palette.size()];for(int i=0;i<air.length;i++)air[i]=Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air").contains(s.palette.get(i).name);
                if(air.length==1){if(!air[0])for(int col=0;col<256;col++){tops[col]=Math.max(tops[col],s.y*16+15);bottoms[col]=Math.min(bottoms[col],s.y*16);}continue;}
                int bits=Math.max(4,32-Integer.numberOfLeadingZeros(air.length-1)),per=64/bits;long[] packed=new long[s.longs.length/8];
                try(var data=new DataInputStream(new ByteArrayInputStream(s.longs))){for(int i=0;i<packed.length;i++)packed[i]=data.readLong();}
                for(int pos=0;pos<4096;pos++){
                    int id=(int)((packed[pos/per]>>>((pos%per)*bits))&((1L<<bits)-1));if(id>=air.length)throw new IOException("Source palette index out of range");
                    if(air[id])continue;int col=pos&255,y=s.y*16+(pos>>>8);tops[col]=Math.max(tops[col],y);bottoms[col]=Math.min(bottoms[col],y);
                }
            }
            for(int col=0;col<256;col++)if(bottoms[col]==MAX_Y)throw new IOException("Arnis left an empty required municipal column");
            out.writeInt(0x4c414333);out.writeShort(sections.size());for(int value:tops)out.writeShort(value);for(int value:bottoms)out.writeShort(value);
            for(var s:sections){out.writeByte(s.y);out.writeShort(s.palette.size());
                for(var state:s.palette){out.writeUTF(state.name);out.writeByte(state.properties.size());for(var p:state.properties.entrySet()){out.writeUTF(p.getKey());out.writeUTF(p.getValue());}}
                out.writeShort(s.longs.length/8);out.write(s.longs);
            }
        }
        return bytes.toByteArray();
    }
    static int pack(Path input,Path output,byte[] mask)throws IOException{
        if(!Files.isRegularFile(input))throw new IOException("Missing required native region: "+input);
        var temporary=output.resolveSibling(output.getFileName()+".tmp");Files.createDirectories(output.getParent());int chunks=0;
        try(var source=new RandomAccessFile(input.toFile(),"r");var out=new RandomAccessFile(temporary.toFile(),"rw")){
            out.setLength(0);out.setLength(HEADER);out.seek(0);out.writeInt(0x4c415232);out.writeInt(1024);long end=HEADER;
            for(int slot=0;slot<1024;slot++){
                if((mask[slot>>>3]&(1<<(slot&7)))==0)continue;
                source.seek(slot*4L);int location=source.readInt();int offset=(location>>>8)*4096;
                if(offset==0)throw new IOException("Missing required native chunk in "+input+" slot "+slot);
                source.seek(offset);int size=bounded(source.readInt(),32*1024*1024),compression=source.readUnsignedByte();
                if(size<=1)throw new IOException("Empty Anvil chunk");byte[] compressed=new byte[size-1];source.readFully(compressed);
                var sections=chunk(compressed,compression);var data=encode(sections);
                if(end+data.length>Integer.MAX_VALUE)throw new IOException("Region archive too large");
                out.seek(end);out.write(data);out.seek(8+slot*8L);out.writeInt((int)end);out.writeInt(data.length);end+=data.length;
                chunks++;totalChunks.increment();totalSections.add(sections.size());
            }
            out.setLength(end);
        }
        Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);return chunks;
    }
    static Map<Long,byte[]> masks(Path path)throws IOException{
        var result=new LinkedHashMap<Long,byte[]>();
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=0x4c41434d||in.readInt()!=1)throw new IOException("Invalid coverage index");int n=bounded(in.readInt(),100_000);
            for(int i=0;i<n;i++){int x=in.readInt(),z=in.readInt();byte[] b=in.readNBytes(128);if(b.length!=128)throw new EOFException();result.put(key(x,z),b);}
            if(in.read()!=-1)throw new IOException("Trailing coverage data");
        }
        return result;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=8)throw new IllegalArgumentException("world coverage.bin output vanilla.jar minRx minRz maxRx maxRz");
        Path world=Path.of(args[0]),output=Path.of(args[2]);vanilla=new HashSet<>();
        try(var zip=new ZipFile(args[3])){zip.stream().map(ZipEntry::getName).filter(n->n.startsWith("assets/minecraft/blockstates/")&&n.endsWith(".json")).forEach(n->vanilla.add("minecraft:"+n.substring("assets/minecraft/blockstates/".length(),n.length()-5)));}
        int minX=Integer.parseInt(args[4]),minZ=Integer.parseInt(args[5]),maxX=Integer.parseInt(args[6]),maxZ=Integer.parseInt(args[7]);int regions=0;
        var selected=masks(Path.of(args[1])).entrySet().stream().filter(e->{int x=(int)(e.getKey()>>32),z=(int)(long)e.getKey();return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}).toList();
        selected.parallelStream().forEach(e->{
            int x=(int)(e.getKey()>>32),z=(int)(long)e.getKey();
            try{
                int count=pack(world.resolve("region/r."+x+"."+z+".mca"),output.resolve(x+"_"+z+".lar"),e.getValue());
                System.out.println("PACKED "+x+" "+z+" "+count);System.out.flush();
            }catch(IOException error){throw new UncheckedIOException(error);}
        });
        regions=selected.size();
        Files.createDirectories(output);Path report=output.resolve("palette-"+minX+"_"+minZ+".txt");Files.write(report,STATES);
        System.out.println("COMPLETE regions="+regions+" chunks="+totalChunks+" sections="+totalSections+" blockNames="+NAMES.size()+" blockStates="+STATES.size());
    }
}
