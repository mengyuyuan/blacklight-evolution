package com.codex.blacklight.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HunterCounterTest {
    @Test void sourceRootFixturesUseSeparatePhaseOriginsAndTheExistingRigScale()throws Exception{
        try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/hunter-counter-root-reference.json"));
            var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
            int count=0;
            for(var entry:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fixtures")){
                var f=entry.getAsJsonObject();boolean release=f.get("clip").getAsString().equals("release");float frame=f.get("sourceFrame").getAsFloat();
                float age=release?8+(frame-3)/1.5F:frame/(1.5F*.9F);
                var point=HunterCounterMotion.offset(release?CreatureAttack.BARRAGE:CreatureAttack.COUNTER,release,age);
                var expected=f.getAsJsonArray("yawZeroOffset");assertEquals(expected.get(0).getAsDouble(),point.x,.00001);assertEquals(0,point.y);assertEquals(expected.get(2).getAsDouble(),point.z,.00001);count++;
            }
            assertEquals(8,count);
        }
        assertEquals(0,HunterCounterMotion.offset(CreatureAttack.BARRAGE,true,8).lengthSqr(),"release does not jump from absolute counterZ-6.516 to absolute barrageZ+.267");
        assertEquals(Vec3.ZERO,HunterCounterMotion.offset(CreatureAttack.BARRAGE,false,57),"standalone barrage retains its separate existing movement");
        var forward=HunterCounterMotion.step(CreatureAttack.COUNTER,false,0,32,90);
        assertEquals(-1.0545467285624959,forward.x,.00001);assertEquals(0,forward.z,.00001);
    }
    @Test void skinPoseDoesNotAlreadyContainTheHorizontalRootTravel(){
        for(var move:new CreatureAttack[]{CreatureAttack.COUNTER,CreatureAttack.BARRAGE})for(int age=0;age<=move.ticks;age++)
            for(var row:CreaturePoseTable.HUNTER.sample(move.clip,age)){
                assertEquals(0,row[3],.000001);assertEquals(0,row[5],.000001);
            }
    }
    @Test void sourceCounterOpensWithTheLeftWristBeforeAnyBarrageWindow(){
        var counter=CreatureAttack.COUNTER;var window=counter.windows()[0];
        assertTrue(window.left());assertEquals(1,counter.windows().length);
        assertEquals(1.066667F,window.from(),.00001);assertEquals(1.166667F,window.to(),.00001);
        var standing=new AABB(-.3,0,1.2,.3,1.95,1.8);
        assertNull(HunterContactPose.contact(counter,window,0,20,Vec3.ZERO,Vec3.ZERO,0,standing));
        assertNotNull(HunterContactPose.contact(counter,window,21,22,Vec3.ZERO,Vec3.ZERO,0,standing),
                "decoded left wrist crosses an ordinary standing body");
        assertNull(HunterContactPose.contact(counter,window,24,25,Vec3.ZERO,Vec3.ZERO,0,standing));
        assertEquals(CreatureAttack.COUNTER,CreatureAttack.choose(1.5,1));
    }
    @Test void releaseKeepsTheLastCounterPoseThenFinishesBlendingBeforeContact(){
        float entry=CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK;
        var opening=HunterContactPose.pose(CreatureAttack.COUNTER,CreatureAttack.COUNTER.ticks);
        var join=HunterContactPose.pose(CreatureAttack.BARRAGE,entry,true);
        for(int i=0;i<opening.length;i++)assertArrayEquals(opening[i],join[i],.00001F);
        float contact=CreatureAttack.BARRAGE.windows()[0].from()*20;
        assertTrue(contact>=entry+CreatureAttack.COUNTER_BARRAGE_BLEND_TICKS);
        var normal=HunterContactPose.pose(CreatureAttack.BARRAGE,contact);
        var released=HunterContactPose.pose(CreatureAttack.BARRAGE,contact,true);
        for(int i=0;i<normal.length;i++)assertArrayEquals(normal[i],released[i],.00001F);
    }
    @Test void source75To150RatioDoesNotDoubleTheExistingBarrageBudget(){
        assertEquals(HunterBalance.damageMultiplier(CreatureAttack.BARRAGE)/2,HunterBalance.damageMultiplier(CreatureAttack.COUNTER));
        assertEquals(HunterBalance.infectedDamage(10000,CreatureAttack.BARRAGE,1)/2,
                HunterBalance.infectedDamage(10000,CreatureAttack.COUNTER,1));
        assertEquals(5,CreatureAttack.DODGE.ordinal(),"old serialized action ids remain stable");
    }
}
