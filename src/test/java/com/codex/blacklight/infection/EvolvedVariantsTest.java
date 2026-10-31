package com.codex.blacklight.infection;

import com.codex.blacklight.combat.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedVariantsTest {
    @Test void EveryBiasHasOneDistinctAvailableBodyRatherThanFallingBackToTheBlade()throws Exception{
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/data/blacklight/blacklight/evolved_mapping/default.json"))){
            var table=EvolvedMapping.read(reader);var picked=new HashSet<String>();
            for(var type:EvolvedArchetype.values()){
                var core=table.organs().entrySet().stream().filter(e->e.getValue().getOrDefault(type.bias(),0D)==1D&&e.getValue().entrySet().stream().noneMatch(w->!w.getKey().equals(type.bias())&&w.getValue()>=1)).map(e->TissueCore.byId(e.getKey())).filter(Objects::nonNull).findFirst().orElseThrow();
                var genes=new TissueInheritance(Map.of(core,new TissueInheritance.Organ(900,0,0,0,0,71)),6,2100,true,null,1,6);
                for(int seed=0;seed<30;seed++){var choice=table.choose(genes,seed,EvolvedArchetype.types());assertEquals(type.id(),choice.type());assertFalse(choice.fallback());assertEquals(type.bias(),choice.bias());picked.add(choice.type());}
            }assertEquals(8,picked.size());
        }
        assertEquals(EvolvedArchetype.BLADE,EvolvedArchetype.resolve("future:missing"));
    }
    @Test void EveryShippedBodyHasItsOwnFiniteWeightedMeshAndValidActualRigDrivers()throws Exception{
        var hashes=new HashSet<Integer>();
        for(var type:EvolvedArchetype.values()){
            var p=Path.of("src/main/resources/assets/blacklight/models/evolved_variants/"+type.key()+".json");var text=Files.readString(p);hashes.add(text.hashCode());var mesh=JsonParser.parseString(text).getAsJsonObject();var vertices=mesh.getAsJsonArray("vertices");int arms=0;
            for(var value:vertices){var v=value.getAsJsonArray();assertEquals(6,v.size());for(int i=0;i<3;i++)assertTrue(Float.isFinite(v.get(i).getAsFloat()));for(int i=3;i<5;i++)assertTrue(v.get(i).getAsInt()>=0&&v.get(i).getAsInt()<EvolvedMotions.bones().size());}
            for(var value:mesh.getAsJsonArray("faces")){var f=value.getAsJsonArray();for(int i=0;i<4;i++)assertTrue(f.get(i).getAsInt()>=0&&f.get(i).getAsInt()<vertices.size());if(Set.of(4,6,8).contains(f.get(5).getAsInt()))arms++;}
            assertTrue(arms>50&&arms<5500,type.key());assertTrue(Files.isRegularFile(Path.of("art/evolved-variants/"+type.key()+".bbmodel")));
        }assertEquals(8,hashes.size());
    }
    @Test void EverySpecialHasWindupContactWithdrawalAndAClosedEndPose(){
        for(var move:EvolvedSpecialMove.values()){
            assertTrue(move.contact>=16);assertTrue(move.retract>move.contact);assertTrue(move.duration>move.retract+8);assertTrue(move.cooldown>move.duration);
            assertEquals(0,move.extension(0));assertEquals(0,move.extension(move.duration));
            for(float t:new float[]{0,move.contact-4,move.contact,move.retract,move.duration}){
                var pose=EvolvedVariantPose.special(move,t);assertEquals(67*7,pose.length);
                for(float f:pose)assertTrue(Float.isFinite(f));
                var skin=EvolvedMotions.skin(pose,1);var bones=EvolvedMotions.bones();
                for(String side:new String[]{"R","L"}){int hip=EvolvedMotions.index("Hip_"+side),knee=EvolvedMotions.index("Knee_"+side);assertEquals(6,skin[hip].transformPosition(bones.get(hip).pivot()).distance(skin[knee].transformPosition(bones.get(knee).pivot())),.02);}
            }
        }
    }
}
