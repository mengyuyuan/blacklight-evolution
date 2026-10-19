package com.codex.blacklight.client.compat;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.BoneTransform;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class YsmPoseFrameTest {
    @Test void articulatedPosesRoundTripIncludingGrip() throws Exception {
        try (var stream=getClass().getResourceAsStream("/assets/blacklight/models/mercer_v4.json")) {
            assertNotNull(stream);
            var bones=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("bones");
            var converter=new YsmPoseFrame(bones);
            var skeleton=BlacklightSkeleton.get();
            var random=new Random(20261018);
            for(int trial=0;trial<240;trial++) {
                var pose=new BlacklightPose();
                if(trial>0)for(var bone:skeleton.bones()) {
                    pose.getJointTransformData().put(bone.getName(),new BoneTransform(
                            new Vector3f((random.nextFloat()-.5F)*.03F,(random.nextFloat()-.5F)*.03F,(random.nextFloat()-.5F)*.03F),
                            new Quaternionf().rotationXYZ(random.nextFloat()*4-2,random.nextFloat()*4-2,random.nextFloat()*4-2),new Vector3f(1)));
                }
                var frame=converter.frame(skeleton.getPoseMatrices(pose),trial%2!=0);
                var channels=converter.channels(frame);
                var globals=new HashMap<String,Matrix4f>();
                var pivots=new HashMap<String,Vector3f>();
                for(int i=0;i<bones.size();i++) {
                    var bone=bones.get(i).getAsJsonObject();
                    var p=bone.getAsJsonArray("pivot");
                    var pivot=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                    var name=bone.get("name").getAsString();
                    var parent=bone.get("parent").isJsonNull()?null:bone.get("parent").getAsString();
                    var position=new Vector3f(pivot);
                    if(parent!=null)position.sub(pivots.get(parent));
                    position.add(new Vector3f(channels[i].position()).div(16));
                    var angle=new Vector3f(channels[i].rotation()).mul((float)(Math.PI/180));
                    var local=new Matrix4f().translation(position).rotateZYX(-angle.z,angle.y,-angle.x);
                    var actual=parent==null?local:new Matrix4f(globals.get(parent)).mul(local);
                    var expected=new Matrix4f(frame.skin()[i]).translate(pivot);
                    assertTrue(actual.equals(expected,.0004F),"Pose "+trial+" / "+name);
                    if(trial==0) {
                        assertEquals(0,channels[i].position().length(),.0001F,name);
                        assertEquals(0,channels[i].rotation().length(),.001F,name);
                    }
                    globals.put(name,actual);pivots.put(name,pivot);
                }
            }
        }
    }
}
