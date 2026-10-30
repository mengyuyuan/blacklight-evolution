package com.codex.blacklight.state;
import com.codex.blacklight.infection.TissueCore;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrganResearchTest {
    @Test void seventyTwoIdsAndUnknownFutureIdsNeverAliasOldBitPositions(){
        var progress=OrganResearch.EMPTY;
        for(var definition:com.codex.blacklight.infection.TissueOrganCatalog.ALL)progress=progress.discover(definition.id(),definition.number()%3+1);
        progress=progress.discover("othermod:future_organ",3);
        var decoded=OrganResearch.CODEC.parse(JsonOps.INSTANCE,OrganResearch.CODEC.encodeStart(JsonOps.INSTANCE,progress).getOrThrow(false, message -> { throw new AssertionError(message); })).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertEquals(progress,decoded);assertEquals(73,decoded.organs().size());
        for(var d:com.codex.blacklight.infection.TissueOrganCatalog.ALL){assertEquals(d.number()%3+1,decoded.sample(d.id()));assertEquals(0,decoded.rank(d.id()));}
        assertEquals(3,decoded.sample("othermod:future_organ"));
    }
    @Test void allOriginalBitPatternsMigrateWithoutBuyingOrLosingASample(){
        for(int bits=0;bits<1024;bits++){
            var json=new JsonObject();json.addProperty("samples",bits);json.addProperty("upgrades",1023);
            var migrated=OrganResearch.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(false, message -> { throw new AssertionError(message); });
            assertEquals(bits,migrated.samples());assertEquals(bits,migrated.upgrades());
            assertEquals(0,migrated.sample(TissueCore.CHELA));
        }
    }
    @Test void oldDataLoadsEmptyAndDiscoveredProgressRoundTrips(){
        assertEquals(OrganResearch.EMPTY,OrganResearch.CODEC.parse(JsonOps.INSTANCE,new JsonObject()).getOrThrow(false, message -> { throw new AssertionError(message); }));
        var progress=OrganResearch.EMPTY.discover(TissueCore.GRASP,3).buy(TissueCore.GRASP).discover(TissueCore.JAW,2);
        var encoded=OrganResearch.CODEC.encodeStart(JsonOps.INSTANCE,progress).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertEquals(progress,OrganResearch.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(false, message -> { throw new AssertionError(message); }));
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
