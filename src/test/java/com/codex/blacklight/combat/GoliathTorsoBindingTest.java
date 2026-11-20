package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathTorsoBindingTest {
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}

    @Test void receivingTorsoAndHipCornersMatchIndependentSourceBindFixtures() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/goliath-torso-reference.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var fixtures=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fixtures");
            var table=GoliathMotion.table();int count=0;
            for(var item:fixtures){
                var f=item.getAsJsonObject();String clip=f.get("clip").getAsString(),bone=f.get("bone").getAsString();
                float frame=f.get("frame").getAsFloat();var pose=GoliathMotion.sample(clip,frame*20/table.clip(clip).fps(),false);
                for(float yaw:new float[]{0,90,-137}){
                    var origin=new Vec3(11,7,-9);
                    var box=GoliathContact.regions(pose,origin,yaw,0).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow().boxes().get(0);
                    for(var value:f.getAsJsonArray("points")){
                        var expected=vector(value.getAsJsonArray()).yRot((float)Math.toRadians(-yaw)).add(origin);
                        assertTrue(box.points.stream().anyMatch(p->p.distanceTo(expected)<.0002),clip+" frame="+frame+" bone="+bone+" yaw="+yaw+" missing source corner");
                    }
                }
                count++;
            }
            assertEquals(66,count,"six torso/hip leaves across eleven independent source samples");
        }
    }

    @Test void waistStaysAttachedToBothHipsAndChestThroughoutEveryExportedSourceClip(){
        var table=GoliathMotion.table();
        String[][] joins={{"torso_lower","torso_waist"},{"torso_waist","torso_middle"},
                {"torso_middle","torso"},{"torso_lower","left_leg"},{"torso_lower","right_leg"}};
        for(String clip:table.names())for(int frame=0;frame<table.clip(clip).frames().length;frame++){
            var pose=GoliathMotion.sample(clip,frame*20F/table.clip(clip).fps(),false);
            var regions=GoliathContact.regions(pose,Vec3.ZERO,0,0);var boxes=new HashMap<String,List<HydraContact.Box>>();
            for(var region:regions)boxes.put(region.bone(),region.boxes());
            for(var join:joins){
                var a=Objects.requireNonNull(boxes.get(join[0]),join[0]);var b=Objects.requireNonNull(boxes.get(join[1]),join[1]);
                assertTrue(a.stream().anyMatch(x->b.stream().anyMatch(x::touches)),
                        clip+" frame="+frame+" disconnected "+join[0]+" from "+join[1]);
            }
        }
    }

    @Test void bodyReusesTheAuthoredSectionsAndKeepsWeakRegionsSeparate() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/assets/blacklight/geo/goliath_mc.geo.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var bones=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            var counts=new HashMap<String,Integer>();
            for(var value:bones){var bone=value.getAsJsonObject();String name=bone.get("name").getAsString();
                if(name.startsWith("torso")&&bone.has("cubes"))counts.put(name,bone.getAsJsonArray("cubes").size());
            }
            assertEquals(Map.of("torso",11,"torso_middle",5,"torso_waist",3,"torso_lower",1),counts,
                    "nineteen authored cubes with one middle cube split at its centre; no added cover volume");
        }
        var pose=GoliathMotion.sample("idle",0,false);
        var intact = new HashMap<String,Integer>();
        for (var region : GoliathContact.regions(pose, Vec3.ZERO, 0, 0)) {
            intact.put(region.bone(), region.boxes().size());
        }
        // weak_0 is on the severed right leg. weak_1 belongs to the surviving
        // left support leg; the second organ break removes the slender R arm.
        var leg = Set.of("right_leg", "right_shin", "right_calf", "right_foot", "right_toes", "weak_0");
        var arm = Set.of("left_arm", "left_forearm", "left_hand",
                "left_finger_0", "left_finger_1", "left_finger_2", "left_finger_3");
        assertEquals(3, intact.get("weak_1"));
        assertTrue(intact.keySet().containsAll(leg) && intact.keySet().containsAll(arm));
        for (int mask : new int[]{1, 3}) {
            for (var region : GoliathContact.regions(pose, Vec3.ZERO, 0, mask)) {
                var bone = region.bone();
                boolean missing = leg.contains(bone) || (mask == 3 && arm.contains(bone));
                assertEquals(missing ? 0 : intact.get(bone), region.boxes().size(),
                        "wrong receiving surface for mask " + mask + ", bone " + bone);
            }
        }
    }
}
