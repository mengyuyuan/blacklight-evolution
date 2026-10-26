package com.codex.blacklight.util;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompactJsonTest {
    @Test void RestoresOriginalFormattingNumbersStringsAndUnicodeExactly()throws Exception{
        String value="{ \"text\":\"汉字😀 -1.2e-03 \\\"quoted\\\" \\\\ \\u0000\",\"values\":[0,-0.0,1,1.0,0.00001,1e-05,1.0E-5,1e+20,1000000.0,9999999999999999999999999999,4.9E-324,5e-324,3.4028234663852886e38]}\n";
        byte[] raw=value.getBytes(StandardCharsets.UTF_8);assertArrayEquals(raw,CompactJson.decode(CompactJson.encode(raw),raw.length));
    }
    @Test void PreservesRandomFloatingPointBitsAndExactLexemes()throws Exception{
        var random=new Random(1984);var text=new StringBuilder("[");
        for(int i=0;i<5000;i++){double value=Double.longBitsToDouble(random.nextLong());if(!Double.isFinite(value))continue;if(text.length()>1)text.append(',');text.append(Double.toString(value));}
        text.append(']');byte[] raw=text.toString().getBytes(StandardCharsets.UTF_8);assertArrayEquals(raw,CompactJson.decode(CompactJson.encode(raw),raw.length));
    }
    @Test void RejectsTruncationCorruptionAndOutputBudget()throws Exception{
        byte[] raw="{\"time\":[0.0,0.016666666666666666,0.03333333333333333],\"value\":-0.0}".getBytes(StandardCharsets.UTF_8),encoded=CompactJson.encode(raw);
        assertThrows(IOException.class,()->CompactJson.decode(encoded,raw.length-1));
        for(int length=0;length<encoded.length;length++){byte[] truncated=Arrays.copyOf(encoded,length);assertThrows(IOException.class,()->CompactJson.decode(truncated,raw.length));}
        encoded[24]^=1;assertThrows(IOException.class,()->CompactJson.decode(encoded,raw.length));
    }
    @Test void RejectsInvalidRawNulAndUnterminatedQuotedInput(){
        assertThrows(IOException.class,()->CompactJson.encode(new byte[]{'{',0,'}'}));
        assertThrows(IOException.class,()->CompactJson.encode("\"unterminated".getBytes(StandardCharsets.UTF_8)));
    }
}
