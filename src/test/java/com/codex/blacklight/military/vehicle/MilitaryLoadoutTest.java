package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryLoadoutTest {
    @Test void defaultEightAndTwelvePersonPatrolsHaveFiniteOrdinarySupportComposition() {
        var eight=MilitaryLoadoutProfile.patrol(8,false);
        assertEquals(8,eight.size());assertEquals(5,eight.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.RIFLE).count());
        assertEquals(1,eight.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.CARBINE).count());
        assertEquals(1,eight.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.MACHINE_GUN).count());
        assertEquals(1,eight.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.GRENADIER).count());
        var twelve=MilitaryLoadoutProfile.patrol(12,false);
        assertEquals(12,twelve.size());assertEquals(8,twelve.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.RIFLE).count());
        assertEquals(1,twelve.stream().filter(p->p.duty()==MilitaryLoadoutProfile.Duty.ANTI_ARMOR).count());
        assertEquals(MilitaryLoadoutProfile.Duty.ANTI_ARMOR,MilitaryLoadoutProfile.patrol(8,true).get(7).duty());
    }
    @Test void ordinaryWeightsRejectNegativeAndZeroAndSelectStablyForTheSameRealIdentity() {
        UUID owner=UUID.fromString("12345678-1234-1234-1234-123456789abc");
        assertThrows(IllegalArgumentException.class,()->MilitaryLoadoutProfile.weighted(owner,"patrol",Map.of(MilitaryLoadoutProfile.Duty.RIFLE,-1)));
        assertThrows(IllegalArgumentException.class,()->MilitaryLoadoutProfile.weighted(owner,"patrol",Map.of(MilitaryLoadoutProfile.Duty.RIFLE,0)));
        var weights=Map.of(MilitaryLoadoutProfile.Duty.ANTI_ARMOR,1);
        assertEquals(MilitaryLoadoutProfile.Duty.ANTI_ARMOR,MilitaryLoadoutProfile.weighted(owner,"patrol",weights).duty());
        assertEquals(MilitaryLoadoutProfile.weighted(owner,"patrol",weights),MilitaryLoadoutProfile.weighted(owner,"patrol",weights));
    }
    @Test void aRocketCarrierOwnsSeparateFiniteCarbineAndRocketResources() {
        var profile=MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR);
        assertEquals("military_carbine",profile.primary().weaponId());assertEquals(24,profile.primary().magazine());
        assertNotNull(profile.secondary());assertEquals("rocket_launcher",profile.secondary().weaponId());assertEquals(1,profile.secondary().magazine());
        var inventory=new MilitaryLoadoutInventory();assertTrue(inventory.claimNative(UUID.randomUUID(),"native",profile));
        assertEquals(48,inventory.reserve("rifle_round"));assertEquals(2,inventory.reserve("rocket_round"));
        assertTrue(inventory.consume("rocket_round",2));assertEquals(0,inventory.receive("rifle_round",90));assertEquals(0,inventory.reserve("rocket_round"));
        assertEquals(2,inventory.receive("rocket_round",90));assertFalse(inventory.consume("rocket_round",3));assertEquals(2,inventory.reserve("rocket_round"));
    }
    @Test void oldNinetyRifleRoundsNeverBecomeRocketsAndMigrationCannotClaimASecondIssue() {
        var old=MilitaryLoadoutInventory.legacy(WeaponCatalog.RIFLE,90);
        assertEquals(90,old.reserve("rifle_round"));assertEquals(0,old.reserve("rocket_round"));
        assertFalse(old.claimNative(UUID.randomUUID(),"new-task",MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR)));
        var copy=MilitaryLoadoutInventory.restore(old.snapshot());assertEquals(90,copy.reserve("rifle_round"));assertEquals(0,copy.reserve("rocket_round"));
        assertEquals(0,MilitaryLoadoutInventory.legacy(null,90).reserve("rifle_round"));
    }
    @Test void spendingOrLosingGunsAndChangingTaskCannotReissueTheNativeStock() {
        var inventory=new MilitaryLoadoutInventory();var id=UUID.randomUUID();var p=MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR);
        assertTrue(inventory.claimNative(id,"base",p));assertTrue(inventory.consume("rocket_round",2));
        var restored=MilitaryLoadoutInventory.restore(inventory.snapshot());
        assertFalse(restored.claimNative(id,"different-task",p));assertEquals(0,restored.reserve("rocket_round"));assertEquals(48,restored.reserve("rifle_round"));
    }
    @Test void heavyAndCommandDutiesNeverEnterTheOrdinaryRandomPool() {
        UUID id=UUID.randomUUID();
        assertEquals(MilitaryLoadoutProfile.Duty.MACHINE_GUN,MilitaryLoadoutProfile.forNative(id,"base",MilitaryFaction.MILITARY,true,MilitaryRole.TROOPER).duty());
        assertEquals(MilitaryLoadoutProfile.Duty.CARBINE,MilitaryLoadoutProfile.forNative(id,"base",MilitaryFaction.BLACKWATCH,false,MilitaryRole.COMMANDER).duty());
        for(var faction:MilitaryFaction.values())for(int i=0;i<128;i++) {
            var p=MilitaryLoadoutProfile.forNative(new UUID(0,i),"base",faction,false,MilitaryRole.TROOPER);
            assertFalse(Set.of("missile_launcher","thermobaric_launcher","infection_launcher").contains(p.primary().weaponId()));
            if(p.secondary()!=null)assertFalse(Set.of("missile_launcher","thermobaric_launcher","infection_launcher").contains(p.secondary().weaponId()));
        }
    }
    @Test void aNewlyCarriedGunCanReceiveOnlyItsEmptyBoundedTypedReserveWithoutFreeIssue() {
        var old=MilitaryLoadoutInventory.legacy(WeaponCatalog.RIFLE,90);old.allow(WeaponCatalog.ROCKET);
        assertEquals(90,old.reserve("rifle_round"));assertEquals(0,old.reserve("rocket_round"));
        assertEquals(2,old.receive("rocket_round",90));old.allow(WeaponCatalog.ROCKET);assertEquals(0,old.receive("rocket_round",90));
        var nativeStock=new MilitaryLoadoutInventory();nativeStock.claimNative(UUID.randomUUID(),"base",MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR));
        nativeStock.allow(WeaponCatalog.CARBINE);nativeStock.allow(WeaponCatalog.RIFLE);assertEquals(0,nativeStock.deficit("rifle_round"));
    }
    @Test void foundGunPocketIsAValidInitializedSnapshotAndOversizedIssueIsAtomic() {
        var found=new MilitaryLoadoutInventory();found.allow(WeaponCatalog.ROCKET);
        assertDoesNotThrow(()->MilitaryLoadoutInventory.restore(found.snapshot()));
        assertFalse(found.claimNative(UUID.randomUUID(),"later",MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.RIFLE)));
        var fresh=new MilitaryLoadoutInventory();var oversized=new MilitaryLoadoutProfile(MilitaryLoadoutProfile.Duty.RIFLE,
                new MilitaryLoadoutProfile.Gun("military_rifle",30,4096),new MilitaryLoadoutProfile.Gun("military_carbine",24,4096));
        assertThrows(IllegalArgumentException.class,()->fresh.claimNative(UUID.randomUUID(),"invalid",oversized));
        assertFalse(fresh.snapshot().initialized());assertTrue(fresh.snapshot().reserves().isEmpty());
    }
    @Test void invalidTypedSnapshotsCannotClobberAmmoKindsOrInventStock() {
        assertThrows(IllegalArgumentException.class,()->MilitaryLoadoutInventory.restore(new MilitaryLoadoutInventory.Snapshot(99,true,false,null,"",Map.of(),Map.of())));
        assertThrows(IllegalArgumentException.class,()->MilitaryLoadoutInventory.restore(new MilitaryLoadoutInventory.Snapshot(1,true,false,null,"",Map.of("rocket_round",90),Map.of("rocket_round",2))));
        assertThrows(IllegalArgumentException.class,()->MilitaryLoadoutInventory.restore(new MilitaryLoadoutInventory.Snapshot(1,false,false,null,"",Map.of("rifle_round",90),Map.of("rifle_round",90))));
    }
}
