package com.codex.blacklight.combat;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumePairedProfileTest {
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
}
