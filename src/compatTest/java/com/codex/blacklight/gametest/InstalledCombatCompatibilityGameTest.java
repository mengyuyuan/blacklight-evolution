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
            titan.hurt(p.damageSources().playerAttack(p),10000);
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
}
