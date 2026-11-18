package com.codex.blacklight.military.aircraft;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterRapidBlastTest {
    @Test void delayedRapidWindowsNeverUseHellfireOrImmediateGenericBlast(){
        var wave=new HelicopterBlastWave(true);assertEquals("helicopter_70mm",wave.weaponId());
        for(int tick=0;tick<9;tick++){assertNull(wave.stage());assertFalse(wave.contact(UUID.randomUUID()));wave.advance(1);}
        assertEquals(2,wave.stage().radius());assertEquals(4,wave.stage().damage(2.4F),1e-6);
        wave.advance(1);assertEquals(6,wave.stage().radius());assertEquals(2,wave.stage().damage(2.4F),1e-6);
        wave.advance(1);assertEquals(6,wave.stage().radius());wave.advance(1);assertNull(wave.stage());
        wave.advance(7);assertFalse(wave.expired());wave.advance(1);assertTrue(wave.expired());assertEquals(20,wave.age());
    }
    @Test void reloadBeforeAndAfterTheInnerRingPreservesProfileAndContactHistory(){
        var wave=new HelicopterBlastWave(true);wave.advance(8);var restored=HelicopterBlastWave.load(wave.save());assertNotNull(restored);assertTrue(restored.rapid());assertNull(restored.stage());
        restored.advance(1);UUID first=UUID.randomUUID();assertTrue(restored.contact(first));restored=HelicopterBlastWave.load(restored.save());assertNotNull(restored);restored.advance(1);
        assertEquals(6,restored.stage().radius());assertFalse(restored.contact(first));assertTrue(restored.contact(UUID.randomUUID()));
        restored.advance(100);assertTrue(restored.expired());assertFalse(restored.contact(UUID.randomUUID()));
    }
    @Test void oldHellfireSavesRemainCompatibleAndAmbiguousRapidSavesFailClosed(){
        var old=new HelicopterBlastWave().save();old.putInt("Version",1);old.remove("Weapon");var legacy=HelicopterBlastWave.load(old);assertNotNull(legacy);assertFalse(legacy.rapid());assertEquals(60,legacy.duration());
        var rapid=new HelicopterBlastWave(true).save();rapid.remove("Weapon");assertNull(HelicopterBlastWave.load(rapid));
        rapid=new HelicopterBlastWave(true).save();rapid.putString("Weapon","vehicle_rocket");assertNull(HelicopterBlastWave.load(rapid));
        rapid=new HelicopterBlastWave(true).save();rapid.putInt("Age",21);assertNull(HelicopterBlastWave.load(rapid));
        rapid=new HelicopterBlastWave(true).save();rapid.putInt("Version",1);assertNull(HelicopterBlastWave.load(rapid));
    }
}
