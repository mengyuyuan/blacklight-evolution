package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathPoseTest {
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    @Test void actualSkinBoxesMatchIndependentSourceBindCornersIncludingUnmappedChildren() throws Exception {
        // Fixture comes directly from the P2 world skeleton and the preserved
        // pre-rebind authored cubes; it does not read exported pose/geo values.
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/goliath-skin-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var table=GoliathMotion.table();int count=0;
            for(var item:root.getAsJsonArray("fixtures")){
                var f=item.getAsJsonObject();float frame=f.get("frame").getAsFloat();String bone=f.get("bone").getAsString(),clip=f.get("clip").getAsString();
                var pose=GoliathMotion.sample(clip,frame*20F/table.clip(clip).fps(),false);
                for(float yaw:new float[]{0,90,-137}){
                    var origin=new Vec3(11,7,-9);var region=GoliathContact.regions(pose,origin,yaw,0).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();
                    var box=region.boxes().get(f.get("cube").getAsInt());
                    for(var value:f.getAsJsonArray("points")){
                        var expected=vector(value.getAsJsonArray()).yRot((float)Math.toRadians(-yaw)).add(origin);
                        assertTrue(box.points.stream().anyMatch(p->p.distanceTo(expected)<.0002),bone+" frame="+frame+" yaw="+yaw+" missing corner "+expected);
                        assertTrue(box.nearest(expected).distanceTo(expected)<.0002,"source corner is on receiving surface");
                    }
                }
                count++;
            }
            assertEquals(48,count,"six slam samples including a half frame and two original scale extremes cover hands, fingers and weak plates");
        }
    }
    @Test void reboundGeometryDoesNotScaleTheSourceHitOriginAgain(){
        var action=SourceBossAction.get(com.codex.blacklight.entity.EncounterKind.GOLIATH,EncounterMove.HAMMER);
        var contact=SourceRadialWave.contact(action,38,Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-1,0,.5,0,2,1.5));
        assertNotNull(contact);assertEquals(.756267174355229,contact.origin().x,.00001);
        assertEquals(1.512534348710458,contact.origin().y,.00001);assertEquals(.756267174355229,contact.origin().z,.00001);
    }
    @Test void slenderClawsFollowTheOpposingWeightedWristInsteadOfTheSlamFist(){
        var pose=GoliathMotion.attack(EncounterMove.HAMMER,38);var table=GoliathMotion.table();
        assertEquals(3.0527516,table.point(pose,"left_hand").y,.002,"source Wrist_R includes SCL and original parent-scale compensation");
        assertEquals(.3602344,table.point(pose,"right_hand").y,.002,"source Wrist_L is unchanged");
        var fingers=GoliathContact.regions(pose,Vec3.ZERO,0,0).stream().filter(r->r.bone().startsWith("left_finger_")).toList();
        assertEquals(4,fingers.size());
        assertTrue(fingers.stream().flatMap(r->r.boxes().stream()).allMatch(b->b.bounds.minY>1.7),"static bind follows actual weighted slender-hand axis; no downward fingers through support at impact");
    }
    @Test void originalShoulderScaleMovesElbowWithoutStretchingTheEntireForearm(){
        var table=GoliathMotion.table();var source=java.util.Arrays.asList(table.bones);
        int shoulder=source.indexOf("source_Shoulder_R"),compensation=source.indexOf("source_parent_scale_Elbow_R");
        assertTrue(shoulder>=0&&compensation>=0,"source segment-scale compensation has an explicit parent factor");
        var clip=table.clip("ERUPTION");float min=Float.POSITIVE_INFINITY,max=0;double length=-1;
        for(float frame=0;frame<clip.frames().length;frame+=.25F){
            var pose=GoliathMotion.sample("ERUPTION",frame*20F/clip.fps(),false);
            float scale=pose[shoulder][6];min=Math.min(min,scale);max=Math.max(max,scale);
            assertEquals(1,scale*pose[compensation][6],.00002,"native inverse-parent-scale cancels the basis, not the elbow offset");
            var transforms=table.transforms(pose);
            var elbow=transforms[source.indexOf("source_Elbow_R")].getTranslation(new org.joml.Vector3f());
            var wrist=transforms[source.indexOf("source_Wrist_R")].getTranslation(new org.joml.Vector3f());
            double distance=elbow.distance(wrist);
            if(length<0)length=distance;else assertEquals(length,distance,.001,"original forearm translation has no scale track");
        }
        assertTrue(min<.39&&max>2,"the original extreme stretch track must not be replaced by unit scale");
    }
}
