package com.codex.blacklight;

import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.power.*;
import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponContentTest {
    @Test void eachMoveHasFiniteFullBodyAnimationWithMatchingDuration()throws Exception{
        for(var move:WeaponMove.values()){
            var authored=com.codex.blacklight.combat.LocalWeaponMotion.find(move.id);
            if(authored.isPresent()){
                // Matrix clips carry validated contact/charge metadata and include
                // the final Coord track; their duration includes the link interval.
                var definition=authored.orElseThrow();
                assertEquals(move.duration,definition.durationTicks());
                assertEquals(21,definition.animation().getAsJsonArray("animation").size());
                continue;
            }
            var path=Path.of("src/main/resources/assets/blacklight/native_animations/biped/combat/"+move.id+".json");
            var tracks=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("animation");
            var joints=new java.util.HashSet<String>();
            for(var element:tracks){var track=element.getAsJsonObject();assertTrue(joints.add(track.get("name").getAsString()));var time=track.getAsJsonArray("time");var frames=track.getAsJsonArray("transform");assertEquals(time.size(),frames.size());
                assertEquals(0,time.get(0).getAsDouble(),.001);assertEquals(move.duration/20.,time.get(time.size()-1).getAsDouble(),.011,move.id);
                double previous=-1;for(int i=0;i<frames.size();i++){assertTrue(time.get(i).getAsDouble()>previous);previous=time.get(i).getAsDouble();double norm=0;for(var q:frames.get(i).getAsJsonObject().getAsJsonArray("rot")){assertTrue(Double.isFinite(q.getAsDouble()));norm+=q.getAsDouble()*q.getAsDouble();}assertEquals(1,norm,.0001);}
            }
            assertEquals(20,joints.size(),move.id);assertTrue(joints.containsAll(java.util.Set.of("Head","Root","Hand_R","Hand_L","Leg_R","Leg_L")));
        }
    }
    @Test void utilitySectorsResolveToTheirDisplayedCenters(){
        var expected=java.util.List.of(PowerForm.SHIELD,PowerForm.ARMOR,PowerForm.THERMAL_VISION,PowerForm.INFECTED_VISION,PowerForm.SOVEREIGN_WINGS);
        for(int i=0;i<PowerWheel.size(true);i++){double angle=-Math.PI/2+i*Math.PI*2/PowerWheel.size(true);assertEquals(i,PowerWheel.sector(Math.cos(angle)*80,Math.sin(angle)*80,30,120,true));assertEquals(expected.get(i),PowerWheel.form(true,i));}
    }
}
