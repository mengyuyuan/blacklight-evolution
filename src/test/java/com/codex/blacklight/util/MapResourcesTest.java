package com.codex.blacklight.util;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.*;
import static org.junit.jupiter.api.Assertions.*;

class MapResourcesTest {
    static final String NAME="/data/blacklight/worldmap/shenzhen/0_0.json.gz";
    byte[] gzip(byte[] raw)throws Exception{var bytes=new ByteArrayOutputStream();try(var out=new GZIPOutputStream(bytes)){out.write(raw);}return bytes.toByteArray();}
    byte[] xz(byte[] raw)throws Exception{var bytes=new ByteArrayOutputStream();var options=new LZMA2Options(3);options.setDictSize(65536);try(var out=new XZOutputStream(bytes,options)){out.write(raw);}return bytes.toByteArray();}
    @Test void StableLogicalNamesLoadEitherEncodingExactly()throws Exception{
        byte[] raw="{\"coordinates\":[-0.0,13129657.1],\"e\":[-60,1350]}".getBytes(java.nio.charset.StandardCharsets.UTF_8),gz=gzip(raw),compact=xz(raw);
        assertArrayEquals(raw,MapResources.read(NAME,n->n.equals(NAME)?new ByteArrayInputStream(gz):null));
        assertArrayEquals(raw,MapResources.read(NAME,n->n.equals(MapResources.compactName(NAME))?new ByteArrayInputStream(compact):null));
    }
    @Test void CorruptPreferredEncodingDoesNotSilentlyUseADifferentSource()throws Exception{
        byte[] raw={1,2,3},gz=gzip(raw),bad=xz(raw);bad[bad.length/2]^=64;
        assertThrows(IOException.class,()->MapResources.read(NAME,n->new ByteArrayInputStream(n.endsWith(".xz")?bad:gz)));
    }
    @Test void CorruptGzipAndMissingResourcesFail()throws Exception{
        byte[] bad=gzip(new byte[]{1,2,3});bad[bad.length-5]^=16;
        assertThrows(IOException.class,()->MapResources.decode(new ByteArrayInputStream(bad),false));
        assertThrows(FileNotFoundException.class,()->MapResources.read(NAME,n->null));
    }
    @Test void MapsCannotInflatePastTheSharedOutputBudget()throws Exception{
        byte[] oversized=gzip(new byte[MapResources.MAXIMUM_BYTES+1]);
        assertThrows(IOException.class,()->MapResources.decode(new ByteArrayInputStream(oversized),false));
    }
    @Test void OnlyKnownMapRootsCanSelectAlternateResources(){
        assertFalse(MapResources.supported("/assets/secret.gz"));assertFalse(MapResources.supported("/data/blacklight/worldmap/../other.gz"));
        assertThrows(IllegalArgumentException.class,()->MapResources.compactName("/assets/secret.gz"));
    }
}
