package com.codex.blacklight.city;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CompactCityCodecTest {
    static final CompactCityCodec.State STONE=new CompactCityCodec.State("minecraft:stone",Map.of()),AIR=new CompactCityCodec.State("minecraft:air",Map.of()),CAVE=new CompactCityCodec.State("minecraft:cave_air",Map.of()),WATER=new CompactCityCodec.State("minecraft:water",Map.of("level","0"));
    CompactCityCodec.SourceChunk fixture(int slot){
        var palette=new CompactCityCodec.State[]{STONE,AIR,CAVE,WATER};long[] packed=new long[256];var random=new Random(99);
        short[] tops=new short[256],bottoms=new short[256];Arrays.fill(tops,(short)15);
        for(int at=0;at<4096;at++){int id=random.nextInt(4);packed[at/16]|=(long)id<<((at%16)*4);if(id==0||id==3)tops[at&255]=(short)Math.max(tops[at&255],48+(at>>>8));}
        return new CompactCityCodec.SourceChunk(slot,tops,bottoms,new CompactCityCodec.SourceSection[]{new CompactCityCodec.SourceSection(0,new CompactCityCodec.State[]{STONE},new long[0]),new CompactCityCodec.SourceSection(3,palette,packed)});
    }
    @Test void PreservesSectionGapsAirVariantsAndEveryCell()throws Exception{
        var source=List.of(fixture(0),fixture(31));var group=CompactCityCodec.decode(CompactCityCodec.encode(source));CompactCityCodec.verify(source,group);
        assertNull(group.chunks().get(0).cells()[1]);assertEquals(1,group.chunks().get(0).cells()[0].length);assertEquals(4096,group.chunks().get(0).cells()[3].length);assertEquals(4,group.palette().length);
    }
    @Test void DetectsAChangedHeightOrSingleChangedBlock()throws Exception{
        var source=List.of(fixture(0));var group=CompactCityCodec.decode(CompactCityCodec.encode(source));
        group.chunks().get(0).tops()[0]++;assertThrows(IOException.class,()->CompactCityCodec.verify(source,group));group.chunks().get(0).tops()[0]--;
        var cells=group.chunks().get(0).cells()[3];cells[4095]=(short)((cells[4095]+1)%group.palette().length);assertThrows(IOException.class,()->CompactCityCodec.verify(source,group));
    }
    @Test void RejectsMixedCoordinatesTruncationAndInvalidPalette()throws Exception{
        assertThrows(IOException.class,()->CompactCityCodec.encode(List.of(fixture(31),fixture(32))));
        byte[] valid=CompactCityCodec.encode(List.of(fixture(0)));
        assertThrows(IOException.class,()->CompactCityCodec.decode(Arrays.copyOf(valid,valid.length-1)));
        valid[4]=0;valid[5]=0;assertThrows(IOException.class,()->CompactCityCodec.decode(valid));
    }
}
