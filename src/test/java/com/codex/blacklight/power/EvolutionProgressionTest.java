package com.codex.blacklight.power;

import com.codex.blacklight.state.*;
import java.util.*;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolutionProgressionTest {
    private BlacklightState full(){
        var state=new BlacklightState();state.infect();state.addEvolutionPoints(Integer.MAX_VALUE);
        for(var form:PowerForm.values())if(form!=PowerForm.NONE)state.buy(form);
        for(var move:MovementUpgrade.values())state.buyMovementUpgrade(move);
        for(var claw:ClawUpgrade.values())state.buyClawUpgrade(claw);
        for(var skill:CombatSkill.values())if(!skill.adaptivePassive())state.buyCombatSkill(skill);
        return state;
    }
    private GrowthProgress growth(){var p=GrowthProgress.EMPTY;for(var g:GrowthUpgrade.values())p=p.buy(g);return p;}
    @Test void FiniteCurriculumExcludesCircularAndUnboundedRequirements(){
        var state=full();assertTrue(BaseEvolutionCatalog.complete(state,growth()));
        assertFalse(state.hasCombatSkill(CombatSkill.ADAPTATION));
        assertEquals(63,BaseEvolutionCatalog.REQUIREMENTS.size());
        assertTrue(BaseEvolutionCatalog.REQUIREMENTS.stream().noneMatch(r->r.id().startsWith("infinite_")||r.id().equals("adaptation")||r.id().equals(RicochetRules.ID)));
        assertFalse(BaseEvolutionCatalog.complete(state,GrowthProgress.EMPTY));
    }
    @Test void EveryAttributeMustPassIndividually(){
        var levels=new HashMap<String,Long>();for(var a:EvolutionProgression.CORE_ATTRIBUTES)levels.put(a.id,20L);
        assertTrue(EvolutionProgression.everyAttribute(new InfiniteProgress(levels),20));
        levels.put(EvolutionAttribute.MOBILITY.id,19L);levels.put(EvolutionAttribute.VITALITY.id,1_000_000L);
        assertFalse(EvolutionProgression.everyAttribute(new InfiniteProgress(levels),20));
    }
    @Test void QualificationsRoundTripAndHigherStagesKeepEarlierQualifications(){
        var progress=EvolutionMilestones.EMPTY.grant(EvolutionMilestones.PERCENT);
        assertTrue(progress.has(EvolutionMilestones.INFINITE)&&progress.has(EvolutionMilestones.PASSIVES));
        var encoded=EvolutionMilestones.CODEC.encodeStart(JsonOps.INSTANCE,progress).getOrThrow();
        assertEquals(progress,EvolutionMilestones.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
        assertEquals(0,new EvolutionMilestones(128).flags());
    }
}
