package com.codex.blacklight.infection;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedMappingTest {
    private static final String B="blacklight:blade",A="blacklight:armor",FIRST="blacklight:blade_evolved",SECOND="blacklight:armored_evolved";
    private static TissueInheritance genes(double blade,double armor){
        var organs=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        if(blade>0)organs.put(TissueCore.BLADE,new TissueInheritance.Organ(blade,0,0,0,1,1));
        if(armor>0)organs.put(TissueCore.CARAPACE,new TissueInheritance.Organ(armor,0,0,0,0,2));
        return new TissueInheritance(organs,6,0,false,null,1,6);
    }
    private static EvolvedMapping.Table table(double bladeWeight,double armorWeight){
        return new EvolvedMapping.Table(Map.of(B,"刃化",A,"重甲"),Map.of(TissueCore.BLADE.key(),Map.of(B,1D),TissueCore.CARAPACE.key(),Map.of(A,1D)),
                Map.of(FIRST,new EvolvedMapping.Candidate(FIRST,FIRST,Set.of(B),bladeWeight),SECOND,new EvolvedMapping.Candidate(SECOND,SECOND,Set.of(A),armorWeight)),FIRST);
    }
    @Test void StrongestAcquiredBiasWinsEvenWhenTheWeakerCandidateHasMuchHigherWeight(){
        var table=table(1,1_000_000);
        for(long seed=0;seed<200;seed++){
            var choice=table.choose(genes(500,100),seed,Set.of(FIRST,SECOND));
            assertEquals(FIRST,choice.form());assertEquals(B,choice.bias());assertFalse(choice.fallback());
        }
    }
    @Test void TiedHighestBiasesBothParticipateAndCandidateOrderIsStable(){
        var table=table(1,1);var seen=new HashSet<String>();
        for(long seed=0;seed<200;seed++){var choice=table.choose(genes(100,100),seed,Set.of(FIRST,SECOND));seen.add(choice.form());assertEquals(choice,table.choose(genes(100,100),seed,Set.of(SECOND,FIRST)));}
        assertEquals(Set.of(FIRST,SECOND),seen);assertEquals(table.fingerprint(),table(1,1).fingerprint());
    }
    @Test void SameBiasHasAWeightedPoolInsteadOfOneHardCodedForm(){
        var table=new EvolvedMapping.Table(Map.of(B,"刃化"),Map.of(TissueCore.BLADE.key(),Map.of(B,1D)),Map.of(
                FIRST,new EvolvedMapping.Candidate(FIRST,FIRST,Set.of(B),1),SECOND,new EvolvedMapping.Candidate(SECOND,SECOND,Set.of(B),3)),FIRST);
        int second=0;for(int seed=0;seed<4000;seed++)if(table.choose(genes(100,0),seed,Set.of(FIRST,SECOND)).form().equals(SECOND))second++;
        assertTrue(second>2800&&second<3200,"three-to-one pool remains stochastic: "+second);
    }
    @Test void UnavailableFutureTypesNeverProduceABrokenEntityAndKeepTheActualDominantBias(){
        var choice=table(1,1).choose(genes(1,500),14,Set.of(FIRST));
        assertTrue(choice.fallback());assertEquals(FIRST,choice.form());assertEquals(A,choice.bias());
        assertThrows(IllegalStateException.class,()->table(1,1).choose(genes(1,500),14,Set.of()));
    }
    @Test void FormingOrUnpaidOrgansDoNotVoteAndHugeStrengthDoesNotOverflow(){
        var organs=Map.of(TissueCore.BLADE,new TissueInheritance.Organ(10,0,0,0,1,1),TissueCore.CARAPACE,new TissueInheritance.Organ(10000,1,20,30,0,2));
        var choice=table(1,1).choose(new TissueInheritance(organs,6,0,false,null,1,6),2,Set.of(FIRST,SECOND));
        assertEquals(FIRST,choice.form());assertFalse(choice.scores().containsKey(A));
        var huge=table(1,1).choose(genes(Double.MAX_VALUE,Double.MAX_VALUE/2),2,Set.of(FIRST,SECOND));
        assertEquals(FIRST,huge.form());assertTrue(huge.scores().values().stream().allMatch(Double::isFinite));
    }
    @Test void FrozenChoiceRoundTripsWithoutConsultingTodaysTable(){
        var choice=table(1,1).choose(genes(500,1),1,Set.of(FIRST,SECOND));var saved=EvolvedChoiceCodec.write(choice);
        assertEquals(choice,EvolvedChoiceCodec.read(saved).orElseThrow());
        var edited=table(1,100);assertNotEquals(choice.table(),edited.fingerprint());
        assertEquals(choice,EvolvedChoiceCodec.read(saved).orElseThrow());
        saved.putString("Form","../../broken");assertTrue(EvolvedChoiceCodec.read(saved).isEmpty());
    }
    @Test void BuiltinMappingCoversAll72DesignIdsWithoutPretendingTheyAreImplemented()throws Exception{
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/data/blacklight/blacklight/evolved_mapping/default.json"))){
            var table=EvolvedMapping.read(reader);assertEquals(72,table.organs().size());
            for(var design:TissueOrganCatalog.ALL)assertTrue(table.organs().containsKey(design.id()),design.id());
            assertEquals(8,table.forms().size());assertEquals(35,TissueOrganCatalog.ALL.stream().filter(TissueOrganCatalog.Definition::implemented).count());
            assertEquals(8,table.biases().size());
        }
    }
    @Test void InvalidWeightsAndUnknownAxesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->table(Double.NaN,1));
        assertThrows(IllegalArgumentException.class,()->new EvolvedMapping.Table(Map.of(B,"刃"),Map.of(TissueCore.BLADE.key(),Map.of(A,1D)),Map.of(FIRST,new EvolvedMapping.Candidate(FIRST,FIRST,Set.of(B),1)),FIRST));
    }
}
