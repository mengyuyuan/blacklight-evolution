package com.codex.blacklight.infection;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueVoxelsTest {
    @Test void hornBecomesAxisAlignedBlocks(){
        // A slanted spike leaving the top of a 8x8x8 cube.
        float[] low={-4,-4,-4},high={4,4,4};
        var tris=new ArrayList<float[]>();var colors=new ArrayList<Integer>();
        float[][] base={{-1,4,-1},{1,4,-1},{1,4,1},{-1,4,1}};float[] tip={3,10,0};
        for(int i=0;i<4;i++){float[] a=base[i],b=base[(i+1)%4];tris.add(new float[]{a[0],a[1],a[2],b[0],b[1],b[2],tip[0],tip[1],tip[2]});colors.add(0x303034);}
        var out=TissueVoxels.convert(tris.toArray(float[][]::new),colors.stream().mapToInt(i->i).toArray(),low,high,.9F,1);
        assertFalse(out.isEmpty());
        for(var t:out){
            assertEquals(1,Math.abs(t.nx())+Math.abs(t.ny())+Math.abs(t.nz()),1e-6,"every block face is axis aligned");
            for(float v:t.p())assertEquals(Math.round(v*2)/2F,v,.01F,"vertices sit on the half-pixel grid");
        }
        var again=TissueVoxels.convert(tris.toArray(float[][]::new),colors.stream().mapToInt(i->i).toArray(),low,high,.9F,1);
        assertEquals(out.size(),again.size());for(int i=0;i<out.size();i++){assertArrayEquals(out.get(i).p(),again.get(i).p());assertEquals(out.get(i).color(),again.get(i).color());}
    }
    @Test void recessedWoundStaysSmooth(){
        float[] low={-4,-4,-4},high={4,4,4};
        float[][] tris={{-2,-2,4.2F,2,-2,3.6F,0,2,3.8F}};
        var out=TissueVoxels.convert(tris,new int[]{0x7a1b20},low,high,.9F,1);
        assertEquals(1,out.size());assertArrayEquals(tris[0],out.getFirst().p());
    }
    /** Every shipped species: bounded geometry after conversion. */
    @Test void speciesAssetsStayWithinBudget() throws IOException{
        Path dir=Path.of("src/main/resources/assets/blacklight/infection_parts");
        int before=0,after=0,worst=0;String worstName="";
        try(var files=Files.list(dir)){
            for(Path file:files.sorted().toList()){
                var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(!root.has("bones"))continue;int b0=0,b1=0;
                for(var e:root.getAsJsonArray("bones")){
                    var bone=e.getAsJsonObject();if(!bone.has("growth")||!bone.has("reference"))continue;
                    var ref=bone.getAsJsonObject("reference");float[] low=vec(ref.get("min")),high=vec(ref.get("max"));
                    for(int a=0;a<3;a++){low[a]*=16;high[a]*=16;}
                    var g=bone.getAsJsonObject("growth");float[] vs=floats(g.get("vertices"));float[] ts=floats(g.get("triangles"));
                    int n=ts.length/4;float[][] tris=new float[n][];int[] colors=new int[n];
                    for(int i=0;i<n;i++){tris[i]=new float[9];for(int j=0;j<3;j++)for(int a=0;a<3;a++)tris[i][j*3+a]=vs[(int)ts[i*4+j]*3+a]*16;colors[i]=(int)ts[i*4+3];}
                    long start=System.nanoTime();
                    var out=TissueVoxels.convert(tris,colors,low,high,.9F,file.hashCode());
                    assertTrue(System.nanoTime()-start<400_000_000L,file+" converts quickly");
                    b0+=n;b1+=out.size();
                }
                before+=b0;after+=b1;if(b1>worst){worst=b1;worstName=file.getFileName().toString();}
                System.out.printf("%s %d -> %d%n",file.getFileName(),b0,b1);
            }
        }
        System.out.printf("total %d -> %d, worst %s %d%n",before,after,worstName,worst);
        assertTrue(worst<9000,"a single species stays under 9000 growth triangles");
    }
    private static float[] vec(JsonElement e){return floats(e);}
    private static float[] floats(JsonElement e){
        if(e.isJsonArray()){var a=e.getAsJsonArray();float[] r=new float[a.size()];for(int i=0;i<r.length;i++)r[i]=a.get(i).getAsFloat();return r;}
        var parts=e.getAsString().trim().split("\\s+");float[] r=new float[parts.length];for(int i=0;i<r.length;i++)r[i]=Float.parseFloat(parts[i]);return r;
    }
}
