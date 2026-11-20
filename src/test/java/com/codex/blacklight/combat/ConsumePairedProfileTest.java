package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumePairedProfileTest {
    @Test void kneeImpactRetainsReceiverThroughLastClientInterpolationTick(){
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,ConsumeVariant.KNEE_HEAD);
        assertEquals(32,finish);
        assertTrue(finish<ConsumeSequence.duration(PowerForm.NONE,false,ConsumeVariant.KNEE_HEAD));
    }

    @Test void groundedReceiversDoNotStartReturningOnTheEffectFrame() {
        var normal=com.codex.blacklight.power.PowerForm.NONE;
        assertEquals(1.33333F,ConsumeReceiverScene.absorptionSeconds(normal,ConsumeVariant.KNEE_HEAD),1e-5F);
        assertEquals(3.83333F,ConsumeReceiverScene.absorptionSeconds(normal,ConsumeVariant.BEATDOWN),1e-5F);
        assertEquals(1.83333F,ConsumeReceiverScene.absorptionSeconds(normal,ConsumeVariant.HEAD_STOMP),1e-5F);
    }

    @Test void proneVillagerKeepsItsImpactPoseThroughAbsorption() {
        String stomp="normal_head_stomp";
        float contact=.9F/ConsumeReceiverScene.clipSeconds(com.codex.blacklight.power.PowerForm.NONE,ConsumeVariant.HEAD_STOMP);
        assertEquals(contact,ConsumePlacementTrack.villagerProgress(stomp,1F),1e-6F);
        assertEquals(contact/2,ConsumePlacementTrack.villagerProgress(stomp,contact/2));
        assertEquals(1F,ConsumePlacementTrack.villagerProgress("whip",1F));
    }

    @Test void packagedReceiverFreezesAtTheSplitWhileTheActorsClockContinues(){
        for(var form:new com.codex.blacklight.power.PowerForm[]{com.codex.blacklight.power.PowerForm.CLAWS,com.codex.blacklight.power.PowerForm.BLADE,com.codex.blacklight.power.PowerForm.WHIPFIST,com.codex.blacklight.power.PowerForm.MUSCLE_MASS}){
            float cut=ConsumeSequence.splitSeconds(form,false),seconds=ConsumeReceiverScene.clipSeconds(form);
            assertEquals(cut,ConsumeSequence.receiverProgress(form,1,false)*seconds,1e-5);
            assertEquals(1,ConsumeSequence.receiverProgress(form,1,true));
        }
        assertEquals(4.333333F,ConsumeReceiverScene.clipSeconds(com.codex.blacklight.power.PowerForm.CLAWS),1e-5);
    }
    @Test void largeClawActionUsesItsOwnPairedClipBeforeReceiverStateArrives() {
        var action=com.codex.blacklight.animation.PlayerAction.consumeLarge(com.codex.blacklight.power.PowerForm.CLAWS);
        assertEquals("consume_large_claws_actor",com.codex.blacklight.animation.MotionLibrary.resolve(action));
        assertNotEquals(com.codex.blacklight.animation.PlayerAction.consume(com.codex.blacklight.power.PowerForm.CLAWS),action);
    }
    @Test void splitPairsHaveValidFullBodyClocksAndFixedSocketLengths() throws Exception {
        for (String key : new String[]{"claws", "whip", "large_claws"}) for (String role : new String[]{"actor", "victim"}) {
            var json=JsonParser.parseString(Files.readString(Path.of("art/motion-profile/consume_"+key+"_"+role+".json"))).getAsJsonObject();
            assertNotNull(LocalVisualMotion.parse(json),key+"/"+role);
            for (var item:json.getAsJsonArray("animation")) {
                var track=item.getAsJsonObject();String joint=track.get("name").getAsString();
                if(joint.equals("Root")||joint.equals("Coord"))continue;
                double first=-1;
                for(var value:track.getAsJsonArray("transform")) {
                    var matrix=value.getAsJsonArray();double length=0;
                    for(int index:new int[]{3,7,11})length+=Math.pow(matrix.get(index).getAsDouble(),2);
                    if(first<0)first=length;
                    assertEquals(first,length,2e-6,key+"/"+role+" stretched socket "+joint);
                }
            }
        }
    }
    @Test void mountedReturnSettlesBeforeShortAndLongActionsRemoveTheReceiver(){
        for(var form:com.codex.blacklight.power.PowerForm.values()){
            int duration=ConsumeSequence.duration(form,true),finish=ConsumeSequence.completionTick(form,true);
            assertEquals(0F,ConsumeSequence.mountedReturn(0,duration,finish));
            assertEquals(1F,ConsumeSequence.mountedReturn(finish-2,duration,finish));
            assertEquals(1F,ConsumeSequence.mountedReturn(finish,duration,finish));
        }
    }
}
