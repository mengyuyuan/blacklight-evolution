package com.codex.blacklight.animation;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class SovereignWingRigTest {
    private static Vector3f point(JsonArray a){return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()).div(16);}
    @Test void recoveryTucksTheLongFingersBehindTheActor() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(double phase=.32;phase<.95;phase+=.02) {
                var pose=SovereignWingMotion.sample(phase,1,1,0,0,0,0,0,0);
                var frames=new HashMap<String,Matrix4f>();var pivots=new HashMap<String,Vector3f>();
                for(var item:data.getAsJsonArray("bones")) {
                    var b=item.getAsJsonObject();String name=b.get("name").getAsString();var pivot=point(b.getAsJsonArray("pivot"));
                    var matrix=new Matrix4f();var offset=new Vector3f(pivot);
                    if(!b.get("parent").isJsonNull()){String parent=b.get("parent").getAsString();matrix.set(frames.get(parent));offset.sub(pivots.get(parent));}
                    matrix.translate(offset);
                    matrix.rotate(SovereignWingMotion.articulation(name,pose,new Vector3f(0,0,1),1,new org.joml.Quaternionf()));
                    frames.put(name,matrix);pivots.put(name,pivot);
                }
                for(var entry:data.getAsJsonObject("skin_paths").entrySet()) {
                    var path=entry.getValue().getAsJsonArray();var tip=point(path.get(path.size()-1).getAsJsonArray());
                    tip.sub(pivots.get(entry.getKey()));frames.get(entry.getKey()).transformPosition(tip);
                    assertTrue(tip.z>.2F,entry.getKey()+" swings in front of actor at "+phase+": "+tip);
                    assertTrue(tip.x*(entry.getKey().startsWith("left")?1:-1)>.15F,entry.getKey()+" crosses the body centre at "+phase+": "+tip);
                }
            }
        }
    }
    @Test void tissueRootsEmergeFromBackWithoutACrossbar() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(2,data.getAsJsonObject("root_paths").size());
            for(var entry:data.getAsJsonObject("root_paths").entrySet()) {
                var path=entry.getValue().getAsJsonArray();var base=point(path.get(0).getAsJsonArray());var hinge=point(path.get(1).getAsJsonArray());
                assertTrue(base.y>1.30F&&base.y<1.45F,"Root must emerge from upper scapula");
                assertTrue(base.distance(hinge)<.35F,"No long stalk between torso and wing");
                assertTrue(Math.abs(base.x)<.25F);assertTrue(base.z<.13F,"Root must embed into the back surface");
                assertTrue(hinge.y>1.4F&&hinge.y<1.5F,"Wing hinge belongs near shoulder blades, not lower back");
            }
            int flesh=0;
            for(var item:data.getAsJsonArray("elements")) {
                var e=item.getAsJsonObject();String name=e.get("name").getAsString();
                assertFalse(name.startsWith("root_saddle")||name.startsWith("root_ridge"),"No cross-back bracket");
                if(e.has("_root_skin")){assertTrue(data.getAsJsonObject("root_paths").has(e.get("_root_skin").getAsString()));flesh++;}
            }
            assertTrue(flesh>=2,"Broad root tissue must blend between chest and wings");
        }
    }
    @Test void independentWingsDoNotReplaceShouldersOrTrailArmpitStrands() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String side:List.of("left","right")) {
                int caps=0;
                for(var item:data.getAsJsonArray("elements")) {
                    var e=item.getAsJsonObject();String name=e.get("name").getAsString();
                    assertFalse(name.contains("_mantle_")||name.contains("_tendril_3")||name.contains("_tendril_4"),"No armpit fringe");
                    if(!e.has("_shoulder_skin")||!side.equals(e.get("_shoulder_skin").getAsString()))continue;
                    caps++;
                    assertTrue(point(e.getAsJsonArray("from")).y>1.25F,"Shoulder detail must stay above the underarm");
                }
                assertEquals(0,caps,"Independent wings must not replace or wrap the player shoulders");
            }
        }
    }
    @Test void individualArchesAndBladesHaveConnectedBendJoints() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            assertNotNull(stream);
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var bones=new HashMap<String,JsonObject>();
            for(var b:data.getAsJsonArray("bones")){var o=b.getAsJsonObject();assertNull(bones.put(o.get("name").getAsString(),o));}
            assertEquals(35,bones.size());
            assertEquals(32,data.getAsJsonObject("skin_paths").size());
            for(String side:List.of("left","right")) {
                for(int i=1;i<=4;i++) {
                    String rib=side+"_rib_"+i,arch=side+"_arch_"+i,finger=side+"_finger_"+i,tip=side+"_tip_"+i;
                    assertEquals(side+"_root",bones.get(rib).get("parent").getAsString());
                    for(var pair:List.of(new String[]{rib,arch},new String[]{arch,finger},new String[]{finger,tip})) {
                        var path=data.getAsJsonObject("skin_paths").getAsJsonArray(pair[0]);
                        assertEquals(pair[0],bones.get(pair[1]).get("parent").getAsString());
                        assertEquals(path.get(path.size()-1),bones.get(pair[1]).get("pivot"),"Connected segments must share the physical hinge");
                    }
                }
            }
            assertNotEquals(data.getAsJsonObject("fold_poses").get("left_over"),data.getAsJsonObject("fold_poses").get("right_over"));
            int membranes=0,triangles=0;
            for(var element:data.getAsJsonArray("elements")) {
                var e=element.getAsJsonObject();assertTrue(bones.containsKey(e.get("_parent").getAsString()));
                assertEquals("mesh",e.get("type").getAsString(),"Continuous surfaces must not fall back to cuboid fragments");
                if(e.get("name").getAsString().contains("_membrane_")){assertTrue(e.getAsJsonObject("vertices").size()>50);membranes++;}
                for(var face:e.getAsJsonObject("faces").entrySet())triangles+=face.getValue().getAsJsonObject().getAsJsonArray("vertices").size()-2;
                for(var weight:e.getAsJsonObject("_weights").entrySet()){
                    var w=weight.getValue().getAsJsonArray();assertTrue(bones.containsKey(w.get(0).getAsString()));assertTrue(bones.containsKey(w.get(1).getAsString()));
                    assertTrue(Float.isFinite(w.get(2).getAsFloat())&&w.get(2).getAsFloat()>=0&&w.get(2).getAsFloat()<=1);
                }
            }
            assertEquals(6,membranes);assertTrue(triangles<=25000,"Runtime mesh budget stays below the original cuboid asset: "+triangles);
        }
    }
}
