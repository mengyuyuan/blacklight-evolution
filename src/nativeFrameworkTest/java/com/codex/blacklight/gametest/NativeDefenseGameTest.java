package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeDefenseGameTest {
    private static final class Fixture {
        final GameTestHelper h;final Mob actor,attacker;final List<? extends String> old;final NativeTestArena arena;boolean closed;
        Fixture(GameTestHelper h,TissueCore core){
            this.h=h;arena=NativeTestArena.open(h,0,15);old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
            for(int x=2;x<=10;x++)for(int y=2;y<=6;y++)for(int z=2;z<=10;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            actor=h.spawn(EntityType.COW,5,2,5);actor.setNoAi(true);attacker=h.spawn(EntityType.PIG,5,2,8);attacker.setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"real native armour actor infected");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",core.key());data.putInt("Nutrition",120);
            actor.getPersistentData().put("BlacklightNativeInfection",data);NativeInfection.tick(actor,h.getLevel());actor.setHealth(actor.getMaxHealth());h.runAfterDelay(285,this::close);
        }
        float hit(boolean front){
            actor.yBodyRot=0;actor.setYRot(0);attacker.setPos(actor.position().add(0,0,front?3:-3));actor.invulnerableTime=0;actor.setHealth(actor.getMaxHealth());
            float before=actor.getHealth();actor.hurt(actor.damageSources().mobAttack(attacker),12);return before-actor.getHealth();
        }
        void close(){if(closed)return;closed=true;actor.discard();attacker.discard();arena.close();InfectionEcologyConfig.PRESERVE.set(old);}
    }
    private static void directional(GameTestHelper h,TissueCore core){
        var f=new Fixture(h,core);
        try{
            boolean protectedFront=core==TissueCore.NACRE;
            float plain=f.hit(!protectedFront),protectedLoss=f.hit(protectedFront);
            String key=core==TissueCore.NACRE?"Shell":"Bone";
            h.assertTrue(plain>0&&protectedLoss>0&&protectedLoss<plain&&NativeTissueDefense.saved(f.actor).getFloat(key)<1,
                    "real directional physical damage is reduced but not erased, with real integrity wear; core="+core+" plain="+plain+" protected="+protectedLoss+" state="+NativeTissueDefense.saved(f.actor));h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_defense_bone") public static void nativeOsteodermReallyProtectsItsBackAndWears(GameTestHelper h){directional(h,TissueCore.OSTEODERM);}
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_defense_shell") public static void nativeNacreReallyProtectsItsFrontAndWears(GameTestHelper h){directional(h,TissueCore.NACRE);}
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_defense_veto")
    public static void damageEliminatedByTheOriginalEffectChainDoesNotConsumeNativeArmourIntegrity(GameTestHelper h){
        var f=new Fixture(h,TissueCore.OSTEODERM);
        try{
            f.actor.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,100,4));
            float loss=f.hit(false);
            h.assertTrue(loss==0&&NativeTissueDefense.saved(f.actor).getFloat("Bone")==1,"source/vanilla effects still run; provisional armour reduction is not charged without real HP or absorption loss");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_defense_bypass")
    public static void nativeArmourCannotConvertAnArmourBypassIntoAnInvulnerabilityMechanism(GameTestHelper h){
        var f=new Fixture(h,TissueCore.NACRE);
        try{
            f.actor.yBodyRot=0;f.attacker.setPos(f.actor.position().add(0,0,3));var source=f.actor.damageSources().sonicBoom(f.attacker);
            h.assertTrue(source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),"real source carries its original armour-bypass tag");
            float before=f.actor.getHealth();f.actor.hurt(source,5);
            h.assertTrue(f.actor.getHealth()<before&&NativeTissueDefense.saved(f.actor).getFloat("Shell")==1,"bypass is retained and does not fake a shell-blocked hit");h.succeed();
        }finally{f.close();}
    }
    @GameTest(template="wall_test",timeoutTicks=300,batch="native_defense_paid_repair")
    public static void nativeArmourReloadsItsWearAndRepairsOnlyAfterQuietLoadedTimeAndActualFood(GameTestHelper h){
        var f=new Fixture(h,TissueCore.OSTEODERM);float wear;
        try{
            f.hit(false);wear=NativeTissueDefense.saved(f.actor).getFloat("Bone");h.assertTrue(wear<1,"fixture has a real worn plate");
            NativeTissue.spend(f.actor,NativeTissue.nutrition(f.actor));var full=new CompoundTag();f.actor.saveWithoutId(full);f.actor.load(full);
            h.assertTrue(NativeTissueDefense.saved(f.actor).getFloat("Bone")==wear,"in-place reload keeps exact integrity and does not repair offline");
        }catch(Throwable failure){f.close();throw failure;}
        h.runAfterDelay(210,()->{try{
            h.assertTrue(NativeTissue.nutrition(f.actor)==0&&NativeTissueDefense.saved(f.actor).getFloat("Bone")==wear,"quiet time without food does not repair real armour");NativeTissue.feed(f.actor,20);
        }catch(Throwable failure){f.close();throw failure;}});
        h.runAfterDelay(260,()->{try{
            h.assertTrue(NativeTissueDefense.saved(f.actor).getFloat("Bone")>wear&&NativeTissue.nutrition(f.actor)<20,"quiet loaded repair consumes real food and restores actual plate integrity");h.succeed();
        }finally{f.close();}});
    }
}
