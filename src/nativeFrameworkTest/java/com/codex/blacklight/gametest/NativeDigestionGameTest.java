package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeDigestionGameTest {
    private static final class Fixture {
        final GameTestHelper h;final Mob actor;final List<? extends String> old;final NativeTestArena arena;boolean closed;
        Fixture(GameTestHelper h,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,15);old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            actor=h.spawn(EntityType.COW,4,2,4);actor.setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"real native animal is infected without replacement");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",0);
            actor.getPersistentData().put("BlacklightNativeInfection",data);NativeInfection.tick(actor,h.getLevel());
            h.runAfterDelay(290,this::close);
        }
        void close(){if(closed)return;closed=true;actor.discard();arena.close();InfectionEcologyConfig.PRESERVE.set(old);}
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="native_digest_real_death")
    public static void acceptedMealQueuesThenDigestsThroughLoadedTicksAndSurvivesInPlaceReload(GameTestHelper h){
        var f=new Fixture(h,TissueCore.GIZZARD);var victim=h.spawn(EntityType.PIG,7,2,4);victim.setNoAi(true);
        try{
            victim.hurt(TissueAttackSource.mobAttack(f.actor),10000);
            h.assertTrue(!victim.isAlive()&&NativeTissue.nutrition(f.actor)==0,"accepted death is not an instant reserve grant");
        }catch(Throwable failure){victim.discard();f.close();throw failure;}
        h.runAfterDelay(3,()->{try{
            h.assertTrue(NativeTissueDigestion.saved(f.actor).getList("Portions",10).size()==1&&NativeTissue.nutrition(f.actor)==0,"accepted corpse becomes one real timed food portion");
            h.assertTrue(NativeTissue.snapshot(f.actor).getCompound("Digestion").getInt("Queued")==1,"the native visual projection carries the actual queued organ work");
        }catch(Throwable failure){victim.discard();f.close();throw failure;}});
        h.runAfterDelay(60,()->{try{
            var before=NativeTissueDigestion.saved(f.actor);var full=new CompoundTag();f.actor.saveWithoutId(full);f.actor.load(full);
            h.assertTrue(NativeTissueDigestion.saved(f.actor).equals(before)&&NativeTissue.nutrition(f.actor)==0,"loading the actual original animal preserves processing progress without paying offline time");
        }catch(Throwable failure){victim.discard();f.close();throw failure;}});
        h.runAfterDelay(260,()->{try{
            h.assertTrue(NativeTissueDigestion.saved(f.actor).getList("Portions",10).isEmpty()&&NativeTissue.nutrition(f.actor)>0,"loaded metabolism completes the genuine food and deposits nutrition");
            h.assertTrue(f.actor.getType()==EntityType.COW&&f.actor.isNoAi(),"digestion neither replaces the real type nor changes its existing NoAI flag");
            f.actor.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION);NativeInfection.clear(f.actor);h.assertTrue(!f.actor.getPersistentData().contains("BlacklightNativeInfection")&&NativeTissueDigestion.saved(f.actor).isEmpty(),"cure removes only owned digestive state");h.succeed();
        }finally{victim.discard();f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="native_digest_fat")
    public static void realOverflowStoresFatThenLoadedMetabolismReleasesItOnlyWhenReserveIsLow(GameTestHelper h){
        var f=new Fixture(h,TissueCore.FAT);int maximum=TissueMetabolism.MAX_RESERVE;
        try{
            NativeTissue.feed(f.actor,maximum-1);NativeTissue.feed(f.actor,64);
            h.assertTrue(NativeTissue.nutrition(f.actor)==maximum&&NativeTissueDigestion.saved(f.actor).getInt("Fat")==63,"overflow becomes bounded real lipid rather than disappearing or exceeding reserve");
            h.assertTrue(NativeTissue.spend(f.actor,maximum-100),"real reserve can be spent by development/actions");
        }catch(Throwable failure){f.close();throw failure;}
        h.runAfterDelay(45,()->{try{
            h.assertTrue(NativeTissueDigestion.saved(f.actor).getInt("Fat")<63&&NativeTissue.nutrition(f.actor)>0,"loaded low-reserve work spends stored fat instead of inventing food");h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_digest_capacity")
    public static void nativeFoodAcceptanceUsesTheSameOrganGateAndPhysicalCropCapacity(GameTestHelper h){
        var f=new Fixture(h,TissueCore.MUSCLE);
        try{
            h.assertTrue(!NativeTissueDigestion.offer(f.actor,TissueDigestion.Kind.FIBRE,6)&&NativeTissue.nutrition(f.actor)==0,"missing grinding organ cannot convert fibre into generic free nutrition");
            h.assertTrue(NativeTissueDigestion.offer(f.actor,TissueDigestion.Kind.MEAT,12)&&NativeTissueDigestion.offer(f.actor,TissueDigestion.Kind.MEAT,12)
                    &&!NativeTissueDigestion.offer(f.actor,TissueDigestion.Kind.MEAT,12),"unexpanded crop accepts two actual portions and rejects overflow");h.succeed();
        }finally{f.close();}
    }
}
