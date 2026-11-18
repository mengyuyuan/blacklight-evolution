package com.codex.blacklight.military.aircraft;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HelicopterStrafePathTest {
    @Test void matchesOriginalInstructionResultsAcrossTerrainAndBoundaryCases()throws Exception{
        try(var input=getClass().getResourceAsStream("/blacklight/helicopter-strafe-native.json")){
            assertNotNull(input);var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("8cba8da3412df4f49cd61d02445e87a0cda551dea4f2fc1e66b19c1bfe28a760",root.get("dllSHA256").getAsString());
            assertEquals("103e1cc0",root.get("entry").getAsString());assertEquals(81,root.getAsJsonArray("cases").size());
            for(var element:root.getAsJsonArray("cases")){
                var c=element.getAsJsonObject();String name=c.get("name").getAsString();int n=c.get("size").getAsInt();float[][] h=new float[n][n];
                for(var row:h)java.util.Arrays.fill(row,c.get("base").getAsFloat());
                for(var rect:c.getAsJsonArray("rectangles")){var r=rect.getAsJsonArray();for(int x=r.get(0).getAsInt();x<=r.get(2).getAsInt();x++)for(int z=r.get(1).getAsInt();z<=r.get(3).getAsInt();z++)h[x][z]=r.get(4).getAsFloat();}
                float cell=c.has("cell")?c.get("cell").getAsFloat():1;Vec3 origin=c.has("origin")?new Vec3(c.getAsJsonArray("origin").get(0).getAsFloat(),0,c.getAsJsonArray("origin").get(1).getAsFloat()):Vec3.ZERO;
                var grid=new HelicopterStrafePath.Grid(){public float cellSize(){return cell;}public float originX(){return (float)origin.x;}public float originZ(){return (float)origin.z;}public float height(int x,int z){return x>=0&&z>=0&&x<n&&z<n?h[x][z]:0;}};
                var actual=HelicopterStrafePath.find(grid,c.has("craft")?vector(c.getAsJsonArray("craft")):new Vec3(40,120,40),c.has("target")?vector(c.getAsJsonArray("target")):new Vec3(64,100,64),c.has("minimum")?c.get("minimum").getAsInt():5,c.has("maximum")?c.get("maximum").getAsInt():40,c.has("farthest")&&c.get("farthest").getAsBoolean());
                var expected=c.getAsJsonObject("native");assertEquals(expected.get("accepted").getAsBoolean(),actual!=null,name);
                if(actual!=null){same(vector(expected.getAsJsonArray("targetGround")),actual.targetGround(),name+" ground");same(vector(expected.getAsJsonArray("start")),actual.start(),name+" start");same(vector(expected.getAsJsonArray("end")),actual.end(),name+" end");assertEquals(expected.get("relativeRise").getAsFloat(),actual.relativeRise(),1e-5,name+" rise");}
            }
        }
    }
    @Test void unavailableMinecraftTerrainCannotBecomeAnEmptyCorridor(){
        var grid=new HelicopterStrafePath.Grid(){public float cellSize(){return 1;}public float originX(){return 0;}public float originZ(){return 0;}public float height(int x,int z){return x>70?Float.NaN:100;}};
        assertNull(HelicopterStrafePath.find(grid,new Vec3(40,120,40),new Vec3(64,100,64),5,40,false));
    }
    @Test void unusableGridResolutionIsRejectedBeforeTerrainAccess(){
        for(float cell:new float[]{Float.MIN_VALUE,.0001F,0,-1,Float.NaN,Float.POSITIVE_INFINITY}){
            var grid=new HelicopterStrafePath.Grid(){public float cellSize(){return cell;}public float originX(){return 0;}public float originZ(){return 0;}public float height(int x,int z){fail("Invalid resolution must not sample terrain");return 100;}};
            assertNull(HelicopterStrafePath.find(grid,new Vec3(40,120,40),new Vec3(64,100,64),5,40,false));
        }
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static void same(Vec3 a,Vec3 b,String name){assertEquals(0,a.distanceTo(b),1e-5,name);}
}
