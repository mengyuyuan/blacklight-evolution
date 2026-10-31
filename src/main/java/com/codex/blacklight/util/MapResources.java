package com.codex.blacklight.util;

import java.io.*;
import java.util.zip.GZIPInputStream;

/** Logical map names stay stable while build-time packing selects a smaller lossless encoding. */
public final class MapResources {
    public static final int MAXIMUM_BYTES=8*1024*1024;
    @FunctionalInterface public interface Opener {InputStream open(String name)throws IOException;}
    private MapResources(){}
    public static boolean supported(String name){
        String n=name.startsWith("/")?name.substring(1):name;
        return !n.contains("..")&&n.endsWith(".gz")&&(n.startsWith("data/blacklight/worldmap/")
                ||n.startsWith("data/blacklight/city/futian/")||n.startsWith("data/blacklight/city/los_angeles/"));
    }
    public static String compactName(String name){
        if(!supported(name))throw new IllegalArgumentException("Invalid logical map resource");
        return name.substring(0,name.length()-3)+".xz";
    }
    public static InputStream open(Class<?> owner,String name)throws IOException{
        return new ByteArrayInputStream(read(name,owner::getResourceAsStream));
    }
    public static byte[] read(String name,Opener resources)throws IOException{
        if(!supported(name))throw new IOException("Invalid logical map resource: "+name);
        var compact=resources.open(compactName(name));
        if(compact!=null)return decode(compact,true);
        var original=resources.open(name);if(original==null)throw new FileNotFoundException("Missing map resource: "+name);
        return decode(original,false);
    }
    public static byte[] decode(InputStream source,boolean xz)throws IOException{
        if(xz)return CompactStreams.read(source,MAXIMUM_BYTES);
        try(var input=new GZIPInputStream(source)){
            byte[] bytes=input.readNBytes(MAXIMUM_BYTES+1);
            if(bytes.length>MAXIMUM_BYTES||input.read()!=-1)throw new IOException("Map resource exceeds output budget");
            return bytes;
        }
    }
}
