package com.codex.blacklight.combat;

import com.codex.blacklight.city.HydraMotion;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HydraDeathTest {
    @Test void thermobaricGroupHasItsOwnClockAndNeverEntersTheNormalSink(){
        assertEquals(20,HydraDeath.THERMOBARIC_EFFECT_TICKS);assertEquals(24,HydraDeath.THERMOBARIC_HIDE_TICKS);assertEquals(40,HydraDeath.duration(HydraDeath.THERMOBARIC));
        assertFalse(HydraDeath.bodyHidden(HydraDeath.THERMOBARIC,23.99F));assertTrue(HydraDeath.bodyHidden(HydraDeath.THERMOBARIC,24));
        assertFalse(HydraDeath.bodyHidden(HydraDeath.NORMAL,102));
        assertEquals(30,HydraDeath.frame(HydraDeath.THERMOBARIC,20));assertEquals(36,HydraDeath.frame(HydraDeath.THERMOBARIC,24));
        assertEquals(60,HydraDeath.frame(HydraDeath.THERMOBARIC,40));assertEquals(60,HydraDeath.frame(HydraDeath.THERMOBARIC,103));
        var sourceDeath=HydraDeath.pose(40);var thermalTerminal=HydraDeath.pose(HydraDeath.THERMOBARIC,103);
        for(int i=0;i<sourceDeath.length;i++)assertArrayEquals(sourceDeath[i],thermalTerminal[i],"special branch ends on death frame60, never exit frame54");
        assertTrue(HydraDeath.validKind(0)&&HydraDeath.validKind(1));assertFalse(HydraDeath.validKind(-1)||HydraDeath.validKind(2));
    }
    @Test void figureStopsDeathAt98AndStartsExitAt27RatherThanPlayingTheWholeClip(){
        assertEquals(66,HydraDeath.DIE_TICKS);assertEquals(37,HydraDeath.SINK_TICKS);assertEquals(103,HydraDeath.TOTAL_TICKS);
        assertEquals(0,HydraDeath.frame(0));assertEquals(98,HydraDeath.frame(65.9F));
        assertEquals(27,HydraDeath.frame(66));assertEquals(30,HydraDeath.frame(68));
        assertEquals(54,HydraDeath.frame(84));assertEquals(54,HydraDeath.frame(102));
    }
    @Test void sourceDeathJointFixturesSurviveTheExistingHydraRigAdaptation()throws Exception{
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/hydra-death-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var motion=HydraMotion.INSTANCE;int checked=0;
            for(var entry:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fixtures")){
                var f=entry.getAsJsonObject();String name=f.get("bone").getAsString();int index=Arrays.asList(motion.bones).indexOf(name);
                // These are the unchanged authored target bone pivots, not a
                // second copy of the exported animation channel values.
                Vector3f pivot=new Vector3f();boolean found=false;
                try(var geo=Objects.requireNonNull(getClass().getResourceAsStream("/assets/blacklight/geo/blacklight_hydra_mc.geo.json"));var gr=new InputStreamReader(geo,StandardCharsets.UTF_8)){
                    for(var bone:JsonParser.parseReader(gr).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                        var b=bone.getAsJsonObject();if(!b.get("name").getAsString().equals(name))continue;var p=b.getAsJsonArray("pivot");pivot.set(-p.get(0).getAsFloat()/16,p.get(1).getAsFloat()/16,p.get(2).getAsFloat()/16);found=true;break;
                    }
                }
                assertTrue(found&&index>=0);var actual=motion.transforms(HydraDeath.pose(f.get("frame").getAsFloat()/1.5F))[index].transformPosition(pivot);
                var expected=f.getAsJsonArray("modelPoint");assertEquals(expected.get(0).getAsDouble(),actual.x,.0001,name);assertEquals(expected.get(1).getAsDouble(),actual.y,.0001,name);assertEquals(expected.get(2).getAsDouble(),actual.z,.0001,name);checked++;
            }
            assertEquals(16,checked);
        }
    }
    @Test void sinkBlendsFromActualFinalDeathPoseAndEndsCompletelyUnderground(){
        var motion=HydraMotion.INSTANCE;var before=motion.transforms(HydraDeath.pose(65.9F));var entry=motion.transforms(HydraDeath.pose(66));
        for(int i=0;i<before.length;i++)assertTrue(before[i].equals(entry[i],.00001F),"sink starts at terminal death pose");
        var terminal=HydraDeath.pose(102);
        assertTrue(HydraContact.regions(terminal,Vec3.ZERO,0,1).stream().allMatch(r->r.bounds().maxY<0),"visible cuboids have sunk before removal");
        var a=HydraDeath.pose(40);a[0][4]=1000;assertNotEquals(1000,HydraDeath.pose(40)[0][4],"sampling must not mutate source rows");
    }
}
