package com.codex.blacklight.gametest;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.lang.reflect.*;
import java.util.UUID;

/** Invokes the installed third-party binaries; absent mods are a test failure. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InstalledCombatCompatibilityGameTest {
    private static ServerPlayer player(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CompatQA"));
        p.moveTo(h.absolutePos(new BlockPos(2,3,2)).getCenter());
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        return p;
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="compat_guns")
    public static void installedTaczMagazineCannotDestroyHive(GameTestHelper h) throws Exception {
        var p=player(h);var hive=h.spawn(ModEntities.BLACKLIGHT_HIVE.get(),5,2,5);hive.setNoAi(true);
        Class<?> bulletClass=Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        Projectile bullet=(Projectile)bulletClass.getConstructor(EntityType.class,Level.class)
                .newInstance(bulletClass.getField("TYPE").get(null),h.getLevel());
        bullet.setOwner(p);
        Class<?> wrapped=Class.forName("com.tacz.guns.entity.EntityKineticBullet$MaybeMultipartEntity");
        Object victim=wrapped.getMethod("of",Entity.class).invoke(null,hive);
        Method sources=bulletClass.getDeclaredMethod("createDamageSources",wrapped);sources.setAccessible(true);
        Object pair=sources.invoke(bullet,victim);
        Method attack=java.util.Arrays.stream(bulletClass.getDeclaredMethods()).filter(m->m.getName().equals("tacAttackEntity")).findFirst().orElseThrow();attack.setAccessible(true);
        Field ap=bulletClass.getDeclaredField("armorIgnore");ap.setAccessible(true);
        float initial=hive.getHealth(),units=CombatScale.units(hive);
        for(int i=0;i<30;i++) {
            final int shot=i;
            h.runAfterDelay(1+i*4,()->{
                try { ap.setFloat(bullet,shot%3==0?0:shot%3==1?.5F:1);attack.invoke(bullet,victim,100F,pair); }
                catch(Exception e){throw new RuntimeException(e);}
            });
        }
        h.runAfterDelay(125,()->{
            float lost=initial-hive.getHealth();
            h.assertTrue(lost>0&&lost<=12*HiveDefense.MAX_CHIP*units+.1F&&hive.isAlive(),"installed TaCZ bypassed hive interval: lost="+lost);
            h.assertTrue(!ImpactControl.stunned(hive),"installed gun knocked hive down");
            float before=hive.getHealth();hive.invulnerableTime=0;hive.hurt(p.damageSources().playerAttack(p),12);
            h.assertTrue(Math.abs(before-hive.getHealth()-12*units)<.01F,"Prototype melee lost full damage");
            BlacklightNeoMod.LOGGER.info("COMPAT_TACZ_PASS rounds=30 AP=0/0.5/1 initial={} lost={} remaining={}",initial,lost,hive.getHealth());
            hive.discard();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="compat_titans")
    public static void installedTitanReceivesHeightScaledDive(GameTestHelper h) throws Exception {
        var p=player(h);
        var candidates=BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k->
            (k.getNamespace().equals("thetitansneo")||k.getNamespace().equals("potentia"))
                &&k.getPath().contains("zombie")&&k.getPath().contains("titan")&&!k.getPath().contains("minion")).sorted().toList();
        h.assertTrue(!candidates.isEmpty(),"installed zombie titan entity missing");
        var key=candidates.getFirst();
        var titan=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());
        h.assertTrue(titan!=null,"cannot create installed titan");
        titan.moveTo(h.absolutePos(new BlockPos(5,3,5)).getCenter());
        if(titan instanceof Mob mob)mob.setNoAi(true);
        // Enter the ordinary vulnerable combat state; never overwrite HP or remove immunity checks.
        for(String name:new String[]{"setInvulTime","setAnimationID"})try{titan.getClass().getMethod(name,int.class).invoke(titan,0);}catch(NoSuchMethodException ignored){}
        try{titan.getClass().getMethod("setWaiting",boolean.class).invoke(titan,false);}catch(NoSuchMethodException ignored){}
        h.getLevel().addFreshEntity(titan);
        titan.setInvulnerable(true);
        float protectedHealth=titan.getHealth();
        titan.hurt(BulletdiveDamage.source(p),1000000);
        h.assertTrue(titan.getHealth()==protectedHealth,"compatibility bypassed invulnerability");
        titan.setInvulnerable(false);
        if(key.getNamespace().equals("thetitansneo")) {
            p.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(false);
            titan.hurt(p.damageSources().playerAttack(p),10000);
            p.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(true);
            h.assertTrue(Math.abs(protectedHealth-titan.getHealth()-1000)<.1,"ordinary attacks lost Titan cap");
        }
        float before=titan.getHealth();
        double height=Math.max(60000,30D*(before*2D/22-1));
        float damage=com.codex.blacklight.combat.BulletdivePhysics.impactDamage(22,height);
        boolean hit=titan.hurt(BulletdiveDamage.source(p),damage);
        BlacklightNeoMod.LOGGER.info("COMPAT_TITAN_HIT id={} class={} hp={} height={} damage={} accepted={} remaining={}",key,titan.getClass().getName(),before,height,damage,hit,titan.getHealth());
        h.assertTrue(hit&&titan.getHealth()<=0,"height-scaled dive capped by installed Titan: "+titan.getHealth()+" / "+before);
        h.assertTrue(!titan.isRemoved(),"damage must preserve native death sequence");
        titan.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="compat_tags")
    public static void installedTitanPreservesUndeadTags(GameTestHelper h) {
        h.assertTrue(EntityType.ZOMBIE.is(net.minecraft.tags.EntityTypeTags.UNDEAD),"installed Titan invalidated vanilla undead tag");
        h.assertTrue(EntityType.ZOMBIE.is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE),"installed Titan invalidated smite tag");
        h.assertTrue(EntityType.ZOMBIE.is(net.minecraft.tags.EntityTypeTags.INVERTED_HEALING_AND_HARM),"installed Titan invalidated healing inversion tag");
        h.succeed();
    }

    private static LivingEntity externalTitan(GameTestHelper h) throws Exception {
        var key=BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k ->
                (k.getNamespace().equals("thetitansneo") || k.getNamespace().equals("potentia"))
                && k.getPath().equals("zombie_titan")).findFirst().orElseThrow();
        var titan=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());
        titan.moveTo(h.absolutePos(new BlockPos(5,3,5)).getCenter());
        if(titan instanceof Mob mob)mob.setNoAi(true);
        for(String name:new String[]{"setInvulTime","setAnimationID"})try{titan.getClass().getMethod(name,int.class).invoke(titan,0);}catch(NoSuchMethodException ignored){}
        try{titan.getClass().getMethod("setWaiting",boolean.class).invoke(titan,false);}catch(NoSuchMethodException ignored){}
        return titan;
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="compat_general_defense")
    public static void externalBurstSharesBudgetAndDroppedItemsSurvive(GameTestHelper h) throws Exception {
        var attacker=externalTitan(h);
        var source=h.getLevel().damageSources().mobAttack((LivingEntity)attacker);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,3);hunter.setNoAi(true);
        float maximum=hunter.getMaxHealth(),before=hunter.getHealth();
        for(int i=0;i<30;i++){hunter.invulnerableTime=0;hunter.hurt(source,1000000);}
        h.assertTrue(hunter.isAlive() && Math.abs(before-hunter.getHealth()-maximum*.15F)<.1F,"external burst bypassed final HP budget: "+hunter.getHealth());
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),hunter.getX(),hunter.getY()+2,hunter.getZ(),ModItems.LIVING_HIVE_TISSUE.get().getDefaultInstance());
        h.getLevel().addFreshEntity(item);
        for(int i=0;i<30;i++)item.hurt(source,1000000);
        h.assertTrue(item.isAlive() && item.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Health")==4,"item burst bypassed integral damage budget");
        var ordinary=h.spawn(EntityType.COW,8,2,8);ordinary.setNoAi(true);ordinary.hurt(source,1000000);
        h.assertTrue(!ordinary.isAlive(),"external protection leaked into ordinary vanilla mobs");
        h.runAfterDelay(com.codex.blacklight.config.CrossModCombatConfig.WINDOW_TICKS.get()+1,()->{
            hunter.invulnerableTime=0;float previous=hunter.getHealth();hunter.hurt(source,1000000);
            float singleLimit=(float)(maximum*Math.min(com.codex.blacklight.config.CrossModCombatConfig.WINDOW_FRACTION.get(),
                    com.codex.blacklight.config.CrossModCombatConfig.MOB_HIT_FRACTION.get()));
            h.assertTrue(Math.abs(previous-hunter.getHealth()-singleLimit)<.1F,"fresh window must admit the configured single-hit budget: "+(previous-hunter.getHealth()));
            item.hurt(source,1000000);
            h.assertTrue(item.isAlive() && item.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Health")==3,"item became immortal");
            hunter.hurt(h.getLevel().damageSources().genericKill(),10000000);
            h.assertTrue(!hunter.isAlive(),"administrative kill was blocked");
            item.discard();attacker.discard();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="compat_general_offense")
    public static void nativeMeleeIgnoresArmorAndKillsTitanAtSufficientDamage(GameTestHelper h) throws Exception {
        var p=player(h);var titan=externalTitan(h);h.getLevel().addFreshEntity(titan);
        titan.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(30);
        titan.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,600,4));
        float before=titan.getHealth();titan.invulnerableTime=0;titan.hurt(p.damageSources().playerAttack(p),1500);
        h.assertTrue(Math.abs(before-titan.getHealth()-3000)<.2F,"native melee was capped, not doubled, or absorbed: "+(before-titan.getHealth()));
        var cow=h.spawn(EntityType.COW,3,2,3);cow.setNoAi(true);float cowBefore=cow.getHealth();cow.hurt(p.damageSources().playerAttack(p),2);
        h.assertTrue(Math.abs(cowBefore-cow.getHealth()-2)<.01F,"external offense changed vanilla combat");
        titan.invulnerableTime=0;titan.hurt(p.damageSources().playerAttack(p),titan.getHealth()/2+1);
        h.assertTrue(!titan.isAlive() && !titan.isRemoved(),"sufficient native damage did not enter normal Titan death");
        BlacklightNeoMod.LOGGER.info("COMPAT_NATIVE_KILL_PASS hp={} firstHit=3000 remaining={} owner={}",before,titan.getHealth(),titan.getLastDamageSource().getEntity()==p);
        h.assertTrue(titan.getLastDamageSource().getEntity()==p,"native kill lost player attribution");
        titan.discard();cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="compat_general_switches")
    public static void externalWeaponsDoNotGainNativeDamageAndDefenseCanBeDisabled(GameTestHelper h) throws Exception {
        var p=player(h);
        var gunId=BuiltInRegistries.ITEM.keySet().stream().filter(k->k.getNamespace().equals("tacz")&&k.getPath().equals("modern_kinetic_gun")).findFirst().orElseThrow();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,BuiltInRegistries.ITEM.get(gunId).getDefaultInstance());
        var source=p.damageSources().playerAttack(p);
        h.assertTrue(!CrossModCombat.nativeAttack(source) && CrossModCombat.externalAttack(source),"foreign held item inherited native offense");
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,3);hunter.setNoAi(true);
        com.codex.blacklight.config.CrossModCombatConfig.DEFENSE.set(false);
        try {hunter.hurt(source,1000000);h.assertTrue(!hunter.isAlive(),"defense config did not disable protection");}
        finally {com.codex.blacklight.config.CrossModCombatConfig.DEFENSE.set(true);}
        var target=externalTitan(h);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        com.codex.blacklight.config.CrossModCombatConfig.OFFENSE.set(false);
        try {h.assertTrue(!CrossModCombat.enhanced(target,p.damageSources().playerAttack(p)),"offense config did not disable enhancement");}
        finally {com.codex.blacklight.config.CrossModCombatConfig.OFFENSE.set(true);}
        com.codex.blacklight.config.CrossModCombatConfig.TRUE_DAMAGE.set(false);
        try {var melee=p.damageSources().playerAttack(p);h.assertTrue(!CrossModCombat.offensiveSource(melee).is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),"true damage toggle still replaced source");}
        finally {com.codex.blacklight.config.CrossModCombatConfig.TRUE_DAMAGE.set(true);}
        target.discard();h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=60,batch="compat_general_player")
    public static void activePrototypeAlsoSharesFinalDamageBudget(GameTestHelper h) throws Exception {
        var profile=new GameProfile(UUID.randomUUID(),"DamageableQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.moveTo(h.absolutePos(new BlockPos(3,3,3)).getCenter());
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getAbilities().invulnerable=false;
        CombatScale.attributes(p,true);p.setHealth(p.getMaxHealth());
        var attacker=externalTitan(h);var source=h.getLevel().damageSources().mobAttack(attacker);
        float before=p.getHealth();
        for(int i=0;i<30;i++){p.invulnerableTime=0;p.hurt(source,1000000);}
        float lost=before-p.getHealth();
        h.assertTrue(lost>0 && lost<=p.getMaxHealth()*.15F+.01F && p.isAlive(),"Prototype final HP budget failed: "+lost);
        attacker.discard();h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=60,batch="compat_titan_real_attack")
    public static void titanOwnAttackCannotForceDeleteProtectedHunter(GameTestHelper h) throws Exception {
        var attacker=externalTitan(h);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),3,2,3);hunter.setNoAi(true);
        float maximum=hunter.getMaxHealth(),before=hunter.getHealth();
        var attack=attacker.getClass().getMethod("attackEntity",LivingEntity.class,float.class);
        for(int i=0;i<30;i++)attack.invoke(attacker,hunter,1000000F);
        float lost=before-hunter.getHealth();
        h.assertTrue(lost>0 && lost<=maximum*.15F+.1F && hunter.isAlive() && !hunter.isRemoved(),"Titan attack fallback bypassed protection: lost="+lost);
        h.assertTrue(hunter.getMaxHealth()==maximum,"Titan rewrote victim maximum health");
        attacker.discard();hunter.discard();h.succeed();
    }
}
