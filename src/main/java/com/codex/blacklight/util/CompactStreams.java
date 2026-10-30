package com.codex.blacklight.util;

import java.io.*;
import org.tukaani.xz.SingleXZInputStream;

/** No native libraries; both decoder dictionary and uncompressed output are bounded. */
public final class CompactStreams {
    public static final int MEMORY_LIMIT_KIB=4096;
    private CompactStreams(){}
    public static byte[] read(InputStream source,int maximumBytes)throws IOException{
        if(maximumBytes<0||maximumBytes==Integer.MAX_VALUE)throw new IOException("Invalid compact resource size limit");
        try(var buffered=new BufferedInputStream(source);var input=new SingleXZInputStream(buffered,MEMORY_LIMIT_KIB)){
            byte[] result=input.readNBytes(maximumBytes+1);
            if(result.length>maximumBytes)throw new IOException("Compact resource exceeds output budget");
            if(buffered.read()!=-1)throw new IOException("Trailing compact resource bytes");
            return result;
        }
    }
}
