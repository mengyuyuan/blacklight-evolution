package com.codex.blacklight.city;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Metadata shared with the actual original NBT authoring tool. */
public record LargeBaseBlueprint(String name,int width,int height,int depth,List<int[]> ammunition,List<Guard> guards,List<Module> modules){
    public record Guard(int x,int y,int z,boolean heavy,String role){
        public Guard(int x,int y,int z,boolean heavy){this(x,y,z,heavy,"");}
    }
    public record Module(int[] bounds,int[] entry){}
    private static final class Definitions { static final Map<String,LargeBaseBlueprint> ALL=read(); }
    public static LargeBaseBlueprint get(boolean blackwatch){return Definitions.ALL.get(blackwatch?"large_blackwatch_base":"large_military_base");}
    private static Map<String,LargeBaseBlueprint> read(){
        try(var stream=LargeBaseBlueprint.class.getResourceAsStream("/data/blacklight/military/large_bases.json")){
            if(stream==null)throw new IllegalStateException("Missing military base metadata");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new HashMap<String,LargeBaseBlueprint>();
            for(var entry:root.getAsJsonArray("bases")){
                var j=entry.getAsJsonObject();var ammunition=new ArrayList<int[]>();var guards=new ArrayList<Guard>();var modules=new ArrayList<Module>();
                for(var coordinate:j.getAsJsonArray("ammo_chests")){var a=coordinate.getAsJsonArray();ammunition.add(new int[]{a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()});}
                for(var value:j.getAsJsonArray("guards")){var g=value.getAsJsonObject();guards.add(new Guard(g.get("x").getAsInt(),g.get("y").getAsInt(),g.get("z").getAsInt(),g.get("heavy").getAsBoolean(),g.has("role")?g.get("role").getAsString():""));}
                for(var value:j.getAsJsonArray("modules")){var m=value.getAsJsonObject();modules.add(new Module(array(m.getAsJsonArray("bounds")),array(m.getAsJsonArray("entry"))));}
                var def=new LargeBaseBlueprint(j.get("name").getAsString(),j.get("width").getAsInt(),j.get("height").getAsInt(),j.get("depth").getAsInt(),List.copyOf(ammunition),List.copyOf(guards),List.copyOf(modules));
                if(def.width!=65||def.height>20||def.guards.size()>32||def.ammunition.isEmpty())throw new IllegalStateException("Invalid base metadata: "+def.name);
                result.put(def.name,def);
            }
            return Map.copyOf(result);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static int[] array(com.google.gson.JsonArray values){int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsInt();return result;}
}
