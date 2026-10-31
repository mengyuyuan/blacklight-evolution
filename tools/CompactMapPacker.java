import com.codex.blacklight.util.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.tukaani.xz.*;

/** Reproducible source-to-resource compression, with exhaustive raw map equality. */
public final class CompactMapPacker {
    private record Result(String name,byte[] packed,long originalBytes,long rawBytes){}
    private static List<Path> inputs(Path root)throws IOException{
        var paths=new ArrayList<Path>();
        for(String name:List.of("data/blacklight/worldmap","data/blacklight/city/futian","data/blacklight/city/los_angeles")){
            Path folder=root.resolve(name);if(!Files.isDirectory(folder))continue;
            try(var files=Files.walk(folder)){files.filter(Files::isRegularFile).filter(p->MapResources.supported(root.relativize(p).toString().replace('\\','/'))).forEach(paths::add);}
        }
        paths.sort(Comparator.comparing(p->root.relativize(p).toString()));return paths;
    }
    private static int deflated(byte[] raw)throws IOException{
        var bytes=new ByteArrayOutputStream();var deflater=new Deflater(6,true);
        try(var out=new DeflaterOutputStream(bytes,deflater)){out.write(raw);}finally{deflater.end();}return bytes.size();
    }
    private static Result encode(Path root,Path file)throws Exception{
        String name=root.relativize(file).toString().replace('\\','/');byte[] original=Files.readAllBytes(file);
        byte[] raw=MapResources.decode(new ByteArrayInputStream(original),false);
        int dictionary=65536;while(dictionary<raw.length&&dictionary<2*1024*1024)dictionary*=2;
        var options=new LZMA2Options(6);options.setDictSize(dictionary);var bytes=new ByteArrayOutputStream();
        try(var stream=new XZOutputStream(bytes,options,XZ.CHECK_CRC64)){stream.write(raw);}byte[] packed=bytes.toByteArray();
        if(!Arrays.equals(raw,MapResources.decode(new ByteArrayInputStream(packed),true)))throw new IOException("Map restoration differs: "+name);
        return new Result(name,deflated(packed)<deflated(original)?packed:null,original.length,raw.length);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--verify")){verify(Path.of(args[1]),Path.of(args[2]));return;}
        if(args.length!=2)throw new IllegalArgumentException("sourceResources outputResources");
        Path root=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath().normalize();
        if(output.startsWith(root)||root.startsWith(output))throw new IOException("Keep compressed maps separate from source resources");
        Files.createDirectories(output);var files=inputs(root);if(files.isEmpty())throw new IOException("No source maps");
        var selected=new TreeSet<String>();long old=0,next=0,raw=0;int count=0;
        var workers=Executors.newFixedThreadPool(2);
        try{
            var pending=new ArrayDeque<Future<Result>>();int cursor=0;
            while(cursor<files.size()||!pending.isEmpty()){
                while(cursor<files.size()&&pending.size()<6){Path file=files.get(cursor++);pending.add(workers.submit(()->encode(root,file)));}
                var result=pending.removeFirst().get();old+=result.originalBytes;raw+=result.rawBytes;
                if(result.packed!=null){
                    String name=MapResources.compactName(result.name);selected.add(result.name);Path target=output.resolve(name),temporary=target.resolveSibling(target.getFileName()+".tmp");
                    Files.createDirectories(target.getParent());Files.write(temporary,result.packed);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);next+=result.packed.length;
                }else next+=result.originalBytes;
                if(++count%2000==0)System.out.println("VERIFIED_MAPS "+count+" / "+files.size());
            }
        }
        finally {workers.shutdownNow();}
        try(var generated=Files.walk(output)){
            for(var path:generated.filter(Files::isRegularFile).toList()){
                String name=output.relativize(path).toString().replace('\\','/');
                if(name.endsWith(".xz")&&!selected.contains(name.substring(0,name.length()-3)+".gz"))Files.delete(path);
            }
        }
        var manifest=new StringBuilder("{\"schema\":1,\"originalBytes\":").append(old).append(",\"selectedBytes\":").append(next)
                .append(",\"decodedBytesVerified\":").append(raw).append(",\"filesVerified\":").append(count).append(",\"converted\":[");
        boolean first=true;for(var name:selected){if(!first)manifest.append(',');manifest.append('"').append(name).append('"');first=false;}
        manifest.append("]}");Files.writeString(output.resolveSibling("compactMapManifest.json"),manifest);
        System.out.println("VERIFIED exactMaps="+count+" xzFiles="+selected.size()+" originalBytes="+old+" selectedBytes="+next);
    }
    private static void verify(Path jar,Path root)throws Exception{
        int count=0,compact=0;long bytes=0;var expected=new HashSet<String>();
        try(var archive=new ZipFile(jar.toFile())){
            for(var file:inputs(root)){
                String name=root.relativize(file).toString().replace('\\','/'),encoded=MapResources.compactName(name);
                boolean xz=archive.getEntry(encoded)!=null;
                if(xz&&archive.getEntry(name)!=null)throw new IOException("Both map encodings shipped: "+name);
                String actual=xz?encoded:name;expected.add(actual);
                byte[] original=MapResources.decode(Files.newInputStream(file),false);
                byte[] restored=MapResources.read(name,path->{var entry=archive.getEntry(path);return entry==null?null:archive.getInputStream(entry);});
                if(!Arrays.equals(original,restored))throw new IOException("Packaged map differs: "+name);
                if(xz)compact++;count++;bytes+=original.length;
            }
            var entries=archive.entries();while(entries.hasMoreElements()){
                String name=entries.nextElement().getName();
                if((name.endsWith(".xz")||MapResources.supported(name))&&!expected.contains(name))throw new IOException("Unexpected compressed map: "+name);
            }
        }
        System.out.println("VERIFIED exactMaps="+count+" xzFiles="+compact+" decodedBytes="+bytes);
    }
}
