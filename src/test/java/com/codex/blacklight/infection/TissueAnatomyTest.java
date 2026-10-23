package com.codex.blacklight.infection;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TissueAnatomyTest {
    @Test void laterKeysJoinMaturityAndRemainCompatibleWithOldAssets() {
        float[][] keys={{0},{2},{4},{7},{11}};
        assertEquals(4,TissueAnatomy.coordinate(keys,0,1,0));
        assertEquals(7,TissueAnatomy.coordinate(keys,0,1,1));
        assertEquals(11,TissueAnatomy.coordinate(keys,0,1,2));
        assertEquals(11,TissueAnatomy.coordinate(keys,0,1,100));
        assertEquals(2,TissueAnatomy.coordinate(keys,0,.5F,0));
        assertEquals(4,TissueAnatomy.coordinate(java.util.Arrays.copyOf(keys,3),0,1,2));
        for(int boundary:new int[]{TissueGrowth.MATURITY_TICKS,TissueGrowth.HYPERTROPHY_TICKS,TissueGrowth.ANCIENT_TICKS}) {
            float before=TissueAnatomy.coordinate(keys,0,TissueGrowth.progress(boundary-1),TissueGrowth.overgrowth(boundary-1));
            float after=TissueAnatomy.coordinate(keys,0,TissueGrowth.progress(boundary+1),TissueGrowth.overgrowth(boundary+1));
            assertEquals(before,after,.00001F);
        }
    }
    @Test void growthKeysAreContinuousAndSecondaryLimbFollowsPrimary() {
        for(long seed=0;seed<100;seed++) {
            for(int phase=0;phase<4;phase++) {
                assertEquals(0,TissueAnatomy.local(seed,0,phase));assertEquals(1,TissueAnatomy.local(seed,1,phase));
                float previous=0;
                for(int i=0;i<=1000;i++) {
                    float next=TissueAnatomy.local(seed,i/1000F,phase);assertTrue(next>=previous);previous=next;
                }
            }
            assertTrue(TissueAnatomy.local(seed,.45F,1)>TissueAnatomy.local(seed,.45F,2));
        }
        assertEquals(7,TissueAnatomy.coordinate(2,7,13,.5F));
        assertTrue(Math.abs(TissueAnatomy.coordinate(2,7,13,.50001F)-TissueAnatomy.coordinate(2,7,13,.49999F))<.0001);
        assertEquals(0,TissueAnatomy.reveal(.1F,.2F,.4F));
        assertEquals(1,TissueAnatomy.reveal(.5F,.2F,.4F));
    }
    @Test void authoredCowHasValidBoundedTopologyAndArticulatedClawDigits() throws Exception {
        var path=Path.of("src/main/resources/assets/blacklight/tissue_blueprints/cow.json");
        var source=JsonParser.parseString(Files.readString(path)).getAsJsonObject();int faces=0,digits=0;
        for(var entry:source.getAsJsonArray("regions")) {
            var region=entry.getAsJsonObject();var ref=region.getAsJsonArray("reference");
            for(int i=0;i<3;i++)assertTrue(ref.get(i+3).getAsFloat()>ref.get(i).getAsFloat());
            for(var item:region.getAsJsonArray("meshes")) {
                var mesh=item.getAsJsonObject();var stages=mesh.getAsJsonArray("stages");assertEquals(3,stages.size());
                int count=stages.get(0).getAsJsonArray().size();assertEquals(0,count%3);
                for(var stage:stages) {
                    assertEquals(count,stage.getAsJsonArray().size());
                    for(var value:stage.getAsJsonArray())assertTrue(Float.isFinite(value.getAsFloat())&&Math.abs(value.getAsFloat())<80);
                }
                for(var f:mesh.getAsJsonArray("faces")) {
                    faces++;var face=f.getAsJsonArray();assertEquals(7,face.size());
                    for(int i=0;i<4;i++)assertTrue(face.get(i).getAsInt()>=0&&face.get(i).getAsInt()<count/3);
                    assertTrue(face.get(4).getAsInt()>=0&&face.get(4).getAsInt()<=7);
                    assertTrue(face.get(6).getAsFloat()>face.get(5).getAsFloat());
                }
                if(mesh.has("motion")&&mesh.get("motion").getAsString().equals("digit")){
                    digits++;assertEquals(3,mesh.getAsJsonArray("pivots").size());assertEquals(3,mesh.getAsJsonArray("parentPivots").size());
                    for(var key:mesh.getAsJsonArray("pivots"))assertEquals(3,key.getAsJsonArray().size());
                }
            }
        }
        assertEquals(6,digits,"both forelimbs grow three independently posed digits");
        assertTrue(faces<=650,"authored anatomy must remain suitable for multiple infected hosts");
    }
    @Test void cowHeadReconstructsItsSkullAndHingedJawInsteadOfOnlyAddingCheekProps() throws Exception {
        var source=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/blacklight/tissue_blueprints/cow.json"))).getAsJsonObject();
        var head=java.util.stream.StreamSupport.stream(source.getAsJsonArray("regions").spliterator(),false)
                .map(com.google.gson.JsonElement::getAsJsonObject).filter(r->r.get("name").getAsString().equals("head")).findFirst().orElseThrow();
        int reconstructed=0,jaws=0;
        for(var item:head.getAsJsonArray("meshes")) {
            var mesh=item.getAsJsonObject();
            if(mesh.has("skin")&&mesh.get("skin").getAsBoolean()) {
                reconstructed++;var stages=mesh.getAsJsonArray("stages");
                assertNotEquals(stages.get(0),stages.get(1));assertNotEquals(stages.get(1),stages.get(2));
                // Newborn reconstruction stays inside the original cube; the
                // mature muzzle actually extends, rather than merely acquiring a mask.
                float minMatureZ=0;
                for(int i=0;i<stages.get(0).getAsJsonArray().size();i+=3) {
                    var initial=stages.get(0).getAsJsonArray();
                    assertTrue(Math.abs(initial.get(i).getAsFloat())<=4);
                    assertTrue(Math.abs(initial.get(i+1).getAsFloat())<=4);
                    assertTrue(initial.get(i+2).getAsFloat()>=-6&&initial.get(i+2).getAsFloat()<=0);
                    minMatureZ=Math.min(minMatureZ,stages.get(2).getAsJsonArray().get(i+2).getAsFloat());
                }
                assertTrue(minMatureZ<-9,"both jaw and skull must develop a longer muzzle");
            }
            if(mesh.has("motion")&&mesh.get("motion").getAsString().equals("jaw")) {
                jaws++;assertEquals(3,mesh.getAsJsonArray("pivots").size());
                assertEquals(0,mesh.get("phase").getAsInt(),"head mutation starts before late hindlimb growth");
            }
        }
        assertEquals(2,reconstructed);assertEquals(1,jaws);
    }
}
