package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueVascularTest {
    @Test void transferAmountKeepsTheDonorReserveAndRespectsRecipientCapacity(){assertEquals(0,TissueVascular.amount(1,96,100));assertEquals(0,TissueVascular.amount(1,Integer.MIN_VALUE,100));assertEquals(4,TissueVascular.amount(1,100,100));assertEquals(3,TissueVascular.amount(8,400,3));assertEquals(16,TissueVascular.amount(1,400,100));assertEquals(0,TissueVascular.amount(Double.NaN,400,100));assertTrue(TissueVascular.amount(Double.MAX_VALUE,400,100)<=72);}
    @Test void alreadyDigestedIncomeIsConservedWithoutCreatingANewPortion(){var c=new ColonySupply();assertEquals(32,c.receive(32));assertEquals(32,c.reserve());assertEquals(0,c.queued());assertEquals(0,c.receive(-1));assertEquals(TissueMetabolism.MAX_RESERVE-32,c.receive(Integer.MAX_VALUE));assertEquals(0,c.receive(10));assertEquals(TissueMetabolism.MAX_RESERVE,c.reserve());}
    @Test void connectionGrowsAndRetractsWithExactlyFixedEndpointsAndABoundedCurve(){var a=new Vec3(1,2,3);var b=new Vec3(5,1,3);assertEquals(0,TissueVascular.extension(-1));assertEquals(0,TissueVascular.extension(40));for(float t=0;t<40;t+=.2F){var path=TissueVascular.path(a,b,t);assertEquals(a,path.getFirst());assertTrue(path.getLast().distanceTo(a.lerp(b,TissueVascular.extension(t)))<1e-10);for(var p:path)assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&p.distanceTo(a)<5);}assertEquals(1,TissueVascular.extension(12));assertEquals(1,TissueVascular.extension(25));}
    @Test void savedCooldownDoesNotRestoreALiveConnectionOrRepeatItsMaterialCommit(){var t=new CompoundTag();t.putInt("Cooldown",120);t.putInt("Delivered",100);t.putLong("Target",35);var v=TissueVascular.read(t);assertFalse(v.active());assertEquals(0,v.delivered());assertEquals(120,v.cooldown());assertEquals(1,v.write().size());t.putInt("Cooldown",Integer.MAX_VALUE);assertEquals(160,TissueVascular.read(t).cooldown());}
    @Test void anInterruptedConnectionRetractsFromItsActualPartialReachWithoutReopening(){var t=new CompoundTag();t.putLong("Started",40_000_000);float reach=TissueVascular.visualExtension(t,40_000_006.5);t.putLong("CancelledAt",40_000_007);t.putFloat("CancelFrom",reach);assertEquals(reach,TissueVascular.visualExtension(t,40_000_007));float previous=reach;for(double age=.1;age<=8;age+=.1){float now=TissueVascular.visualExtension(t,40_000_007+age);assertTrue(now<=previous+.00001&&now>=0);previous=now;}assertEquals(0,TissueVascular.visualExtension(t,40_000_020));}
}
