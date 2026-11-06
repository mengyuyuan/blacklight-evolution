package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueSubjectGameTest {
    @GameTest(template="wall_test",batch="native_shared_subject")
    public static void bothRepresentationsShareDonationValuesAndNativeScenesNeverPersist(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        var nativeMob=h.spawn(EntityType.COW,4,2,4);nativeMob.setNoAi(true);
        var converted=ModEntities.INFECTED_HOST.get().create(h.getLevel());
        try{
            BlacklightInfectionSystem.infect(nativeMob,null);
            var state=new CompoundTag();state.putLong("Age",48000);state.putString("Core",TissueCore.JAW.key());
            nativeMob.getPersistentData().put("BlacklightNativeInfection",state);NativeInfection.tick(nativeMob,h.getLevel());
            converted.inherit(EntityType.COW.create(h.getLevel()));converted.setNoAi(true);converted.advanceTissue(48000);
            converted.setPos(h.absolutePos(new BlockPos(6,2,4)).getCenter());h.getLevel().addFreshEntity(converted);
            var nativeSubject=TissueSubject.of(nativeMob);var hostSubject=TissueSubject.of(converted);
            h.assertTrue(nativeSubject!=null&&hostSubject!=null&&nativeSubject.mob()==nativeMob&&hostSubject.mob()==converted,"handles retain the actual entity rather than creating an AI proxy");
            var nativeDonor=nativeSubject.donor();
            h.assertTrue(hostSubject.inherit(UUID.randomUUID(),nativeDonor)&&hostSubject.inheritance().meals()==1,"converted host accepts the native component donor through the shared rule");
            h.assertTrue(nativeSubject.inherit(UUID.randomUUID(),hostSubject.donor())&&nativeSubject.inheritance().meals()==1,"native mob accepts converted-host genetics through the same rule");
            nativeSubject.scene(converted.getId(),h.getLevel().getGameTime(),false,100);
            h.assertTrue(NativeTissue.snapshot(nativeMob).getInt("CannibalTarget")==converted.getId(),"transient native scene can be synchronized");
            var saved=new CompoundTag();nativeMob.saveWithoutId(saved);
            h.assertTrue(!saved.toString().contains("CannibalTarget"),"saving during a scene does not persist target IDs or replay a claim");
            nativeMob.discard();
            h.assertTrue(!nativeSubject.valid()&&nativeSubject.donor()==null&&nativeSubject.inheritance()==TissueInheritance.EMPTY,
                    "a stale handle becomes inactive without reading invalid entity storage");
            h.assertTrue(nativeDonor.age()==48000&&nativeDonor.core()==TissueCore.JAW&&nativeDonor.species().equals("minecraft:cow"),"captured donor values remain usable after the source is gone");
            h.assertTrue(TissueSubject.of(null)==null,"missing entity lookup is not a subject");h.succeed();
        }finally{nativeMob.discard();converted.discard();InfectionEcologyConfig.PRESERVE.set(old);}
    }
}
