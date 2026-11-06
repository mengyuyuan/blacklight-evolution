package com.codex.blacklight.gametest;

import com.codex.blacklight.compat.CultivationCompatibility;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.config.CrossModCombatConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.gametest.*;

/** Requires the real installed optional mod. No foreign implementation is included. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CultivationCompatibilityGameTest {
    private static final Map<String,Object> ROWS=new LinkedHashMap<>();
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Mob source(GameTestHelper h,String id)throws Exception {
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var key=new ResourceLocation("xiaoxiang_cultivation",id);
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key),"installed cultivation entity "+id);
        var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(key).create(h.getLevel());
        mob.setNoAi(true);mob.moveTo(h.absolutePos(new BlockPos(5,30,5)).getCenter());
        if(id.equals("wandering_cultivator")) {
            var realm=Class.forName("com.xiaoxiang.cultivation.cultivation.realm.Realm");
            mob.getClass().getMethod("setRealmForGameTest",realm).invoke(mob,Enum.valueOf((Class)realm,"TRUE_IMMORTAL"));
            var technique=Class.forName("com.xiaoxiang.cultivation.cultivation.technique.Technique");
            mob.getClass().getMethod("setTechniqueLoadoutForGameTest",List.class).invoke(mob,List.of(Enum.valueOf((Class)technique,"METAL_SWORD")));
            var m=mob.getClass().getDeclaredMethod("applyNpcDerivedCultivationAttributes",boolean.class);m.setAccessible(true);m.invoke(mob,true);
            mob.getClass().getMethod("setCombatQiForGameTest",long.class,long.class).invoke(mob,1000000L,1000000L);
        }
        h.getLevel().addFreshEntity(mob);mob.setHealth(mob.getMaxHealth());return mob;
    }
    private static ServerPlayer player(GameTestHelper h)throws Exception {
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"CultCompatQA")) {
            @Override public boolean isInvulnerableTo(DamageSource s){return getAbilities().invulnerable&&!s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);}
        };
        // FakePlayer.tick is empty: its normal 60-tick spawn grace never counts down.
        var grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
        p.moveTo(h.absolutePos(new BlockPos(7,30,5)).getCenter());
        // Complete the dependency's initial identity choice, as a normal player must.
        var cap=Class.forName("com.xiaoxiang.cultivation.cultivation.CultivationCapability");
        var data=((Optional<?>)cap.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,p)).orElseThrow();
        data.getClass().getMethod("setIdentityId",String.class).invoke(data,"mortal");
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);p.setHealth(p.getMaxHealth());
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        return p;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void cultivate(ServerPlayer p)throws Exception {
        var cap=Class.forName("com.xiaoxiang.cultivation.cultivation.CultivationCapability");
        var data=((Optional<?>)cap.getMethod("get",net.minecraft.world.entity.player.Player.class).invoke(null,p)).orElseThrow();
        var realm=Class.forName("com.xiaoxiang.cultivation.cultivation.realm.Realm");
        data.getClass().getMethod("setRealm",realm).invoke(data,Enum.valueOf((Class)realm,"TRUE_IMMORTAL"));
        data.getClass().getMethod("setCurrentQi",long.class).invoke(data,0L);
    }
    private static BlacklightInfectedHost host(GameTestHelper h,Mob source) {
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(source);host.setNoAi(true);
        host.moveTo(h.absolutePos(new BlockPos(9,30,5)).getCenter());h.getLevel().addFreshEntity(host);host.setHealth(host.getMaxHealth());return host;
    }
    private static void report(String key,Map<String,Object> row)throws Exception {
        ROWS.put(key,row);var path=java.nio.file.Path.of("cultivation-regression.json");
        java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(ROWS));
        System.out.println("CULTIVATION_REGRESSION "+key+" "+row);
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="cultivation_large_stats")
    public static void realHighHealthCultivatorKeepsItsEffectiveAttackAndUnlimitedGrowth(GameTestHelper h)throws Exception {
        var npc=source(h,"wandering_cultivator");var p=player(h);BlacklightInfectedHost infected=null;
        var target=EntityType.COW.create(h.getLevel());
        try{
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);npc.setHealth(npc.getMaxHealth());
            npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5000);
            double nativeHp=CombatScale.originalMaximum(npc),nativeAttack=npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double effective=CultivationCompatibility.inheritedAttack(npc,nativeAttack);
            infected=host(h,npc);
            double expectedHealth=com.codex.blacklight.infection.InfectionStatScaling.health(nativeHp,com.codex.blacklight.infection.InfectionStatScaling.rules());
            double expectedAttack=com.codex.blacklight.infection.InfectionStatScaling.attack(nativeHp,effective,com.codex.blacklight.infection.InfectionStatScaling.rules());
            h.assertTrue(nativeHp>=10000&&CombatScale.units(infected)==1&&Math.abs(infected.getMaxHealth()-expectedHealth)<.1,"real high native HP uses the small excess gain and no fivefold units");
            h.assertTrue(effective>nativeAttack&&expectedAttack>2048&&Math.abs(infected.getAttributeValue(Attributes.ATTACK_DAMAGE)-expectedAttack)<.01,"public technique bonus survives the local vanilla attribute clamp");
            CombatScale.modifier(infected.getAttribute(Attributes.ATTACK_DAMAGE),new ResourceLocation("blacklight","qa_cultivation_growth"),1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
            h.assertTrue(Math.abs(infected.getAttributeValue(Attributes.ATTACK_DAMAGE)-expectedAttack*2)<.01,"earned attack growth remains unlimited");
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000000);target.setHealth(target.getMaxHealth());target.setNoAi(true);h.getLevel().addFreshEntity(target);
            float before=target.getHealth();h.assertTrue(infected.doHurtTarget(target)&&Math.abs((before-target.getHealth())-expectedAttack*2)<.2,"effective large attack causes real health loss");
            cultivate(p);p.setHealth(p.getMaxHealth());p.invulnerableTime=0;before=p.getHealth();h.assertTrue(infected.doHurtTarget(p)&&before-p.getHealth()>10&&p.isAlive(),"a strong infected cultivator can damage a cultivating player without the ordinary trash ceiling");
            report("highNativeStats",Map.of("sourceHp",nativeHp,"infectedHp",infected.getMaxHealth(),"nativeAttributeAttack",nativeAttack,"effectiveNativeAttack",effective,"infectedAttackAfterGrowth",infected.getAttributeValue(Attributes.ATTACK_DAMAGE),"cultivatingPlayerLoss",before-p.getHealth()));
        }finally{npc.discard();if(infected!=null)infected.discard();target.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="cultivation_melee")
    public static void conversionPreservesAttackAndMigratesNativeHealth(GameTestHelper h)throws Exception {
        var npc=source(h,"wandering_cultivator");var p=player(h);var host=host(h,npc);BlacklightInfectedHost loaded=null;
        boolean policy=CrossModCombatConfig.CULTIVATION_NATIVE_HEALTH.get();
        try {
            float nativeHp=CombatScale.originalMaximum(npc);double attack=npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double inherited=CultivationCompatibility.inheritedAttack(npc,attack);
            h.assertTrue(inherited>attack,"real technique melee bonus is present before conversion");
            h.assertTrue(CombatScale.units(host)==1&&Math.abs(host.getMaxHealth()-Math.max(18,nativeHp*1.4))<.1,"conversion must not add fivefold HP units");
            h.assertTrue(host.getAttributeValue(Attributes.ATTACK_DAMAGE)>=inherited*1.3-.01,"converted attack retains supported technique bonus");
            float hp=p.getHealth();boolean accepted=host.doHurtTarget(p);float lost=hp-p.getHealth();
            System.out.println("CULT_PLAYER cultivator accepted="+accepted+" loss="+lost+" difficulty="+p.level().getDifficulty()+" abilities="+p.getAbilities().invulnerable+" invuln="+p.isInvulnerableTo(p.damageSources().mobAttack(host))+" armor="+p.getArmorValue()+" source="+host.getAttributeValue(Attributes.ATTACK_DAMAGE));
            h.assertTrue(accepted&&lost>0,"converted cultivator must damage an ordinary unshielded survival player");
            var row=new LinkedHashMap<String,Object>();row.put("sourceHp",nativeHp);row.put("infectedHp",host.getMaxHealth());row.put("sourceAttributeAttack",attack);row.put("inheritedEffectiveAttack",inherited);row.put("infectedAttack",host.getAttributeValue(Attributes.ATTACK_DAMAGE));row.put("playerHpLoss",lost);
            cultivate(p);
            boolean late=CrossModCombatConfig.CULTIVATION_LATE_BUDGET.get();
            try {
                CrossModCombatConfig.CULTIVATION_LATE_BUDGET.set(false);p.invulnerableTime=0;p.setHealth(p.getMaxHealth());hp=p.getHealth();host.doHurtTarget(p);float earlyLoss=hp-p.getHealth();
                CrossModCombatConfig.CULTIVATION_LATE_BUDGET.set(true);p.invulnerableTime=0;p.setHealth(p.getMaxHealth());hp=p.getHealth();host.doHurtTarget(p);float lateLoss=hp-p.getHealth();
                h.assertTrue(earlyLoss<=lateLoss&&lateLoss>0&&lateLoss<=10.01,"real registered order preserves bounded damage: old="+earlyLoss+" fixed="+lateLoss);
                // Both mods use HIGH priority: verify the opposite legal registration order too.
                var shield=Class.forName("com.xiaoxiang.cultivation.event.QiShieldHandler");
                var early=new net.minecraftforge.event.entity.living.LivingHurtEvent(p,p.damageSources().mobAttack(host),(float)host.getAttributeValue(Attributes.ATTACK_DAMAGE));
                CrossModCombatConfig.CULTIVATION_LATE_BUDGET.set(false);com.codex.blacklight.power.InfectedCombatBalance.incoming(early);
                shield.getMethod("onLivingHurt",early.getClass()).invoke(null,early);
                float reversedLegacy=early.isCanceled()?0:early.getAmount();
                var after=new net.minecraftforge.event.entity.living.LivingHurtEvent(p,p.damageSources().mobAttack(host),(float)host.getAttributeValue(Attributes.ATTACK_DAMAGE));
                CrossModCombatConfig.CULTIVATION_LATE_BUDGET.set(true);com.codex.blacklight.power.InfectedCombatBalance.incoming(after);
                shield.getMethod("onLivingHurt",after.getClass()).invoke(null,after);
                float reversedFixed=after.isCanceled()?0:CultivationCompatibility.afterCultivationDefense(p,after.getSource(),after.getAmount());
                h.assertTrue(reversedLegacy==0&&reversedFixed>0&&reversedFixed<=2.01,"opposite real callback order must no longer collapse damage to zero");
                row.put("oppositeRegistrationLegacyDamage",reversedLegacy);row.put("oppositeRegistrationFixedDamage",reversedFixed);
                row.put("cultivatingPlayerLegacyLoss",earlyLoss);row.put("cultivatingPlayerFixedLoss",lateLoss);
            }finally{CrossModCombatConfig.CULTIVATION_LATE_BUDGET.set(late);}
            float maximum=host.getMaxHealth();host.setHealth(maximum*.4F);
            CrossModCombatConfig.CULTIVATION_NATIVE_HEALTH.set(false);CombatScale.synchronizeMob(host);
            h.assertTrue(CombatScale.units(host)==5&&Math.abs(host.getMaxHealth()-maximum*5)<.1,"config opt-out restores old units");
            var saved=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(saved);
            CrossModCombatConfig.CULTIVATION_NATIVE_HEALTH.set(true);
            loaded=ModEntities.INFECTED_HOST.get().create(h.getLevel());loaded.load(saved);h.getLevel().addFreshEntity(loaded);
            h.assertTrue(CombatScale.units(loaded)==1&&Math.abs(loaded.getMaxHealth()-maximum)<.1&&Math.abs(loaded.getHealth()/maximum-.4)<.001,"saved legacy host migrates without healing or killing it");
            report("cultivator",row);
        }finally{CrossModCombatConfig.CULTIVATION_NATIVE_HEALTH.set(policy);npc.discard();host.discard();if(loaded!=null)loaded.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="cultivation_shield")
    public static void trueDamageHonorsQiOptOutAndInvulnerability(GameTestHelper h)throws Exception {
        var npc=source(h,"wandering_cultivator");var p=player(h);
        boolean bypass=CrossModCombatConfig.CULTIVATION_TRUE_DAMAGE.get(),offense=CrossModCombatConfig.OFFENSE.get();
        try {
            var shield=Class.forName("com.xiaoxiang.cultivation.event.QiShieldHandler");
            System.out.println("CULT_SHIELD_MIXINS "+Arrays.stream(shield.getDeclaredMethods()).map(java.lang.reflect.Method::getName).filter(n->n.contains("blacklight")).toList());
            var wrapped=CrossModCombat.offensiveSource(DevastatorDamage.source(p));
            var attackEvent=new net.minecraftforge.event.entity.living.LivingAttackEvent(npc,wrapped,40);
            var hurtEvent=new net.minecraftforge.event.entity.living.LivingHurtEvent(npc,wrapped,40);
            System.out.println("CULT_SHIELD_PREDICATE "+CultivationCompatibility.bypassMobShield(attackEvent));
            h.assertTrue(CultivationCompatibility.bypassMobShield(attackEvent),"loaded event/source is eligible for native shield bridge");
            long beforeQi=((Number)npc.getClass().getMethod("getCurrentQi").invoke(npc)).longValue();
            shield.getMethod("onLivingAttack",attackEvent.getClass()).invoke(null,attackEvent);
            shield.getMethod("onLivingHurt",hurtEvent.getClass()).invoke(null,hurtEvent);
            h.assertTrue(!attackEvent.isCanceled()&&!hurtEvent.isCanceled()&&hurtEvent.getAmount()==40&&beforeQi==((Number)npc.getClass().getMethod("getCurrentQi").invoke(npc)).longValue(),"actual shield callbacks bypass native true damage without spending Qi");
            float original=npc.getHealth();long qi=((Number)npc.getClass().getMethod("getCurrentQi").invoke(npc)).longValue();
            var trace=new Object(){
                @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
                public void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(e.getEntity()==npc)System.out.println("CULT_TRUE_ATTACK damage="+e.getAmount()+" canceled="+e.isCanceled()+" bypass="+CultivationCompatibility.bypassMobShield(e)+" tags="+e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)+" source="+e.getSource());}
                @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
                public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(e.getEntity()==npc)System.out.println("CULT_TRUE_HURT damage="+e.getAmount()+" canceled="+e.isCanceled()+" bypass="+CultivationCompatibility.bypassMobShield(e));}
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);boolean accepted;
            try{accepted=DevastatorDamage.hurt(p,npc,20);}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(trace);}
            System.out.println("CULT_TRUE_RESULT accepted="+accepted+" hp="+original+"->"+npc.getHealth()+" qi="+qi+"->"+npc.getClass().getMethod("getCurrentQi").invoke(npc)+" invuln="+npc.isInvulnerableTo(DevastatorDamage.source(p))+" grace="+npc.invulnerableTime);
            if(!accepted){
                var f=net.minecraftforge.common.MinecraftForge.EVENT_BUS.getClass().getDeclaredField("busID");f.setAccessible(true);int id=f.getInt(net.minecraftforge.common.MinecraftForge.EVENT_BUS);
                var probe=new net.minecraftforge.event.entity.living.LivingAttackEvent(npc,DevastatorDamage.source(p),20);
                for(var listener:probe.getListenerList().getListeners(id)){listener.invoke(probe);if(probe.isCanceled()){System.out.println("CULT_FIRST_CANCEL "+listener);break;}}
            }
            h.assertTrue(accepted,"native true hit accepted");
            float lost=original-npc.getHealth();
            System.out.println("CULT_SHIELD amount="+lost+" mult="+CrossModCombatConfig.OUTGOING.get()+" offense="+CrossModCombatConfig.OFFENSE.get()+" true="+CrossModCombatConfig.TRUE_DAMAGE.get());
            h.assertTrue(lost>0&&lost<=CrossModCombat.offensiveAmount(20)+.01,"full native hit remains damageable within its external budget: "+lost);

            npc.invulnerableTime=0;npc.setHealth(original);CrossModCombatConfig.CULTIVATION_TRUE_DAMAGE.set(false);
            DevastatorDamage.hurt(p,npc,20);float legacy=original-npc.getHealth();
            h.assertTrue(legacy<lost,"disabling bridge restores the mod's real independent shield");
            npc.invulnerableTime=0;npc.setHealth(original);CrossModCombatConfig.CULTIVATION_TRUE_DAMAGE.set(true);npc.setInvulnerable(true);
            h.assertTrue(!DevastatorDamage.hurt(p,npc,20)&&npc.getHealth()==original,"administrative invulnerability remains effective");
            npc.setInvulnerable(false);CrossModCombatConfig.OFFENSE.set(false);
            h.assertTrue(CultivationCompatibility.bypassMobShield(new net.minecraftforge.event.entity.living.LivingHurtEvent(npc,DevastatorDamage.source(p),20)),"formal true damage remains explicit when blanket external offense is disabled");
            h.assertTrue(!CultivationCompatibility.bypassMobShield(new net.minecraftforge.event.entity.living.LivingHurtEvent(p,DevastatorDamage.source(p),20)),"player/PvP shields are not overridden");
            report("qiShield",Map.of("trueHpLoss",lost,"bridgeDisabledHpLoss",legacy,"shieldCallbacksDoNotSpendQi",true,"invulnerabilityPreserved",true));
        }finally{CrossModCombatConfig.CULTIVATION_TRUE_DAMAGE.set(bypass);CrossModCombatConfig.OFFENSE.set(offense);npc.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="cultivation_beast")
    public static void convertedBeastDamagesPlayerAndRetainsNativeHp(GameTestHelper h)throws Exception {
        // Snow wolf was introduced after the older server distribution; its turtle is supported there.
        String id=BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation("xiaoxiang_cultivation","shuangya_xuelang"))?"shuangya_xuelang":"xuan_gui";
        var beast=source(h,id);var p=player(h);var host=host(h,beast);
        try {
            h.assertTrue(CombatScale.units(host)==1&&Math.abs(host.getMaxHealth()-Math.max(18,beast.getMaxHealth()*1.4))<.1,"beast infection retains native health units");
            cultivate(p);float before=p.getHealth();boolean hit=host.doHurtTarget(p);float loss=before-p.getHealth();
            System.out.println("CULT_PLAYER beast accepted="+hit+" loss="+loss+" difficulty="+p.level().getDifficulty()+" abilities="+p.getAbilities().invulnerable+" invuln="+p.isInvulnerableTo(p.damageSources().mobAttack(host)));
            h.assertTrue(hit&&loss>0,"converted beast must damage an ordinary unshielded survival player");
            report("beast",Map.of("species",id,"sourceHp",beast.getMaxHealth(),"infectedHp",host.getMaxHealth(),"infectedAttack",host.getAttributeValue(Attributes.ATTACK_DAMAGE),"playerHpLoss",loss));
        }finally{beast.discard();host.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="cultivation_boundaries")
    public static void otherSpeciesAndProtectionVetoRemainEffective(GameTestHelper h)throws Exception {
        var cow=EntityType.COW.create(h.getLevel());h.getLevel().addFreshEntity(cow);var host=host(h,cow);var npc=source(h,"wandering_cultivator");var p=player(h);
        var veto=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
            public void stop(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(e.getEntity()==npc)e.setCanceled(true);}};
        try {
            h.assertTrue(CombatScale.units(host)==5,"vanilla infected health presentation remains unchanged");
            float hp=npc.getHealth();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);
            h.assertTrue(!DevastatorDamage.hurt(p,npc,20)&&npc.getHealth()==hp,"bridge never clears a protection listener's veto");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);cow.discard();host.discard();npc.discard();p.discard();}h.succeed();
    }
}
