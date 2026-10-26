import com.codex.blacklight.city.CompactCityCodec;
import com.codex.blacklight.util.CompactStreams;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Independent whole-city acceptance using the current runtime decoder; may follow completed immutable region proofs. */
public final class VerifyCompactMunicipalMap {
    static Path original,compact;static final int HEADER=8200;
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static int[][] header(byte[] data,int magic)throws IOException{
        var in=new DataInputStream(new ByteArrayInputStream(data));if(in.readInt()!=magic||in.readInt()!=1024)throw new IOException("Invalid municipal header");
        int[][] result=new int[1024][2];for(int slot=0;slot<1024;slot++){result[slot][0]=in.readInt();result[slot][1]=in.readInt();}
        return result;
    }
    static void extent(int[] entry,int length)throws IOException{if(entry[0]<HEADER||entry[1]<1||(long)entry[0]+entry[1]>length)throw new IOException("Invalid municipal extent");}
    static long[] verify(JsonObject row)throws Exception{
        String name=row.get("x").getAsInt()+"_"+row.get("z").getAsInt();Path proof=compact.resolve(name+".verified.json");long end=System.nanoTime()+TimeUnit.HOURS.toNanos(2);
        while(!Files.isRegularFile(proof)){if(System.nanoTime()>end)throw new IOException("Timed out waiting for completed compact region "+name);Thread.sleep(200);}
        var metadata=JsonParser.parseString(Files.readString(proof)).getAsJsonObject();
        byte[] old=Files.readAllBytes(original.resolve(name+".lar")),next=Files.readAllBytes(compact.resolve(name+".larc"));
        if(!sha(old).equals(row.get("sha256").getAsString())||!sha(old).equals(metadata.get("sourceSha256").getAsString())||!sha(next).equals(metadata.get("sha256").getAsString()))throw new IOException("Region identity changed "+name);
        int[][] source=header(old,0x4c415232),target=header(next,0x4c415234);long chunks=0,cells=0;int previousEnd=HEADER;
        for(int first=0;first<1024;first+=CompactCityCodec.GROUP_SIZE){
            var items=new ArrayList<CompactCityCodec.SourceChunk>();int offset=0,length=0;
            for(int slot=first;slot<first+CompactCityCodec.GROUP_SIZE;slot++){
                if(source[slot][0]==0){if(source[slot][1]!=0||target[slot][0]!=0||target[slot][1]!=0)throw new IOException("Region coverage changed "+name);continue;}
                extent(source[slot],old.length);extent(target[slot],next.length);
                if(offset==0){offset=target[slot][0];length=target[slot][1];}else if(offset!=target[slot][0]||length!=target[slot][1])throw new IOException("Group offsets changed "+name);
                try(var gzip=new GZIPInputStream(new ByteArrayInputStream(old,source[slot][0],source[slot][1]))){
                    byte[] raw=gzip.readNBytes(8*1024*1024+1);if(raw.length>8*1024*1024)throw new IOException("Oversized source chunk");
                    var chunk=CompactCityCodec.readLegacy(slot,raw);items.add(chunk);cells+=4096L*chunk.sections().length;
                }
            }
            if(items.isEmpty())continue;
            if(offset!=previousEnd)throw new IOException("Unreferenced compact payload "+name);previousEnd=offset+length;
            var decoded=CompactCityCodec.decode(CompactStreams.read(new ByteArrayInputStream(next,offset,length),CompactCityCodec.MAX_GROUP_BYTES));
            CompactCityCodec.verify(items,decoded);chunks+=items.size();
        }
        if(previousEnd!=next.length||chunks!=metadata.get("chunks").getAsLong()||cells!=metadata.get("verifiedCells").getAsLong())throw new IOException("Compact region accounting differs "+name);
        return new long[]{chunks,cells};
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("legacy compact report runtimeSourceFile");
        original=Path.of(args[0]);compact=Path.of(args[1]);byte[] indexBytes=Files.readAllBytes(original.resolve("index.json"));
        var index=JsonParser.parseString(new String(indexBytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var workers=Executors.newFixedThreadPool(2);var completed=new ExecutorCompletionService<long[]>(workers);int count=0;
        try{
            for(var entry:index.getAsJsonArray("regions")){var row=entry.getAsJsonObject();completed.submit(()->verify(row));count++;}
            long chunks=0,cells=0;
            for(int done=1;done<=count;done++){long[] result=completed.take().get();chunks+=result[0];cells+=result[1];if(done%100==0||done==count)System.out.println("RUNTIME VERIFIED regions="+done+"/"+count+" chunks="+chunks+" cells="+cells);}
            if(chunks!=index.get("nativeChunks").getAsLong())throw new IOException("Whole municipality coverage changed");
            var report=new JsonObject();report.addProperty("completed",true);report.addProperty("regions",count);report.addProperty("chunks",chunks);report.addProperty("verifiedCells",cells);
            report.addProperty("sourceIndexSHA256",sha(indexBytes));report.addProperty("runtimeDecoderSourceSHA256",sha(Files.readAllBytes(Path.of(args[3]))));
            report.addProperty("checks","all source region identities, every stored cell and air variant, absent sections, exact tops/bottoms, region/chunk coordinates and coverage, XZ CRC and output/memory bounds");
            Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("COMPLETE "+report);
        }finally{workers.shutdownNow();}
    }
}
