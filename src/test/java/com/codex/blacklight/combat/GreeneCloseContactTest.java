package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneCloseContactTest {
    @Test void bothSlugTipsFollowIndependentOriginalMatrixFixturesIncludingLocalOffset(){throwsFixtureCheck();}
    private static void throwsFixtureCheck(){
        try(var in=GreeneCloseContactTest.class.getResourceAsStream("/greene-close-reference.json");var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var rows=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fixtures");int checked=0;
            for(var value:rows){var row=value.getAsJsonObject();var side=row.get("clip").getAsString().equals("close_left")?GreeneCloseAttack.Side.LEFT:GreeneCloseAttack.Side.RIGHT;
                var pose=GreeneCloseAttack.sample(side,row.get("frame").getAsFloat());var point=row.getAsJsonArray("pointModelBlocks");
                var expected=new Vec3(point.get(0).getAsDouble(),point.get(1).getAsDouble(),point.get(2).getAsDouble()).scale(GreenePresentation.SCALE);
                var origin=new Vec3(17,64,-9);var actual=GreeneCloseContact.sphere(side,GreeneCloseAttack.Track.TIP,pose,origin,180);
                assertTrue(actual.center().distanceTo(expected.add(origin).add(0,.01,0))<.0001,row.toString());
                assertEquals(4*GreeneMotion.table().sourceUnitBlocks(),actual.radius(),1e-8);checked++;
            }assertEquals(14,checked);
        }catch(Exception e){throw new AssertionError(e);}
    }
    @Test void rootFrontWindowsUseSourceMinusZAndOnePresentationScale(){
        var pose=GreeneMotion.sample("idle",0,false);double unit=GreeneMotion.table().sourceUnitBlocks();
        var sphere=GreeneCloseContact.sphere(GreeneCloseAttack.Side.RIGHT,GreeneCloseAttack.Track.OUTER,pose,Vec3.ZERO,180);
        assertEquals(-6*unit,sphere.center().z,1e-5);assertEquals(7*unit,sphere.radius(),1e-8);
        var turned=GreeneCloseContact.sphere(GreeneCloseAttack.Side.RIGHT,GreeneCloseAttack.Track.OUTER,pose,Vec3.ZERO,90);
        assertEquals(sphere.center().z,turned.center().x,1e-5);assertEquals(-sphere.center().x,turned.center().z,1e-5);
    }
    @Test void fixedOverlapCanMissAndDoesNotBecomePreviousPositionSweep(){
        var sphere=new GreeneCloseContact.Sphere(new Vec3(8,2,0),1);
        assertNull(GreeneCloseContact.contact(sphere,new AABB(3,1,-.5,4,3,.5)));
        assertNotNull(GreeneCloseContact.contact(sphere,new AABB(8.5,1,-.5,9,3,.5)));
    }
}
