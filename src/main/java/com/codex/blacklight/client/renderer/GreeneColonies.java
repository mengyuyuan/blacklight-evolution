package com.codex.blacklight.client.renderer;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class GreeneColonies {
    record Attachment(int source,int owner,float x,float y,float z){}
    static final List<Attachment> GROUPS=load();
    static final int NATIVE=791, JOINTS=NATIVE+12*GROUPS.size(), ROWS=(JOINTS+3)/4;

    private static List<Attachment> load(){
        var list=new ArrayList<Attachment>();
        try(var stream=GreeneColonies.class.getResourceAsStream("/assets/blacklight/models/greene_colonies.json");
            var reader=new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)){
            for(var value:JsonParser.parseReader(reader).getAsJsonArray()){
                var o=value.getAsJsonObject();var p=o.getAsJsonArray("point");
                int source=35+12*o.get("actor").getAsInt(),owner=o.get("bone").getAsInt();
                if(source<35 || source+11>=NATIVE || owner<0 || owner>=35)throw new IllegalArgumentException("Invalid colony attachment");
                list.add(new Attachment(source,owner,p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
            }
        }catch(IOException ex){throw new UncheckedIOException(ex);}
        return List.copyOf(list);
    }
    /** Duplicate each donor's original vertices just once, on the same VBO as the goddess. */
    static float[] mesh(float[] original){
        var starts=new int[NATIVE];var counts=new int[NATIVE];
        Arrays.fill(starts,-1);
        for(int i=0;i<original.length;i+=12){
            int joint=(int)original[i+11];
            if(starts[joint]<0)starts[joint]=i;
            counts[joint]+=12;
            if(i+12!=starts[joint]+counts[joint])throw new IllegalArgumentException("Non-contiguous colony mesh "+joint);
        }
        int size=original.length;
        for(var a:GROUPS)for(int j=0;j<12;j++)size+=counts[a.source+j];
        var vertices=Arrays.copyOf(original,size);int w=original.length,joint=NATIVE;
        for(var a:GROUPS)for(int j=0;j<12;j++,joint++){
            int source=a.source+j,n=counts[source];if(n==0)continue;
            System.arraycopy(original,starts[source],vertices,w,n);
            for(int p=w+11;p<w+n;p+=12)vertices[p]=joint;
            w+=n;
        }
        return vertices;
    }
    /** Retain each donor's twelve animated joints and exact scale at the new grip point. */
    static void pose(float[] matrices){
        int dst=NATIVE*16;
        for(var a:GROUPS){
            int owner=a.owner*16,anchor=a.source*16;
            float x=matrices[owner]*a.x+matrices[owner+4]*a.y+matrices[owner+8]*a.z+matrices[owner+12]-matrices[anchor+12];
            float y=matrices[owner+1]*a.x+matrices[owner+5]*a.y+matrices[owner+9]*a.z+matrices[owner+13]-matrices[anchor+13];
            float z=matrices[owner+2]*a.x+matrices[owner+6]*a.y+matrices[owner+10]*a.z+matrices[owner+14]-matrices[anchor+14];
            for(int j=0;j<12;j++,dst+=16){
                System.arraycopy(matrices,anchor+16*j,matrices,dst,16);
                matrices[dst+12]+=x;matrices[dst+13]+=y;matrices[dst+14]+=z;
            }
        }
    }
    private GreeneColonies(){}
}
