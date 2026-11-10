package com.codex.blacklight.infection;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedSamplePresetTest {
    private static EvolvedMapping.Table table() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/data/blacklight/blacklight/evolved_mapping/default.json"))){return EvolvedMapping.read(reader);}
    }
    @Test void EveryArchetypePresetResolvesToItselfThroughTheShippedTable() throws Exception {
        var table=table();
        for(var type:EvolvedArchetype.values()){
            var genes=new TissueInheritance(EvolvedSamplePreset.organs(type),6,2100,false,null,1,6);
            assertEquals(6,EvolvedSamplePreset.organs(type).size(),type.key());
            for(long seed=0;seed<64;seed++){
                var choice=table.choose(genes,seed,EvolvedArchetype.types());
                assertEquals(type.id(),choice.type(),type.key()+" seed "+seed);assertFalse(choice.fallback());
            }
        }
    }
    @Test void KeysRoundTripAndDefaultKeepsTheMixedSample(){
        for(var key:EvolvedSamplePreset.keys())assertEquals(key,EvolvedSamplePreset.byKey(key).orElseThrow().key());
        assertTrue(EvolvedSamplePreset.byKey("nope").isEmpty());
        assertEquals(6,EvolvedSamplePreset.organs(null).size());
    }
}