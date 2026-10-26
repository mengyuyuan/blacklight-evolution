package com.codex.blacklight.util;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CompactStreamsTest {
    byte[] packed(byte[] data,int dictionary)throws Exception{
        var bytes=new ByteArrayOutputStream();var options=new LZMA2Options(3);options.setDictSize(dictionary);
        try(var out=new XZOutputStream(bytes,options)){out.write(data);}return bytes.toByteArray();
    }
    @Test void ChecksOutputBudgetChecksumAndTrailingBytes()throws Exception{
        byte[] raw=new byte[8192];new Random(17).nextBytes(raw);byte[] value=packed(raw,1024*1024);
        assertArrayEquals(raw,CompactStreams.read(new ByteArrayInputStream(value),raw.length));
        assertThrows(IOException.class,()->CompactStreams.read(new ByteArrayInputStream(value),raw.length-1));
        byte[] trailing=Arrays.copyOf(value,value.length+1);assertThrows(IOException.class,()->CompactStreams.read(new ByteArrayInputStream(trailing),raw.length));
        value[value.length/2]^=64;assertThrows(IOException.class,()->CompactStreams.read(new ByteArrayInputStream(value),raw.length));
    }
    @Test void RejectsDictionaryLargerThanTheRuntimeMemoryBudget()throws Exception{
        byte[] value=packed(new byte[10000],8*1024*1024);
        assertThrows(IOException.class,()->CompactStreams.read(new ByteArrayInputStream(value),10000));
    }
}
