package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColonySupplyTest {
    private static ColonySupply fed(int count){var s=new ColonySupply();for(int i=0;i<count;i++){assertTrue(s.offer(TissueDigestion.Kind.MEAT,64));s.digest(200);}return s;}
    @Test void timeAndOldSavesCannotGenerateMaterials(){var s=ColonySupply.read(new CompoundTag());for(int i=0;i<100;i++){s.digest(200);assertFalse(s.maintain(5,0));}assertEquals(0,s.reserve());assertFalse(s.pay(1));}
    @Test void mealsTakeActualWorkAndKeepTheirRemainingWorkAcrossReload(){var s=new ColonySupply();assertTrue(s.offer(TissueDigestion.Kind.MEAT,12));s.digest(100);assertEquals(0,s.reserve());var saved=s.write();s=ColonySupply.read(saved);assertEquals(saved,s.write());s.digest(99);assertEquals(0,s.reserve());s.digest(1);assertEquals(12,s.reserve());assertEquals(0,s.queued());}
    @Test void constructionsAndRefundsConserveTheSameFiniteReserve(){var s=fed(1);assertTrue(s.pay(1));assertTrue(s.pay(2));assertEquals(61,s.reserve());s.refund(2);assertEquals(63,s.reserve());assertFalse(s.pay(64));assertEquals(63,s.reserve());assertFalse(s.pay(0));}
    @Test void largeBodiesPayMoreMaintenanceWithoutExpiringUnloadedTime(){var small=fed(1);var large=ColonySupply.read(small.write());for(int i=0;i<40;i++){assertTrue(small.maintain(5,0));assertTrue(large.maintain(5,3000));}assertEquals(63,small.reserve());assertEquals(59,large.reserve());assertEquals(large.write(),ColonySupply.read(large.write()).write());}
    @Test void hungryPaidWorkCanResumeWithoutDroppingItsPendingMaintenanceDebt(){var s=fed(1);assertTrue(s.pay(60));for(int i=0;i<39;i++)assertTrue(s.maintain(5,3000));assertFalse(s.maintain(5,3000));assertEquals(4,s.reserve());assertTrue(s.offer(TissueDigestion.Kind.MEAT,8));s.digest(200);assertTrue(s.maintain(5,3000));assertEquals(7,s.reserve());}
    @Test void broodIsASeparateRealCostAndEarlyStagesAreCheaper(){assertEquals(12,ColonySupply.broodCost(OutbreakRules.Tier.FRESH));assertTrue(ColonySupply.broodCost(OutbreakRules.Tier.HUNTER)>ColonySupply.broodCost(OutbreakRules.Tier.MATURE));var s=fed(1);assertTrue(s.pay(ColonySupply.broodCost(OutbreakRules.Tier.HUNTER)));assertEquals(0,s.reserve());assertFalse(s.pay(12));}
    @Test void pendingFoodAndEditedSavedValuesStayBounded(){var s=new ColonySupply();for(int i=0;i<12;i++)assertTrue(s.offer(TissueDigestion.Kind.MEAT,64));assertFalse(s.offer(TissueDigestion.Kind.MEAT,64));var t=s.write();t.putInt("Reserve",Integer.MAX_VALUE);t.putInt("Upkeep",Integer.MAX_VALUE);var copy=ColonySupply.read(t);assertEquals(TissueMetabolism.MAX_RESERVE,copy.reserve());assertEquals(12,copy.queued());assertEquals(200,copy.write().getInt("Upkeep"));copy.refund(Integer.MAX_VALUE);assertEquals(TissueMetabolism.MAX_RESERVE,copy.reserve());}
}
