package com.codex.blacklight.infection;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LateSpeciesGrowthAssetTest {
    @Test void lateBodiesKeepSpeciesJointsAndBoundedFiniteTopology() throws Exception {
        for(String species:List.of("bat","phantom","iron_golem","wither")){
            String directory=species.equals("wither")?"infected_models":"infection_parts";
            try(var in=getClass().getResourceAsStream("/assets/blacklight/"+directory+"/"+species+".json")){
                assertNotNull(in,species);
                var data=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("minecraft:"+species,data.get("species").getAsString());
                int triangles=0,activeJoints=0;
                var meshes=new ArrayList<JsonObject>();
                for(var node:data.getAsJsonArray("bones")){
                    var bone=node.getAsJsonObject();if(!bone.has("lateGrowth"))continue;
                    var mesh=bone.getAsJsonObject("lateGrowth").deepCopy();mesh.add("name",bone.get("name"));meshes.add(mesh);
                }
                if(data.has("nativeLateGrowth"))for(var node:data.getAsJsonArray("nativeLateGrowth"))meshes.add(node.getAsJsonObject());
                if(species.equals("bat"))assertEquals(Set.of("left_wing_tip","right_wing_tip"),data.getAsJsonArray("nativeLateGrowth").asList().stream().map(n->n.getAsJsonObject().get("name").getAsString()).collect(java.util.stream.Collectors.toSet()));
                for(var mesh:meshes){
                    var vs=mesh.getAsJsonArray("vertices");var ts=mesh.getAsJsonArray("triangles");
                    assertEquals(0,vs.size()%3);assertEquals(0,ts.size()%4);
                    if(!ts.isEmpty())activeJoints++;
                    for(var vertex:vs)assertTrue(Float.isFinite(vertex.getAsFloat()));
                    for(int i=0;i<ts.size();i++)if(i%4!=3)assertTrue(ts.get(i).getAsInt()>=0&&ts.get(i).getAsInt()<vs.size()/3,mesh.get("name").getAsString());
                    triangles+=ts.size()/4;
                }
                assertTrue(activeJoints>=4,species+" needs changes on several connected joints");
                assertTrue(triangles>=200&&triangles<2200,species+" fixed mesh budget: "+triangles);
            }
        }
    }
}
