package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import com.google.gson.JsonParser;
import java.nio.file.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextActionProfileTest {
    @Test void newProfilesPassTheActualRuntimeLoaders() throws Exception {
        var root=Path.of("art/motion-profile");
        var stomp=LocalWeaponMotion.parse(JsonParser.parseString(Files.readString(root.resolve("ground_stomp.json"))).getAsJsonObject());
        assertTrue(stomp.contactStartTick()>2&&stomp.contactEndTick()<stomp.durationTicks());
        for(String key:new String[]{"leg_slam","normal_head_stomp","normal_beatdown","normal_knee_head"}){
            for(String role:new String[]{"actor","victim"})
                assertNotNull(LocalVisualMotion.parse(JsonParser.parseString(Files.readString(root.resolve("consume_"+key+"_"+role+".json"))).getAsJsonObject()));
            assertEquals(46,ConsumePlacementTrack.contactFrame(key,.5F).orElseThrow().length);
            assertEquals(51,ConsumePlacementTrack.projection(key,.5F).orElseThrow().length);
        }
    }
    @Test void slamBlocksInputButNeverStartsAbsorptionAndRecoveryDoesNotLoop(){
        var visual=new ConsumeVisual(1,100,HeldSlamMotion.duration(),PowerForm.NONE.ordinal(),ConsumeVisual.GRAB_ATTACK,0,Vec3.ZERO);
        assertTrue(ConsumeVisual.blocksInput(visual.stage()));assertTrue(ConsumeVisual.blocksTravel(visual.stage()));
        assertFalse(visual.consuming());assertTrue(GrappleMotion.active(visual));
        assertEquals(0,HeldSlamMotion.progress(visual,100,0));
        assertEquals(1,HeldSlamMotion.progress(visual,1000,0));
        assertTrue(HeldSlamMotion.IMPACT_TICK<visual.duration());
    }
    @Test void bareSceneIdsAreStableAndCannotLeakIntoWeaponOrLargeScenes(){
        assertEquals("normal",ConsumeSequence.key(PowerForm.NONE,0));
        assertEquals("normal_head_stomp",ConsumeSequence.key(PowerForm.NONE,2));
        assertEquals("normal_beatdown",ConsumeSequence.key(PowerForm.NONE,3));
        assertEquals("normal_knee_head",ConsumeSequence.key(PowerForm.NONE,4));
        assertEquals(0,ConsumeVariant.normalize(PowerForm.NONE,1));
        assertEquals(0,ConsumeVariant.normalize(PowerForm.CLAWS,2));
        for(int variant:ConsumeVariant.variants(PowerForm.NONE))
            assertEquals("large",ConsumeSequence.clipKey(PowerForm.NONE,true,variant));
        assertEquals(WeaponMove.BULLETDIVE_DROP.ordinal()+1,WeaponMove.GROUND_STOMP.ordinal());
    }
}
