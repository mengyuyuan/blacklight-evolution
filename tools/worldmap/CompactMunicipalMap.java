import com.codex.blacklight.city.CompactCityCodec;
import com.codex.blacklight.util.CompactStreams;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import org.tukaani.xz.*;

/** Converts the frozen complete municipality; verifies every existing stored cell before checkpointing. */
public final class CompactMunicipalMap {
    static Path input,output;static String revision;static final int HEADER=8200;
    static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    static void save(Path path,JsonElement value)throws IOException{
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(value));
        Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
    }
    static JsonObject convert(JsonObject entry)throws Exception{
        int x=entry.get("x").getAsInt(),z=entry.get("z").getAsInt();String name=x+"_"+z;
        Path target=output.resolve(name+".larc"),proof=output.resolve(name+".verified.json");
        if(Files.isRegularFile(proof)&&Files.isRegularFile(target)){
            var old=JsonParser.parseString(Files.readString(proof)).getAsJsonObject();
            if(old.get("revision").getAsString().equals(revision)&&old.get("sourceSha256").getAsString().equals(entry.get("sha256").getAsString())
                    &&old.get("sha256").getAsString().equals(sha(Files.readAllBytes(target))))return old;
        }
        byte[] source=Files.readAllBytes(input.resolve(name+".lar"));
        if(source.length!=entry.get("bytes").getAsInt()||!sha(source).equals(entry.get("sha256").getAsString()))throw new IOException("Source changed: "+name);
        var read=new DataInputStream(new ByteArrayInputStream(source));if(read.readInt()!=0x4c415232||read.readInt()!=1024)throw new IOException("Invalid legacy region");
        int[] offsets=new int[1024],lengths=new int[1024];for(int i=0;i<1024;i++){offsets[i]=read.readInt();lengths[i]=read.readInt();}
        var result=new ByteArrayOutputStream();result.write(new byte[HEADER]);int count=0;long cells=0,maximumGroup=0;
        int[] nextOffsets=new int[1024],nextLengths=new int[1024];
        for(int first=0;first<1024;first+=CompactCityCodec.GROUP_SIZE){
            var chunks=new ArrayList<CompactCityCodec.SourceChunk>();
            for(int slot=first;slot<first+CompactCityCodec.GROUP_SIZE;slot++){
                if(offsets[slot]==0){if(lengths[slot]!=0)throw new IOException("Invalid absent source slot");continue;}
                if(offsets[slot]<HEADER||lengths[slot]<1||(long)offsets[slot]+lengths[slot]>source.length)throw new IOException("Invalid source extent");
                try(var inflated=new GZIPInputStream(new ByteArrayInputStream(source,offsets[slot],lengths[slot]))){
                    byte[] raw=inflated.readNBytes(8*1024*1024+1);if(raw.length>8*1024*1024)throw new IOException("Source chunk exceeds budget");
                    var chunk=CompactCityCodec.readLegacy(slot,raw);chunks.add(chunk);cells+=4096L*chunk.sections().length;
                }
            }
            if(chunks.isEmpty())continue;
            byte[] raw=CompactCityCodec.encode(chunks);maximumGroup=Math.max(maximumGroup,raw.length);
            var packed=new ByteArrayOutputStream();var options=new LZMA2Options(3);options.setDictSize(1024*1024);
            try(var compressed=new XZOutputStream(packed,options)){compressed.write(raw);}
            byte[] compressed=packed.toByteArray();
            byte[] restored=CompactStreams.read(new ByteArrayInputStream(compressed),CompactCityCodec.MAX_GROUP_BYTES);
            if(!Arrays.equals(restored,raw))throw new IOException("Compact stream is not lossless");
            CompactCityCodec.verify(chunks,CompactCityCodec.decode(restored));
            int offset=result.size();result.write(compressed);
            for(var chunk:chunks){nextOffsets[chunk.slot()]=offset;nextLengths[chunk.slot()]=compressed.length;count++;}
        }
        byte[] encoded=result.toByteArray();var header=new ByteArrayOutputStream();var out=new DataOutputStream(header);
        out.writeInt(0x4c415234);out.writeInt(1024);for(int i=0;i<1024;i++){out.writeInt(nextOffsets[i]);out.writeInt(nextLengths[i]);}
        System.arraycopy(header.toByteArray(),0,encoded,0,HEADER);
        Path temporary=target.resolveSibling(target.getFileName()+".tmp");Files.write(temporary,encoded);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
        var record=new JsonObject();record.addProperty("x",x);record.addProperty("z",z);record.addProperty("bytes",encoded.length);record.addProperty("sha256",sha(encoded));
        record.addProperty("sourceSha256",sha(source));record.addProperty("chunks",count);record.addProperty("verifiedCells",cells);record.addProperty("maximumGroupBytes",maximumGroup);record.addProperty("revision",revision);
        save(proof,record);return record;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<3||args.length>4)throw new IllegalArgumentException("input output codecRevision [limit]");
        input=Path.of(args[0]);output=Path.of(args[1]);revision=args[2];Files.createDirectories(output);int limit=args.length==4?Integer.parseInt(args[3]):Integer.MAX_VALUE;
        byte[] sourceIndex=Files.readAllBytes(input.resolve("index.json"));var index=JsonParser.parseString(new String(sourceIndex,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if(index.get("schema").getAsInt()!=2||!index.get("complete").getAsBoolean())throw new IOException("Incomplete baseline city");
        var regions=index.getAsJsonArray("regions");var workers=Executors.newFixedThreadPool(Math.min(4,Runtime.getRuntime().availableProcessors()));var completion=new ExecutorCompletionService<JsonObject>(workers);int submitted=0;
        try{
            for(var entry:regions){if(submitted==limit)break;var region=entry.getAsJsonObject();completion.submit(()->convert(region));submitted++;}
            var records=new ArrayList<JsonObject>();long bytes=0,cells=0,maximumGroup=0;
            for(int done=1;done<=submitted;done++){
                var row=completion.take().get();records.add(row);bytes+=row.get("bytes").getAsLong();cells+=row.get("verifiedCells").getAsLong();maximumGroup=Math.max(maximumGroup,row.get("maximumGroupBytes").getAsLong());
                if(done%100==0||done==submitted)System.out.println("VERIFIED regions="+done+"/"+submitted+" bytes="+bytes+" cells="+cells);
            }
            if(submitted!=regions.size()){System.out.println("SAMPLE ONLY; no complete index written");return;}
            long chunks=records.stream().mapToLong(r->r.get("chunks").getAsLong()).sum();if(chunks!=index.get("nativeChunks").getAsLong())throw new IOException("Municipal coverage changed");
            records.sort(Comparator.comparingInt((JsonObject r)->r.get("z").getAsInt()).thenComparingInt(r->r.get("x").getAsInt()));
            var archive=new JsonArray();records.forEach(archive::add);index.add("regions",archive);index.addProperty("schema",3);index.addProperty("chunkEncoding","COL-RLE1");
            index.addProperty("sourceIndexSHA256",sha(sourceIndex));index.addProperty("groupSize",CompactCityCodec.GROUP_SIZE);index.addProperty("verifiedCells",cells);
            index.addProperty("maximumGroupBytes",maximumGroup);index.addProperty("storageBytes",bytes);index.addProperty("revision",revision);
            Files.copy(input.resolve("coverage.bin"),output.resolve("coverage.bin"),StandardCopyOption.REPLACE_EXISTING);save(output.resolve("index.json"),index);
            System.out.println("COMPLETE regions="+records.size()+" chunks="+chunks+" bytes="+bytes+" verifiedCells="+cells);
        }finally{workers.shutdownNow();}
    }
}
