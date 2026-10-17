package com.codex.blacklight.animation;

import com.google.gson.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class WingFoldEnvelopeTest {
    private static Vector3f vec(JsonArray a){return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    @Test void bothFoldOrdersKeepTheEntireSkinnedSurfaceBehindTheActorAndAboveGround() throws Exception {
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(String variant:List.of("left_over","right_over")) for(float air:new float[]{0,.25F,.5F,.75F,1}) {
                var frames=new HashMap<String,Matrix4f>();var pivots=new HashMap<String,Vector3f>();var skin=new HashMap<String,Matrix4f>();
                for(var value:data.getAsJsonArray("bones")) {
                    var b=value.getAsJsonObject();String name=b.get("name").getAsString();var pivot=vec(b.getAsJsonArray("pivot"));
                    var delta=new Vector3f(pivot);var frame=new Matrix4f();
                    if(!b.get("parent").isJsonNull()){String parent=b.get("parent").getAsString();frame.set(frames.get(parent));delta.sub(pivots.get(parent));}
                    var q=data.getAsJsonObject("fold_poses").getAsJsonObject(variant).getAsJsonArray(name);
                    var folded=new Quaternionf(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat());
                    frame.translate(delta).rotate(SovereignWingMotion.articulation(name,SovereignWingMotion.sample(0,1,air,0,0,0,0,0,0),new Vector3f(0,0,1),air,folded));
                    frames.put(name,frame);pivots.put(name,pivot);skin.put(name,new Matrix4f(frame).translate(new Vector3f(pivot).negate()));
                }
                if(air==0) for(int i=1;i<=4;i++) {
                    Vector3f[] tips=new Vector3f[2];
                    for(int side=0;side<2;side++) {
                        String name=(side==0?"left":"right")+"_tip_"+i;
                        var path=data.getAsJsonObject("skin_paths").getAsJsonArray(name);
                        tips[side]=skin.get(name).transformPosition(vec(path.get(path.size()-1).getAsJsonArray()));
                    }
                    assertTrue(tips[0].x<0&&tips[1].x>0,"Folded wings must overlap across the spine");
                    float depth=tips[0].z-tips[1].z;
                    assertTrue(variant.equals("left_over")?depth>2:depth< -2,"Random order must change the physical outer layer");
                }
                for(var value:data.getAsJsonArray("elements")) {
                    var e=value.getAsJsonObject();String name=e.get("name").getAsString();
                    for(var vertex:e.getAsJsonObject("vertices").entrySet()) {
                        var p=vec(vertex.getValue().getAsJsonArray());var w=e.getAsJsonObject("_weights").getAsJsonArray(vertex.getKey());
                        var a=skin.get(w.get(0).getAsString()).transformPosition(new Vector3f(p));
                        var b=skin.get(w.get(1).getAsString()).transformPosition(new Vector3f(p));a.lerp(b,w.get(2).getAsFloat());
                        assertTrue(a.isFinite(),name);
                        if(air==0)assertTrue(a.z>=1.5F,variant+" crosses torso/front: "+name+" "+a);
                        if(air==0)assertTrue(a.z<13.5F,variant+" folds into a rear basket: "+name+" "+a);
                        if(air==0)assertTrue(a.y>.8F,variant+" enters floor: "+name+" "+a);
                        assertFalse(java.lang.Math.abs(a.x)<4.6F&&a.y>23.4F&&a.y<32.6F&&a.z<4.6F,"head: "+name);
                    }
                }
            }
        }
    }
    @Test void cameraPitchAloneControlsClimbWithoutJumpOrSneak() {
        for(float pitch:new float[]{-60,60}) {
            var v=new com.codex.blacklight.movement.WingFlightPhysics.Velocity(0,0,0);
            for(int tick=0;tick<100;tick++)v=com.codex.blacklight.movement.WingFlightPhysics.step(v.x(),v.y(),v.z(),1,0,false,false,true,0,pitch,false);
            float travel=SovereignFlightMotion.pathPitch(java.lang.Math.hypot(v.x(),v.z()),v.y());
            assertEquals(-pitch,travel,.001);
        }
    }
    @Test void climbAndDivePitchFollowTravelInsteadOfSaturatingAtFiveDegrees() {
        for(float degrees:new float[]{-60,60}) {
            double rad=java.lang.Math.toRadians(degrees);
            float pitch=SovereignFlightMotion.pathPitch(java.lang.Math.cos(rad),java.lang.Math.sin(rad));
            assertEquals(degrees,pitch,.001);
            var pose=SovereignFlightMotion.airborne(0,1,1,0,0,0,true);
            var before=new Quaternionf(pose.get("Root").rotation());
            SovereignFlightMotion.followFlightPath(pose,pitch,1,1,0);
            var expected=new Quaternionf().rotationX((float)rad).mul(before);
            assertEquals(1,java.lang.Math.abs(expected.dot(pose.get("Root").rotation())),.00001);
        }
        assertEquals(0,SovereignFlightMotion.pathPitch(0,0));
        assertEquals(0,SovereignFlightMotion.pathPitch(Double.NaN,1));
        assertEquals(80,SovereignFlightMotion.pathPitch(0,1));
    }
}
