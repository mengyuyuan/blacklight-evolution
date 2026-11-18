package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StealthContextActionTest {
    @Test void ordinaryConsumeNeverRandomlySelectsAStealthScene(){
        var random=RandomSource.create(4219);
        var ordinary=new java.util.HashSet<Integer>();
        var stealth=new java.util.HashSet<Integer>();
        for(int i=0;i<2048;i++){
            int normal=ConsumeVariant.choose(PowerForm.NONE,false,random);
            assertFalse(ConsumeVariant.stealth(PowerForm.NONE,normal));ordinary.add(normal);
            int selected=ConsumeVariant.chooseStealth(random);
            assertTrue(ConsumeVariant.stealth(PowerForm.NONE,selected));stealth.add(selected);
            for(var form:PowerForm.values())if(form!=PowerForm.NONE)assertEquals(0,ConsumeVariant.normalize(form,selected));
            assertEquals(0,ConsumeVariant.choose(PowerForm.NONE,true,random));
        }
        assertEquals(java.util.Set.of(0,2,3,4),ordinary);
        assertEquals(java.util.Set.of(5,6,7,8,9),stealth);
    }
    @Test void slamUsesARealReleaseInsideEachSourceClock(){
        for(boolean two:new boolean[]{false,true}){
            assertTrue(BlockSlamMotion.available(two));
            int release=BlockSlamMotion.releaseTick(two);
            var clip=LocalVisualMotion.find(BlockThrowMotion.key(two,"slam")).orElseThrow();
            assertTrue(release>=2&&release<clip.durationTicks());
            assertEquals("alex_weap_2hnd_slam",clip.animation().getAsJsonObject("source").get("animation").getAsString());
        }
    }
}
