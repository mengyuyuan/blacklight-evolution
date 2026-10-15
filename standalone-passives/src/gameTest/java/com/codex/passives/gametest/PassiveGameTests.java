package com.codex.passives.gametest;
import com.codex.passives.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.player.PlayerEvent;

@GameTestHolder("adaptive_passives") @PrefixGameTestTemplate(false)
public final class PassiveGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"passive-qa"));
        p.connection=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"connection-qa")).connection;
        p.setGameMode(GameType.SURVIVAL); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000);
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2,2,2)));
        for(int i=0;i<65;i++) p.tick();
        p.invulnerableTime=0; p.setHealth(1000); return p;
    }
    private static void near(GameTestHelper h,double actual,double expected,String name) { h.assertTrue(Math.abs(actual-expected)<.002,name+" actual="+actual+" expected="+expected); }
    private static void hit(ServerPlayer p,float damage) { p.invulnerableTime=0; p.hurt(p.damageSources().generic(),damage); }
    @GameTest(template="empty",timeoutTicks=60)
    public static void potionsAndThreeRealHits(GameTestHelper h) {
        var p=player(h);
        var bottle=PassivesMod.ADAPTATION_SERUM.get().finishUsingItem(new ItemStack(PassivesMod.ADAPTATION_SERUM.get()),h.getLevel(),p);
        h.assertTrue(bottle.is(Items.GLASS_BOTTLE),"drink returns bottle");
        var duplicate=new ItemStack(PassivesMod.ADAPTATION_SERUM.get());
        PassivesMod.ADAPTATION_SERUM.get().finishUsingItem(duplicate,h.getLevel(),p); h.assertTrue(duplicate.getCount()==1,"duplicate not consumed");
        var d=PassiveData.read(p); d.adaptation=100; d.save(p);
        hit(p,300); near(h,p.getHealth(),730,"first hit"); near(h,PassiveData.read(p).adaptation,142,"first stacks");
        hit(p,300); near(h,p.getHealth(),481,"second hit"); near(h,PassiveData.read(p).adaptation,178,"second stacks");
        hit(p,300); near(h,p.getHealth(),265,"third hit"); near(h,PassiveData.read(p).adaptation,205,"third stacks"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void upgradedAdaptationAndActualRegenerationStacks(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,1,2); PassiveApi.grant(p,2,1);
        var d=PassiveData.read(p); d.adaptation=100; d.save(p);
        hit(p,300); near(h,p.getHealth(),850,"tier II first"); near(h,PassiveData.read(p).adaptation,110,"II layers"); near(h,PassiveData.read(p).regeneration,25,"actual damage layers");
        hit(p,300); near(h,p.getHealth(),721,"tier II second"); hit(p,300); near(h,p.getHealth(),604,"corrected third row");
        near(h,PassiveData.read(p).adaptation,115,"third stacks"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void firstTierHealingClock(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,2,1); p.setHealth(500);
        var d=PassiveData.read(p); d.regeneration=150; d.nextHeal=p.level().getGameTime()+20; d.save(p);
        h.runAfterDelay(19,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),500,"not early"); });
        h.runAfterDelay(20,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),650,"first pulse"); near(h,PassiveData.read(p).regeneration,120,"first decay"); });
        h.runAfterDelay(40,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),770,"second pulse"); near(h,PassiveData.read(p).regeneration,96,"second decay"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void secondTierHealingClock(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,2,2); p.setHealth(600);
        var d=PassiveData.read(p); d.regeneration=75; d.nextHeal=p.level().getGameTime()+20; d.save(p);
        h.runAfterDelay(20,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),900,"II first pulse"); near(h,PassiveData.read(p).regeneration,15,"II first decay"); });
        h.runAfterDelay(40,()->{ PassiveSystem.tick(p); near(h,p.getHealth(),915,"II second pulse"); near(h,PassiveData.read(p).regeneration,12,"II second decay"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void absorptionAndBypassDoNotFarmStacks(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,1,1); PassiveApi.grant(p,2,1);
        
        p.setAbsorptionAmount(10); hit(p,6); near(h,p.getHealth(),1000,"absorbed HP"); near(h,PassiveData.read(p).regeneration,0,"no absorbed stacks");
        p.setAbsorptionAmount(5); hit(p,12); near(h,p.getHealth(),993,"partial absorption"); near(h,PassiveData.read(p).regeneration,2,"ceil seven over six");
        int previous=PassiveData.read(p).regeneration; p.setAbsorptionAmount(0); p.invulnerableTime=0;
        p.hurt(p.damageSources().fellOutOfWorld(),10); near(h,PassiveData.read(p).regeneration,previous,"bypass excluded"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void saveReloadDeathCopyAndMilk(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,1,2); PassiveApi.grant(p,2,2); PassiveApi.grant(p,4,1);
        var d=PassiveData.read(p); d.adaptation=99; d.regeneration=17; d.bonusHealth=60; d.save(p);
        var n=new CompoundTag(); p.saveWithoutId(n); var loaded=player(h); loaded.load(n);
        h.assertTrue(PassiveData.read(loaded).encode().equals(PassiveData.read(p).encode()),"full entity save/load");
        var clone=player(h); PassiveEvents.clone(new PlayerEvent.Clone(clone,p,true));
        h.assertTrue(PassiveData.read(clone).encode().equals(PassiveData.read(p).encode()),"death clone preserves entitlement and progress");
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET),h.getLevel(),clone); PassiveSystem.tick(clone);
        h.assertTrue(clone.hasEffect(PassivesMod.ADAPTATION.get()),"milk restores marker"); near(h,PassiveData.read(clone).adaptation,99,"milk keeps layers"); h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void creditedKillCapsAndDoesNotRepeat(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,4,1);
        var cow=h.spawn(EntityType.COW,2,2,3); cow.setNoAi(true); cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); cow.setHealth(1000);
        cow.hurt(p.damageSources().playerAttack(p),2000);
        h.runAfterDelay(3,()->{ PassiveSystem.finishDeaths(); near(h,PassiveData.read(p).bonusHealth,30,"kill cap"); near(h,p.getMaxHealth(),1030,"health attribute");
            PassiveSystem.queueDeath(cow,p.damageSources().playerAttack(p)); PassiveSystem.finishDeaths(); near(h,PassiveData.read(p).bonusHealth,30,"no duplicate"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void indirectKillsAndUnownedKills(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,4,1);
        var cow=h.spawn(EntityType.COW,2,2,3); cow.setNoAi(true);
        cow.hurt(p.damageSources().indirectMagic(null,p),100);
        var other=h.spawn(EntityType.COW,4,2,3); other.setNoAi(true); other.hurt(p.damageSources().generic(),100);
        h.runAfterDelay(3,()->{ PassiveSystem.finishDeaths(); near(h,PassiveData.read(p).bonusHealth,1,"10 HP potion kill pays one; unowned pays zero"); h.succeed(); });
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void actualTntExplosionCreditsOwner(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,4,1);
        var cow=h.spawn(EntityType.COW,3,2,3); cow.setNoAi(true); cow.setNoGravity(true);
        var tnt=new net.minecraft.world.entity.item.PrimedTnt(h.getLevel(),cow.getX(),cow.getY(),cow.getZ(),p);
        tnt.setFuse(1); h.getLevel().addFreshEntity(tnt);
        h.runAfterDelay(8,()->{ PassiveSystem.finishDeaths(); near(h,PassiveData.read(p).bonusHealth,1,"actual owned TNT explosion"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void cancelledDamageAndDeathsDoNotPay(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,1,1); PassiveApi.grant(p,2,1); PassiveApi.grant(p,4,1);
        p.getPersistentData().putBoolean("passive_test_cancel",true); hit(p,300);
        near(h,p.getHealth(),1000,"canceled hit"); near(h,PassiveData.read(p).regeneration,0,"canceled layers");
        var cow=h.spawn(EntityType.COW,2,2,3); cow.setNoAi(true); cow.getPersistentData().putBoolean("passive_test_cancel_death",true);
        cow.hurt(p.damageSources().playerAttack(p),100);
        var direct=h.spawn(EntityType.COW,4,2,3); direct.setNoAi(true); direct.setHealth(0);
        h.runAfterDelay(3,()->{ PassiveSystem.finishDeaths(); near(h,PassiveData.read(p).bonusHealth,0,"canceled death and direct setHealth pay zero"); h.succeed(); });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void upgradingNeverAddsStacksOrDowngrades(GameTestHelper h) {
        var p=player(h); PassiveApi.grant(p,1,1); PassiveApi.grant(p,2,1);
        var d=PassiveData.read(p); d.adaptation=17; d.regeneration=23; d.save(p);
        PassivesMod.ADAPTATION_II_SERUM.get().finishUsingItem(new ItemStack(PassivesMod.ADAPTATION_II_SERUM.get()),h.getLevel(),p);
        PassivesMod.REGENERATION_II_SERUM.get().finishUsingItem(new ItemStack(PassivesMod.REGENERATION_II_SERUM.get()),h.getLevel(),p);
        d=PassiveData.read(p); near(h,d.adaptationTier,2,"upgraded A"); near(h,d.regenerationTier,2,"upgraded R"); near(h,d.adaptation,17,"no upgrade gift"); near(h,d.regeneration,23,"no upgrade reset");
        h.assertTrue(!PassiveApi.grant(p,1,1)&&!PassiveApi.grant(p,2,1),"cannot downgrade"); h.succeed();
    }
}
