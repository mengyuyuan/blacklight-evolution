package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.power.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.gametest.*;

/** Exercises actual external entity/capability and spell code, not a fake stand-in. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CultivationNativeEcologyGameTest {
    private static Mob source(GameTestHelper h)throws Exception{
        var method=CultivationCompatibilityGameTest.class.getDeclaredMethod("source",GameTestHelper.class,String.class);method.setAccessible(true);return (Mob)method.invoke(null,h,"wandering_cultivator");
    }
    private static ServerPlayer player(GameTestHelper h)throws Exception{
        var method=CultivationIncomingGameTest.class.getDeclaredMethod("player",GameTestHelper.class);method.setAccessible(true);return (ServerPlayer)method.invoke(null,h);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @GameTest(template="wall_test",timeoutTicks=160,batch="cult_native_ecology")
    public static void actualCultivatorRetainsRealmQiEquipmentGeneticsAndNativeSpellCasting(GameTestHelper h)throws Exception{
        var npc=source(h);npc.setNoGravity(true);var uuid=npc.getUUID();var clazz=npc.getClass();
        var spellClass=Class.forName("com.xiaoxiang.cultivation.cultivation.spell.Spell");var spell=Enum.valueOf((Class)spellClass,"FIREBALL");
        clazz.getMethod("forceLearnSpellForGameTest",spellClass).invoke(npc,spell);
        npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        Object realm=clazz.getMethod("getRealm").invoke(npc);var techniques=List.copyOf((List<?>)clazz.getMethod("getTechniqueIds").invoke(npc));var spells=List.copyOf((List<?>)clazz.getMethod("getSpellIds").invoke(npc));
        h.assertTrue(!spells.isEmpty(),"the real original owns a learned spell before infection");
        h.assertTrue(BlacklightInfectionSystem.infect(npc,null)&&NativeInfection.preserve(npc),"foreign cultivator uses in-place infection");
        h.runAfterDelay(80,()->{Mob loaded=null;Mob target=null;try{
            h.assertTrue(h.getLevel().getEntity(uuid)==npc&&!npc.isRemoved(),"same real original survives beyond conversion onset");
            h.assertTrue(realm.equals(clazz.getMethod("getRealm").invoke(npc))&&techniques.equals(clazz.getMethod("getTechniqueIds").invoke(npc))&&spells.equals(clazz.getMethod("getSpellIds").invoke(npc)),"realm, techniques and learned spells are preserved");
            h.assertTrue(npc.getMainHandItem().is(Items.DIAMOND_SWORD)&&NativeInfection.age(npc)>0&&CombatScale.units(npc)==1,"gear, growing infection component and native health units coexist");
            long qi=((Number)clazz.getMethod("getCurrentQi").invoke(npc)).longValue();
            var saved=new CompoundTag();npc.saveWithoutId(saved);loaded=(Mob)npc.getType().create(h.getLevel());loaded.load(saved);
            h.assertTrue(realm.equals(clazz.getMethod("getRealm").invoke(loaded))&&((Number)clazz.getMethod("getCurrentQi").invoke(loaded)).longValue()==qi,"native entity save/load keeps actual realm and Qi");
            target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(npc.position().add(5,0,0));target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000000);target.setHealth(target.getMaxHealth());h.getLevel().addFreshEntity(target);npc.setTarget(target);
            var caster=Class.forName("com.xiaoxiang.cultivation.entity.npc.NpcSpellCaster");
            long needed=((Number)caster.getMethod("spellCostForCast",clazz,spellClass).invoke(null,npc,spell)).longValue();
            long reserve=Math.max(1000000,needed*4);clazz.getMethod("setCombatQiForGameTest",long.class,long.class).invoke(npc,reserve,reserve);
            long beforeQi=((Number)clazz.getMethod("getCurrentQi").invoke(npc)).longValue();float beforeHp=target.getHealth();
            boolean cast=(Boolean)caster.getMethod("cast",clazz,spellClass,LivingEntity.class).invoke(null,npc,spell,target);
            int nativeEffects=0;for(var entity:h.getLevel().getAllEntities())if(entity!=npc&&BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("xiaoxiang_cultivation")&&entity.position().distanceToSqr(npc.position())<1024)nativeEffects++;
            long afterQi=((Number)clazz.getMethod("getCurrentQi").invoke(npc)).longValue();
            System.out.println("CULT_NATIVE_CAST cast="+cast+" cost="+needed+" qi="+beforeQi+"->"+afterQi+" effects="+nativeEffects+" hpLoss="+(beforeHp-target.getHealth()));
            h.assertTrue(cast&&afterQi<beforeQi&&(nativeEffects>0||target.getHealth()<beforeHp),"the original native casting implementation still produces an actual attack and spends Qi");
            var row=Map.of("sameUuid",true,"realm",realm.toString(),"techniques",techniques,"spells",spells,"qiSpentByNativeCast",beforeQi-afterQi,"nativeAttackEntities",nativeEffects,"nativeAge",NativeInfection.age(npc),"hp",npc.getMaxHealth());
            java.nio.file.Files.writeString(java.nio.file.Path.of("cultivation-native-ecology.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(row));System.out.println("CULT_NATIVE_ECOLOGY "+row);h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}finally{if(loaded!=null)loaded.discard();if(target!=null)target.discard();npc.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="cult_player_namespace")
    public static void weaponNamespaceDoesNotTaxTheSameCultivationPlayer(GameTestHelper h)throws Exception{
        var p=player(h);var donor=source(h);var victim=ModEntities.INFECTED_HOST.get().create(h.getLevel());victim.inherit(donor);victim.setNoAi(true);h.getLevel().addFreshEntity(victim);
        try{
            var source=p.damageSources().playerAttack(p);boolean external=CrossModCombat.externalAttack(source);float sword=CrossModCombat.defend(victim,source,120);
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));float vanilla=CrossModCombat.defend(victim,p.damageSources().playerAttack(p),120);
            h.assertTrue(external&&sword==120&&vanilla==120,"same player and legal damage budget do not differ by sword namespace");
            java.nio.file.Files.writeString(java.nio.file.Path.of("cultivation-weapon-equivalence.json"),new com.google.gson.Gson().toJson(Map.of("cultivationSword",sword,"vanillaSword",vanilla)));h.succeed();
        }finally{donor.discard();victim.discard();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="cult_npc_elite_policy")
    public static void externalNpcDamageProtectsElitesWithoutBlanketFodderImmunity(GameTestHelper h)throws Exception{
        var attacker=source(h);var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());var cow=EntityType.COW.create(h.getLevel());var fodder=ModEntities.INFECTED_HOST.get().create(h.getLevel());fodder.inherit(cow);h.getLevel().addFreshEntity(hunter);h.getLevel().addFreshEntity(fodder);
        try{var hit=attacker.damageSources().mobAttack(attacker);float elite=CrossModCombat.defend(hunter,hit,20);float ordinary=CrossModCombat.defend(fodder,hit,20);
            h.assertTrue(CrossModCombat.externalAttack(hit)&&elite>0&&elite<20&&ordinary==20,"actual external NPC keeps elite protection while ordinary fodder uses normal damage");h.succeed();
        }finally{attacker.discard();hunter.discard();cow.discard();fodder.discard();}
    }
}
