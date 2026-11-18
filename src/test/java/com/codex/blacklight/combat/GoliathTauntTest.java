package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathTauntTest {
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    @Test void sourceJointIndexChainMatchesRawNativePoseIncludingUnweightedFingers() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/goliath-taunt-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("samples")){
                var row=value.getAsJsonObject();var snapshot=new GoliathCharge.Snapshot(row.get("phase").getAsInt(),100);
                float age=100+row.get("age").getAsFloat();var origin=new Vec3(17,5,-9);
                for(float yaw:new float[]{0,90,-137}){
                    var anchor=vector(row.getAsJsonArray("bodyAnchor")).yRot((float)Math.toRadians(-yaw)).add(origin);
                    assertTrue(GoliathTaunt.bodyAnchor(snapshot,age,origin,yaw).distanceTo(anchor)<.0004,"cover origin follows source chest, not feet");
                    var window=GoliathTaunt.windows(snapshot,age,origin,yaw,origin,yaw).stream().filter(w->w.id()==row.get("windowId").getAsInt()).findFirst().orElseThrow();
                    for(var p:row.getAsJsonArray("points")){
                        var point=p.getAsJsonObject();String joint=point.get("joint").getAsString();
                        var actual=window.sweeps().stream().filter(s->s.joint().equals(joint)).findFirst().orElseThrow();
                        var expected=vector(point.getAsJsonArray("worldYaw0")).yRot((float)Math.toRadians(-yaw)).add(origin);
                        assertTrue(actual.to().distanceTo(expected)<.0004,joint+" raw frame="+row.get("frame"));
                        assertEquals(row.get("radius").getAsDouble(),actual.radius(),.00001);
                    }
                }
            }
        }
    }
    @Test void releaseCirclesShareOneDamageGroupAndPropOnlyHitNeverBecomesPlayerDamage(){
        var hits=GoliathTaunt.windows(new GoliathCharge.Snapshot(9,0),4,Vec3.ZERO,0,Vec3.ZERO,0);
        var circles=hits.stream().filter(w->w.id()==901).toList();assertEquals(2,circles.size());
        assertEquals(750,circles.get(0).sourceDamage());assertEquals(400,circles.get(1).sourceDamage());
        assertTrue(hits.stream().noneMatch(w->w.sourceDamage()>750),"Prop-only5000 is not a health-target attack");
        assertTrue(GoliathTaunt.windows(new GoliathCharge.Snapshot(10,0),20,Vec3.ZERO,0,Vec3.ZERO,0).isEmpty(),"ordinary pose has no continuing wrist/body blast");
    }
    @Test void finiteWindowClipsTheSweepAndDoesNotReplayAfterCancellationOrSkippedTicks(){
        var snapshot=new GoliathCharge.Snapshot(7,0);var start=new Vec3(0,0,0);var end=new Vec3(50,0,0);
        var windows=GoliathTaunt.windows(snapshot,6,start,0,end,0);assertEquals(1,windows.size(),"5..6 crosses original8/30-second end");
        var fixed=GoliathTaunt.windows(snapshot,6,Vec3.ZERO,0,Vec3.ZERO,0).get(0);
        double displacement=windows.get(0).sweeps().get(0).to().x-fixed.sweeps().get(0).to().x;
        assertEquals(50*(.2666670084*20-5),displacement,.001,"root transform is clipped to source window end, not whole50-block tick");
        assertTrue(GoliathTaunt.windows(snapshot,1000,start,0,end,0).isEmpty(),"unobserved historical window cannot replay");
        assertTrue(GoliathTaunt.windows(GoliathCharge.Snapshot.INACTIVE,4,start,0,end,0).isEmpty());
    }
    @Test void sourceShapecastFindsAnIntermediateSurfaceWithoutInflatedCubeFalseHits(){
        var sweep=new GoliathTaunt.Sweep("Wrist_L",new Vec3(-4,0,0),new Vec3(4,0,0),.5);
        var target=new AABB(-.05,-.2,-.2,.05,.2,.2);var hit=GoliathTaunt.contact(sweep,target);
        assertNotNull(hit);assertEquals(-.05,hit.point().x,.00001);assertTrue(hit.fraction()>0&&hit.fraction()<1);
        assertNull(GoliathTaunt.contact(sweep,new AABB(-.1,.49,.49,.1,.6,.6)),"rounded swept sphere excludes expanded-AABB corner");
    }
    @Test void onlyCommittedRecoveryMayOutliveTargetAndNativeVoicesDoNotRepeatOrStopAtPoseExit(){
        assertTrue(GoliathCharge.requiresTarget(new GoliathCharge.Snapshot(3,0)));
        assertFalse(GoliathCharge.requiresTarget(new GoliathCharge.Snapshot(4,0)));
        assertFalse(GoliathCharge.requiresTarget(new GoliathCharge.Snapshot(12,0)));
        var sounds=GoliathTaunt.sounds();assertEquals(2,sounds.size());
        assertEquals(0,sounds.get(0).at());assertEquals(1,sounds.get(1).at());
        assertEquals(sounds.get(0).instance(),sounds.get(1).instance(),"source voices share native instance identity");
        for(var sound:sounds){assertEquals("Play",sound.trigger());assertEquals("Retrigger",sound.triggerType());assertEquals(1,sound.frequency());assertFalse(sound.stopOnExit());assertFalse(sound.autoRepeat());}
    }
    @Test void sourceRecoverySphereTangencyIsQueriedButAnActualGapIsStillMissed(){
        var window=GoliathTaunt.windows(new GoliathCharge.Snapshot(9,0),4,Vec3.ZERO,0,Vec3.ZERO,0)
            .stream().filter(w->w.id()==901&&w.sourceDamage()==400).findFirst().orElseThrow();
        var sweep=window.sweeps().get(0);assertEquals(sweep.from(),sweep.to(),"this source Hit is discrete, not an invented longer sweep");
        var c=sweep.to();double edge=c.x+sweep.radius();
        var target=new AABB(edge,c.y-.05,c.z-.05,edge+.1,c.y+.05,c.z+.05);
        assertFalse(new AABB(c,c).inflate(sweep.radius()).intersects(target));
        assertNotNull(GoliathTaunt.contact(sweep,target));assertTrue(GoliathTaunt.bounds(window).intersects(target));
        assertNull(GoliathTaunt.contact(sweep,target.move(.001,0,0)));
    }
}
