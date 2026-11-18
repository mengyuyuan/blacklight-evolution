package com.codex.blacklight.combat;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneSegmentDeathTest {
    @Test void fittedFirstTrackThenSlowerTailUsesOriginalEndFrame(){
        assertEquals(20,GreeneSegmentDeath.frame(0));
        assertEquals(66,GreeneSegmentDeath.frame(GreeneSegmentDeath.FIRST_SECONDS*20),.00001);
        assertEquals(75,GreeneSegmentDeath.frame((GreeneSegmentDeath.FIRST_SECONDS+.5F)*20),.00001);
        assertEquals(3.1444410108,GreeneSegmentDeath.seconds(),.00000001);
        assertEquals(63,GreeneSegmentDeath.ticks());assertEquals(95,GreeneSegmentDeath.frame(63));
    }
    @Test void copiedDrawableStartsAtTheActualBreakPoseAndIsNotAliased(){
        var copied=GreeneMotion.sample("idle",37,false);var original=GreeneSegmentDeath.savePose(copied);
        var first=GreeneSegmentDeath.pose(copied,0);
        for(int i=0;i<copied.length;i++)assertArrayEquals(copied[i],first[i]);
        first[0][3]+=100;assertEquals(original,GreeneSegmentDeath.savePose(copied));
        var blended=GreeneSegmentDeath.pose(copied,3);assertEquals(copied.length,blended.length);
        assertEquals(original,GreeneSegmentDeath.savePose(copied),"sampling must not overwrite the stored CopyDrawable pose");
    }
    @Test void namedPoseRoundTripsAndRejectsIncompleteOrNonfiniteSnapshots(){
        var pose=GreeneMotion.sample("enter",31,false);var tag=GreeneSegmentDeath.savePose(pose);var restored=GreeneSegmentDeath.loadPose(tag);
        assertNotNull(restored);for(int i=0;i<pose.length;i++)for(int axis=0;axis<9;axis++)assertEquals(axis<pose[i].length?pose[i][axis]:1F,restored[i][axis],"named pose channel "+i+"/"+axis);
        String bone=GreeneMotion.table().bones[0];var incomplete=tag.copy();incomplete.remove(bone);assertNull(GreeneSegmentDeath.loadPose(incomplete));
        var invalid=tag.copy();var row=invalid.getIntArray(bone);row[3]=Float.floatToIntBits(Float.NaN);invalid.putIntArray(bone,row);assertNull(GreeneSegmentDeath.loadPose(invalid));
        var zeroScale=tag.copy();row=zeroScale.getIntArray(bone);row[6]=0;zeroScale.putIntArray(bone,row);assertNull(GreeneSegmentDeath.loadPose(zeroScale));
    }
    @Test void independentDeathCurveMatchesOriginalThreeSlugJointFixtures()throws Exception{
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/greene-skin-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            int checked=0;
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("segmentsDeath")){
                var f=value.getAsJsonObject();float frame=f.get("frame").getAsFloat();if(frame<20)continue;
                float age=frame<=66?(frame-20)*GreeneSegmentDeath.FIRST_SECONDS*20/46
                        :(GreeneSegmentDeath.FIRST_SECONDS+(frame-66)/(30*GreeneSegmentDeath.TAIL_SPEED))*20;
                var pose=GreeneSegmentDeath.pose(null,age);var center=f.getAsJsonArray("centerModelBlocks");
                var p=new org.joml.Vector3f(center.get(0).getAsFloat(),center.get(1).getAsFloat(),center.get(2).getAsFloat());
                var expected=new org.joml.Matrix4f().rotateY((float)Math.PI).transformPosition(p.mul(GreenePresentation.SCALE));
                String region=f.get("region").getAsString();int index=f.get("regionCube").getAsInt();
                var actual=GreeneContact.regions(pose,Vec3.ZERO,0,0).stream().filter(r->r.bone().equals(region)).findFirst().orElseThrow().boxes().get(index).center;
                assertTrue(actual.distanceTo(new Vec3(expected.x,expected.y+.01,expected.z))<.0002,region+" original death frame "+frame);checked++;
            }
            assertEquals(150,checked);
        }
    }
}
