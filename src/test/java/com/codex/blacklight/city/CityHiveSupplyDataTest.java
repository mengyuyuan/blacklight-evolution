package com.codex.blacklight.city;

import com.codex.blacklight.infection.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityHiveSupplyDataTest {
    @Test void oldCitySavesKeepDestroyedPortsButCannotCreateFreeHiveNutrition(){
        var old=new CompoundTag();old.putInt("destroyed",1);old.putIntArray("ports",new int[]{127,4});
        var d=CityData.load(old);assertTrue(d.destroyed(0));assertEquals(4,d.brokenPorts[1]);
        for(int i=0;i<2;i++){assertEquals(0,d.hiveSupply[i].reserve());assertEquals(0,d.hiveSupply[i].queued());assertEquals(0,d.hiveBroodTicks[i]);assertFalse(d.hiveSustained[i]);}
    }
    @Test void eachHiveStoresItsRealQueueAndBroodWorkOnceAndDoesNotReplayOfflineWork(){
        var d=new CityData();assertTrue(d.hiveSupply[0].offer(TissueDigestion.Kind.MEAT,20));d.hiveSupply[0].receive(100);d.hiveSupply[0].pay(12);d.hiveSupply[1].receive(7);
        d.hiveBroodTicks[0]=120;d.hiveBirths[0]=3;d.hiveFeedCursor[0]=4;d.hiveLoadedTicks[0]=2000;d.hiveSustained[0]=true;d.lastHiveSupplyTick[0]=100;
        var tag=d.save(new CompoundTag());assertEquals(2,tag.getList("hiveSupply",10).size());
        var r=CityData.load(tag);assertEquals(88,r.hiveSupply[0].reserve());assertEquals(1,r.hiveSupply[0].queued());assertEquals(7,r.hiveSupply[1].reserve());assertEquals(120,r.hiveBroodTicks[0]);assertEquals(3,r.hiveBirths[0]);assertEquals(4,r.hiveFeedCursor[0]);assertEquals(2000,r.hiveLoadedTicks[0]);assertFalse(r.hiveSustained[0]);assertEquals(Long.MIN_VALUE,r.lastHiveSupplyTick[0]);
    }
    @Test void malformedCitySupplyCannotMintBroodTimeOrUnboundedSavedCounters(){
        var d=new CityData();var tag=d.save(new CompoundTag());var n=tag.getList("hiveSupply",10).getCompound(0);n.putInt("ticks",-200);n.putInt("births",-5);n.putInt("cursor",Integer.MIN_VALUE);n.putInt("brood",Integer.MAX_VALUE);
        var r=CityData.load(tag);assertEquals(0,r.hiveLoadedTicks[0]);assertEquals(0,r.hiveBirths[0]);assertTrue(r.hiveFeedCursor[0]>=0&&r.hiveFeedCursor[0]<7);assertEquals(200,r.hiveBroodTicks[0]);assertFalse(CityHiveSupply.ready(r,0));
    }
}
