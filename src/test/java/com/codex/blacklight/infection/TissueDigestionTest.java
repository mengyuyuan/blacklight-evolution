package com.codex.blacklight.infection;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueDigestion.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueDigestionTest {
    private static final Profile BASE=new Profile(0,0,0,0,0,0);
    private static int process(TissueDigestion gut,Profile profile,int reserve,int ticks){for(int t=0;t<ticks;t+=20)reserve=gut.advance(Math.min(20,ticks-t),reserve,profile).reserve();return reserve;}
    @Test void timeWithoutAnActualPortionCannotProduceNutrition(){
        var gut=new TissueDigestion();var p=new Profile(8,8,8,8,8,8);
        assertEquals(10,process(gut,p,10,10000));assertEquals(0,gut.queued());assertEquals(0,gut.fat());assertEquals(0,gut.enzymes());
    }
    @Test void fibreAndFermentationHaveTheirOwnRealSubstratesAndProcessingTime(){
        var g=new TissueDigestion();assertFalse(g.offer(Kind.FIBRE,6,BASE));assertFalse(g.offer(Kind.FERMENT,8,BASE));
        var grinder=new Profile(4,0,0,0,0,0);assertTrue(g.offer(Kind.FIBRE,6,grinder));assertEquals(0,process(g,grinder,0,200));assertEquals(6,process(g,grinder,0,120));
        var ferment=new Profile(0,4,0,0,0,0);var f=new TissueDigestion();assertTrue(f.offer(Kind.FERMENT,8,ferment));
        assertEquals(0,process(f,ferment,0,1200));assertEquals(8,process(f,ferment,0,400));
    }
    @Test void absorptionUsesOnlyDigestedFoodAndSavesItsFractionInsteadOfMintingAReadReward(){
        var plain=new TissueDigestion();var enhanced=new TissueDigestion();var p=new Profile(0,0,4,0,0,0);
        assertTrue(plain.offer(Kind.SOFT,20,BASE));assertTrue(enhanced.offer(Kind.SOFT,20,p));
        assertEquals(20,process(plain,BASE,0,160));assertEquals(26,process(enhanced,p,0,160));
        assertEquals(26,process(TissueDigestion.read(enhanced.write()),p,26,1000));
        var empty=TissueDigestion.read(new CompoundTag());assertEquals(0,process(empty,p,0,1000));
    }
    @Test void pancreaticEnzymesCostRealStoredFoodAndCannotSpendTheLastUnitTwice(){
        var gut=new TissueDigestion();var p=new Profile(0,0,0,4,0,0);assertTrue(gut.offer(Kind.MEAT,12,p));
        int reserve=process(gut,p,1,120);assertEquals(12,reserve,"1 nutrient paid for an enzyme batch; 12 from the processed meal");assertEquals(0,gut.queued());assertEquals(4,gut.enzymes());
        var starved=new TissueDigestion();assertTrue(starved.offer(Kind.MEAT,12,p));assertEquals(0,process(starved,p,0,120));assertEquals(12,process(starved,p,0,100));
    }
    @Test void cropStoresExtraRealPortionsWithoutSkippingDigestionOrDeletingOlderQueues(){
        var gut=new TissueDigestion();var crop=new Profile(0,0,0,0,8,0);
        for(int i=0;i<MAX_PORTIONS;i++)assertTrue(gut.offer(Kind.SOFT,2,crop));assertFalse(gut.offer(Kind.SOFT,2,crop));
        var read=TissueDigestion.read(gut.write());assertEquals(MAX_PORTIONS,read.queued());assertEquals(24,process(read,BASE,0,MAX_PORTIONS*160));assertEquals(0,read.queued());
    }
    @Test void lipidStorageConservesOverflowAndOnlyReleasesWhileTheOrganCanFunction(){
        var gut=new TissueDigestion();var fat=new Profile(0,0,0,0,0,4);
        assertEquals(2400,gut.deposit(2390,40,fat));assertEquals(30,gut.fat());
        var restored=TissueDigestion.read(gut.write());assertEquals(0,process(restored,BASE,0,100));assertEquals(30,restored.fat());
        assertEquals(30,process(restored,fat,0,120));assertEquals(0,restored.fat());
    }
    @Test void aFullReserveLeavesThePortionPendingRatherThanDeletingItsNutrients(){
        var gut=new TissueDigestion();assertTrue(gut.offer(Kind.SOFT,12,BASE));
        assertEquals(2400,process(gut,BASE,2400,1000));assertEquals(1,gut.queued());assertEquals(160,gut.remainingWork());
        assertEquals(2400,process(gut,BASE,2388,160));assertEquals(0,gut.queued());
    }
    @Test void partialDigestionReloadAndFutureFoodDoNotReplayOrInventAReward(){
        var gut=new TissueDigestion();gut.offer(Kind.MEAT,10,BASE);process(gut,BASE,0,80);var restored=TissueDigestion.read(gut.write());
        assertEquals(120,restored.remainingWork());assertEquals(0,process(restored,BASE,0,100));assertEquals(10,process(restored,BASE,0,20));
        var tag=new CompoundTag();var list=new ListTag();var unknown=new CompoundTag();unknown.putString("Kind","future:algae");unknown.putInt("Value",64);unknown.putDouble("Work",500);list.add(unknown);tag.put("Portions",list);
        var future=TissueDigestion.read(tag);assertEquals(0,process(future,new Profile(8,8,8,8,8,8),0,10000));assertEquals(1,future.queued());assertEquals("future:algae",future.write().getList("Portions",Tag.TAG_COMPOUND).getCompound(0).getString("Kind"));
    }
    @Test void digestiveStructureRespondsToActualWorkAndStoredLoadWithoutMovingItsRoot(){
        for(var core:TissueCore.values())if(core.digestive()){
            int role=core==TissueCore.CROP||core==TissueCore.GILL_RAKER?1:0;var bone=new Bone("digestive_socket",role,new Box(new V(-4,-4,-3),new V(4,4,3)),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,0,0));
            var source=TissueMorphogenesis.functional(core,bone,0,42).getFirst().stages()[2];float[] empty=source.clone(),full=source.clone();
            TissueDigestiveMotion.apply(empty,bone,core,20,false,0);TissueDigestiveMotion.apply(full,bone,core,30,true,1);
            assertFalse(java.util.Arrays.equals(empty,full));assertArrayEquals(java.util.Arrays.copyOf(source,24),java.util.Arrays.copyOf(full,24),0);
            for(float value:full)assertTrue(Float.isFinite(value));
        }
    }
    @Test void realStorageDeformsTheSharedBellySurfaceWithoutMovingTheUpperSeamOrLegs(){
        var box=new Box(new V(-4,-4,-3),new V(4,4,3));var up=new V(0,-1,0);var front=new V(0,0,-1);var right=new V(1,0,0);
        var bone=new Bone("body",0,box,up,front,right,new V(0,0,0));float[] skin={-4,-4,0,4,-4,0,-4,4,0,4,4,0};
        var state=new CompoundTag();state.putInt("Fat",200);state.putInt("FatCapacity",200);state.putInt("Queued",2);state.putInt("Capacity",2);
        var full=skin.clone();var profile=new Profile(2,2,1,1,0,4);TissueDigestiveMotion.surface(full,bone,profile,state,0);
        assertArrayEquals(java.util.Arrays.copyOf(skin,6),java.util.Arrays.copyOf(full,6),0,"the upper body seam is stable");
        assertEquals(-full[6],full[9],.00001);assertTrue(full[9]>skin[9]&&full[10]>skin[10],"storage widens and lowers the real belly surface");
        var leg=new Bone("leg",2,box,up,front,right,new V(0,0,0));var unchanged=skin.clone();TissueDigestiveMotion.surface(unchanged,leg,profile,state,30);assertArrayEquals(skin,unchanged);
        var empty=skin.clone();TissueDigestiveMotion.surface(empty,bone,BASE,state,0);assertArrayEquals(skin,empty,"an ordinary host gets no fake organ animation");
    }
    @Test void filterPortionsNeedARealRakerAndRetainTheirDistinctSmallValueAndProgress(){
        var gut=new TissueDigestion();assertFalse(gut.offer(Kind.FILTER,2,BASE));
        var raker=new Profile(0,0,0,0,0,0,2);assertTrue(gut.offer(Kind.FILTER,2,raker));
        assertEquals(0,process(gut,raker,0,200));var restored=TissueDigestion.read(gut.write());
        assertEquals(2,process(restored,raker,0,100));assertEquals(0,restored.queued());assertEquals(2,process(restored,raker,2,10000));
    }
    @Test void extraoralContactUsesItsOwnContinuousOpeningAndReturnClock(){
        var c=TissueOrganCycle.cycle(TissueCore.EXTRAORAL);
        assertEquals(0,TissueTongueShape.extension(TissueCore.EXTRAORAL,-1));assertEquals(0,TissueTongueShape.extension(TissueCore.EXTRAORAL,c.windup()));
        assertEquals(1,TissueTongueShape.extension(TissueCore.EXTRAORAL,c.contact()));assertEquals(0,TissueTongueShape.extension(TissueCore.EXTRAORAL,c.end()));
        float previous=0;for(float t=c.windup();t<c.end();t+=.1F){float v=TissueTongueShape.extension(TissueCore.EXTRAORAL,t);assertTrue(Math.abs(v-previous)<.04);previous=v;}
    }
}
