package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldAtlasTest {
    @Test void geographicCoordinatesAreStableAndFitTheWorldBorder(){
        assertEquals(0,WorldAtlas.worldX(114.048));assertEquals(0,WorldAtlas.worldZ(22.552));
        assertTrue(WorldAtlas.worldX(114.05)>0);assertTrue(WorldAtlas.worldZ(22.55)>0);
        for(int lon=-180;lon<=180;lon++)assertTrue(Math.abs(WorldAtlas.worldX(lon))<29_999_984);
        assertEquals(WorldAtlas.worldX(-180),WorldAtlas.worldX(180));
        assertEquals(WorldAtlas.worldX(15),WorldAtlas.worldX(375));
        assertTrue(Math.abs(WorldAtlas.worldZ(-90))<29_999_984);
    }
    @Test void elevationSamplesAgreeAtRegionalEdgesIncludingNegativeCoordinates(){
        int checked=0;
        for(var cell:WorldAtlas.CELLS){
            if(checked++%101!=0)continue;
            int x=cell.x()*1024,z=cell.z()*1024;
            var left=WorldAtlas.tile(x,z);
            var right=WorldAtlas.tile(x+1024,z);
            if(right.heights!=null)for(int i=0;i<33;i++)assertEquals(left.heights[i*33+32],right.heights[i*33]);
            var below=WorldAtlas.tile(x,z+1024);
            if(below.heights!=null)for(int i=0;i<33;i++)assertEquals(left.heights[32*33+i],below.heights[i]);
            assertEquals(left.heights[0],left.height(x,z));
        }
    }
    @Test void wholeCityPlanIsLazyAndCoversEachTileWithoutAllocatingAllChunks(){
        var cells=CityGenerationPlan.cells(3);
        assertTrue(cells.size()>10_000_000);
        assertEquals(WorldAtlas.CELLS.size()*4096,cells.size());
        for(int i=0;i<WorldAtlas.CELLS.size();i+=97){
            var tile=WorldAtlas.CELLS.get(i);var first=cells.get(i*4096);var last=cells.get(i*4096+4095);
            assertEquals(tile.x()*64,first.x());assertEquals(tile.z()*64,first.z());
            assertEquals(first.x()+63,last.x());assertEquals(first.z()+63,last.z());
        }
        assertThrows(IndexOutOfBoundsException.class,()->cells.get(cells.size()));
    }
    @Test void courtyardHolesAndRoadDistanceRemainGeometric(){
        var f=new WorldAtlas.Feature(com.google.gson.JsonParser.parseString("""
            {"k":8,"id":1,"y":70,"h":20,"m":0,"w":4,"l":0,
             "p":[[[0,0,10,0,10,10,0,10,0,0],[3,3,7,3,7,7,3,7,3,3]]],"s":[[0,0,10,0]]}
            """).getAsJsonObject());
        assertTrue(f.contains(1,1));assertFalse(f.contains(5,5));assertFalse(f.contains(11,5));
        assertEquals(2,f.lineDistance(5,2));assertEquals(1,f.edgeDistance(1,1));
    }
}
