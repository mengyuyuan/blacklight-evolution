package com.codex.blacklight.state;

import com.codex.blacklight.power.EvolutionAttribute;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfiniteProgressTest {
    @Test void repeatableRanksPassOldTierBoundariesWithoutAffectingOtherAttributes(){
        var progress=InfiniteProgress.EMPTY;
        for(int i=0;i<1001;i++)progress=progress.buy(EvolutionAttribute.VITALITY);
        assertEquals(1001,progress.rank(EvolutionAttribute.VITALITY));assertEquals(0,progress.rank(EvolutionAttribute.STRENGTH));
    }
    @Test void codecKeepsLongRanksAndFiltersUnknownOrNegativeData(){
        var original=new InfiniteProgress(Map.of("infinite_vitality",9_000_000_000L,"infinite_strength",-9L,"unknown",100L));
        var json=InfiniteProgress.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow();
        var copy=InfiniteProgress.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        assertEquals(original,copy);assertEquals(1,copy.ranks().size());
        assertEquals(9_000_000_000L,copy.rank(EvolutionAttribute.VITALITY));
    }
    @Test void NumericBoundaryNeverWrapsTheLevelBackToNegative(){
        var max=new InfiniteProgress(Map.of("infinite_vitality",Long.MAX_VALUE));assertSame(max,max.buy(EvolutionAttribute.VITALITY));
        for(var a:EvolutionAttribute.values())assertTrue(Double.isFinite(a.effect(Long.MAX_VALUE)));
    }
    @Test void DamageAndMobilityGrowWithoutChangingTheAnimationClock(){
        assertEquals(1.02,EvolutionAttribute.attackMultiplier(1),1e-12);assertEquals(1.04,EvolutionAttribute.skillMultiplier(1),1e-12);
        assertTrue(EvolutionAttribute.defenseMultiplier(1000)<EvolutionAttribute.defenseMultiplier(100));
        assertTrue(EvolutionAttribute.defenseMultiplier(Long.MAX_VALUE)>0);
        assertTrue(EvolutionAttribute.mobilityMultiplier(1001)>EvolutionAttribute.mobilityMultiplier(1000));
        assertTrue(EvolutionAttribute.mobilityMultiplier(1000)<1.2,"mobility must not restore unreadably fast sprint steps");
    }
}
