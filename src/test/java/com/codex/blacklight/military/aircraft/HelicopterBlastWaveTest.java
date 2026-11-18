package com.codex.blacklight.military.aircraft;

import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterBlastWaveTest {
    @Test void sourceWindowsSampleAtTwentyHzAndRetainRelativeDamage(){
        double[] radii={1,4,7,7,9,9};float[] damage={20,4,1.4F,1.4F,.6F,.6F};
        for(int t=0;t<6;t++){var s=HelicopterBlastWave.stage(t);assertEquals(radii[t],s.radius());assertEquals(damage[t],s.damage(20),1e-6);}
        assertNull(HelicopterBlastWave.stage(-1));assertNull(HelicopterBlastWave.stage(6));assertNull(HelicopterBlastWave.stage(59));
    }
    @Test void theSameTargetCannotTakeAnotherRingAfterReloadButNewContactsCan(){
        UUID first=UUID.randomUUID(),later=UUID.randomUUID();var wave=new HelicopterBlastWave();assertTrue(wave.contact(first));wave.advance(1);
        var restored=HelicopterBlastWave.load(wave.save());assertNotNull(restored);assertEquals(1,restored.age());assertFalse(restored.contact(first));assertTrue(restored.contact(later));
        restored.advance(3);assertFalse(restored.contact(first));assertFalse(restored.contact(later));assertTrue(restored.contact(UUID.randomUUID()));
    }
    @Test void missedWorldTimeSkipsOldRingsAndExpiresWithoutReplay(){
        var wave=new HelicopterBlastWave();wave.advance(4);assertEquals(9,wave.stage().radius());wave.advance(2);assertNull(wave.stage());assertFalse(wave.contact(UUID.randomUUID()));
        wave.advance(Integer.MAX_VALUE);assertTrue(wave.expired());assertEquals(60,wave.age());assertThrows(IllegalArgumentException.class,()->wave.advance(-1));
    }
    @Test void invalidOrDuplicateSavedContactListsFailClosed(){
        var wave=new HelicopterBlastWave();wave.contact(UUID.randomUUID());var tag=wave.save();var list=tag.getList("Contacts",Tag.TAG_COMPOUND);list.add(list.getCompound(0).copy());assertNull(HelicopterBlastWave.load(tag));
        tag=wave.save();tag.putInt("Age",-1);assertNull(HelicopterBlastWave.load(tag));tag=wave.save();tag.putInt("Age",61);assertNull(HelicopterBlastWave.load(tag));
        tag=wave.save();tag.putString("Contacts","invalid");assertNull(HelicopterBlastWave.load(tag));tag=wave.save();tag.getList("Contacts",Tag.TAG_COMPOUND).getCompound(0).remove("Entity");assertNull(HelicopterBlastWave.load(tag));
    }
}
