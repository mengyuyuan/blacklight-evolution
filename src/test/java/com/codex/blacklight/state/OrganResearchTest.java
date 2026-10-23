package com.codex.blacklight.state;
import com.codex.blacklight.infection.TissueCore;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrganResearchTest {
    @Test void oldDataLoadsEmptyAndDiscoveredProgressRoundTrips(){
        assertEquals(OrganResearch.EMPTY,OrganResearch.CODEC.parse(JsonOps.INSTANCE,new JsonObject()).getOrThrow());
        var progress=OrganResearch.EMPTY.discover(TissueCore.GRASP,3).buy(TissueCore.GRASP).discover(TissueCore.JAW,2);
        var encoded=OrganResearch.CODEC.encodeStart(JsonOps.INSTANCE,progress).getOrThrow();
        assertEquals(progress,OrganResearch.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
    }
    @Test void discoveringRepeatedOrWeakerSpecimensNeverLosesResearchOrRaisesPurchasedRank(){
        var first=OrganResearch.EMPTY.discover(TissueCore.CARAPACE,2);
        assertEquals(first,first.discover(TissueCore.CARAPACE,1));assertEquals(0,first.rank(TissueCore.CARAPACE));
        var bought=first.buy(TissueCore.CARAPACE).buy(TissueCore.CARAPACE);
        assertEquals(2,bought.rank(TissueCore.CARAPACE));assertEquals(bought,bought.buy(TissueCore.CARAPACE));
        assertEquals(0,new OrganResearch(0,-1).upgrades());
        assertEquals(0,bought.sample(TissueCore.MUSCLE));
    }
}
