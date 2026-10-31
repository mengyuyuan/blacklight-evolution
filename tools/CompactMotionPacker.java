import com.codex.blacklight.util.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.tukaani.xz.*;

/** Exact-byte motion profile packing. Original files and provenance remain unchanged in the source profile. */
public final class CompactMotionPacker {
    static final Set<String> DATA=Set.of("combat-motion-tuning.data","consume-placement.data","consume-receiver-events.data","receiver-poses.data","hunter-consume.data","hammer-scales.data","weapon-attack-scales.data","armor-growth.data","camera-profile.data","camera-events.data","camera-film.data","health-hud.data","evolved-poses.data");
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static List<Path> profile(Path source)throws IOException{
        try(var entries=Files.list(source)){return entries.filter(p->Files.isRegularFile(p)&&(p.getFileName().toString().endsWith(".json")||DATA.contains(p.getFileName().toString()))).sorted().toList();}
    }
    static void verify(Path archive,Path source)throws Exception{
        var files=profile(source);var expected=new TreeSet<String>();for(var file:files)expected.add(file.getFileName().toString());
        try(var zip=new ZipFile(archive.toFile())){
            var logical=new TreeSet<String>();
            for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory()&&entry.getName().startsWith("blacklight/bundled_motions/")){
                String name=entry.getName().substring("blacklight/bundled_motions/".length());if(name.equals("compact-index.json"))continue;
                if(name.endsWith(".jz"))name=name.substring(0,name.length()-3);
                if(!name.matches("[a-z0-9_-]+\\.(json|data)")||!logical.add(name))throw new IOException("Duplicate or invalid logical motion "+name);
            }
            if(!logical.equals(expected))throw new IOException("Logical motion inventory changed");
            for(var file:files){
                String name=file.getFileName().toString();byte[] raw=Files.readAllBytes(file);var encoded=zip.getEntry("blacklight/bundled_motions/"+name+".jz");byte[] decoded;
                if(encoded!=null){try(var input=zip.getInputStream(encoded)){decoded=CompactJson.decode(CompactStreams.read(input,raw.length*8+64),raw.length);}}
                else{var plain=zip.getEntry("blacklight/bundled_motions/"+name);if(plain==null)throw new IOException("Missing motion "+name);try(var input=zip.getInputStream(plain)){decoded=input.readAllBytes();}}
                if(!Arrays.equals(raw,decoded))throw new IOException("Bundled motion bytes changed: "+name);
            }
        }
        System.out.println("VERIFIED exactBundledOriginals="+files.size());
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--verify")){verify(Path.of(args[1]),Path.of(args[2]));return;}
        if(args.length!=2)throw new IllegalArgumentException("profile output");Path source=Path.of(args[0]).toRealPath(),out=Path.of(args[1]).toAbsolutePath().normalize();
        if(out.startsWith(source)||source.startsWith(out))throw new IOException("Keep generated profiles separate from originals");Files.createDirectories(out);
        var files=profile(source);
        if(files.isEmpty())throw new IOException("Empty motion profile");var keep=new HashSet<String>();long oldBytes=0,newBytes=0;var manifest=new StringBuilder("{\n  \"schema\": 1,\n  \"files\": {\n");int count=0;
        for(var file:files){
            String name=file.getFileName().toString();if(!name.matches("[a-z0-9_-]+\\.(json|data)")||Files.isSymbolicLink(file))throw new IOException("Invalid source motion "+name);
            byte[] raw=Files.readAllBytes(file);if(raw.length>8*1024*1024)throw new IOException("Oversized motion "+name);
            byte[] transformed=CompactJson.encode(raw);var bytes=new ByteArrayOutputStream();var options=new LZMA2Options(6);options.setDictSize(2*1024*1024);
            try(var packed=new XZOutputStream(bytes,options)){packed.write(transformed);}byte[] packed=bytes.toByteArray();
            byte[] restored=CompactJson.decode(CompactStreams.read(new ByteArrayInputStream(packed),raw.length*8+64),raw.length);
            if(!Arrays.equals(restored,raw))throw new IOException("Motion bytes changed: "+name);
            var compressed=new ByteArrayOutputStream();var deflater=new Deflater(6,true);try(var zip=new DeflaterOutputStream(compressed,deflater)){zip.write(raw);}finally{deflater.end();}
            boolean smaller=packed.length<compressed.size();String target=name+(smaller?".jz":"");byte[] value=smaller?packed:raw;
            Path temporary=out.resolve(target+".tmp");Files.write(temporary,value);Files.move(temporary,out.resolve(target),StandardCopyOption.REPLACE_EXISTING);keep.add(target);
            if(count++>0)manifest.append(",\n");manifest.append("    \"").append(name).append("\": {\"bytes\": ").append(raw.length).append(", \"sha256\": \"").append(sha(raw)).append("\", \"resource\": \"").append(target).append("\"}");
            oldBytes+=compressed.size();newBytes+=smaller?packed.length:compressed.size();
        }
        manifest.append("\n  }\n}\n");Files.writeString(out.resolve("compact-index.json"),manifest.toString());keep.add("compact-index.json");
        try(var previous=Files.list(out)){
            for(var file:previous.toList())if(!keep.contains(file.getFileName().toString())){
                String name=file.getFileName().toString();if(Files.isRegularFile(file)&&name.matches("[a-z0-9_-]+\\.(json|data)(\\.jz)?(\\.tmp)?"))Files.delete(file);
            }
        }
        System.out.println("VERIFIED exactOriginals="+count+" oldZIPBytes="+oldBytes+" compactBytes="+newBytes);
    }
}
