package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.power.CombatScale;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HunterAliasGameTest {
    @GameTest(template="blackwatch_arena",batch="alias_spawn",timeoutTicks=35)
    public static void oldEggTypeSpawnsOriginalHunter(GameTestHelper h){
        var old=EncounterEntities.LEGACY_BRAWLER.get();
        h.assertTrue(net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(old)==EncounterEntities.LEGACY_BRAWLER_EGG.get(),"old egg retains its own dispatch");
        h.assertTrue(net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(ModEntities.BLACKLIGHT_HUNTER.get())==ModItems.HUNTER_SPAWN_EGG.get(),"canonical egg is not replaced");
        var mob=old.spawn(h.getLevel(),h.absolutePos(new net.minecraft.core.BlockPos(8,2,8)),MobSpawnType.SPAWN_EGG);
        h.assertTrue(mob!=null&&mob.getType()==ModEntities.BLACKLIGHT_HUNTER.get(),"legacy spawn uses original type and renderer");
        h.assertTrue(mob.getMaxHealth()==2500&&mob.getHealth()==2500,"original health retained");
        h.assertTrue(mob.getArmorValue()==8&&mob.getAttributeValue(Attributes.ATTACK_DAMAGE)==28,"original defense and attack retained");
        mob.discard();h.succeed();
    }
    private static CompoundTag legacy(GameTestHelper h,boolean custom){
        var old=EncounterEntities.CREATURES.get(EncounterKind.JUGGERNAUT).get().create(h.getLevel());
        old.setPos(h.absoluteVec(new Vec3(8,2,8)));old.setNoAi(true);old.setPersistenceRequired();
        old.getAttribute(Attributes.MAX_HEALTH).setBaseValue(custom?321:100);
        old.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(custom?57:9);
        old.getAttribute(Attributes.ARMOR).setBaseValue(custom?19:0);
        old.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.4);
        CombatScale.attributes(old,true);old.setHealth(old.getMaxHealth()*.35F);
        old.setCustomName(Component.literal("legacy hunter"));old.addTag("keep-owner");
        old.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        var tag=new CompoundTag();old.save(tag);tag.putString("id","blacklight:brawler");old.discard();return tag;
    }
    @GameTest(template="blackwatch_arena",batch="alias_load",timeoutTicks=35)
    public static void oldSaveMigratesIdentityInjuryAndEquipment(GameTestHelper h){
        var tag=legacy(h,false);
        var entity=EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
        h.assertTrue(entity instanceof BlacklightHunter,"old save loads original class");var mob=(BlacklightHunter)entity;
        h.assertTrue(mob.getUUID().equals(tag.getUUID("UUID")),"UUID and entity references retained");
        h.assertTrue(mob.getMaxHealth()==2500&&Math.abs(mob.getHealth()-875)<.01,"35 percent injury preserved at 2500 max health");
        h.assertTrue(mob.isNoAi()&&mob.isPersistenceRequired()&&mob.getTags().contains("keep-owner"),"standard saved flags retained");
        h.assertTrue(mob.getCustomName().getString().equals("legacy hunter")&&mob.getMainHandItem().is(Items.DIAMOND_SWORD),"name and equipment retained");
        var saved=new CompoundTag();mob.save(saved);
        h.assertTrue(saved.getString("id").equals("blacklight:blacklight_hunter")&&!saved.contains("EncounterPhase"),"next save canonicalizes the ID");
        var again=(BlacklightHunter)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
        h.assertTrue(again.getMaxHealth()==2500&&again.getHealth()==mob.getHealth(),"reload never heals or multiplies again");
        mob.discard();again.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="alias_custom",timeoutTicks=35)
    public static void customizedLegacyStatsSurvive(GameTestHelper h){
        var tag=legacy(h,true);var mob=(BlacklightHunter)EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
        h.assertTrue(mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue()==321&&mob.getAttributeValue(Attributes.ATTACK_DAMAGE)==57&&mob.getArmorValue()==19,"server custom attributes are not replaced with stock values");
        h.assertTrue(Math.abs(mob.getHealth()/mob.getMaxHealth()-.35F)<.001,"custom injury retained");mob.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="retired_bosses",timeoutTicks=45)
    public static void retiredDraftsCannotFightAndSupremeRemainsAvailable(GameTestHelper h){
        var cow=h.spawn(EntityType.COW,new Vec3(8,2,8));cow.setNoAi(true);
        var retired=new java.util.ArrayList<EncounterMob>();
        for(var kind:EncounterKind.values())if(!kind.enabled()&&kind!=EncounterKind.BRAWLER){
            var mob=EncounterEntities.CREATURES.get(kind).get().create(h.getLevel());mob.setPos(cow.position().add(0,0,-2));
            mob.setTarget(cow);h.getLevel().addFreshEntity(mob);retired.add(mob);
            h.assertTrue(net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(mob.getType())==null,"retired egg cannot spawn "+kind);
        }
        h.assertTrue(EncounterEntities.allSpawnEggs().contains(ModItems.SUPREME_HUNTER_SPAWN_EGG.get()),"existing Supreme Hunter remains in the tab");
        h.assertTrue(net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(ModEntities.SUPREME_HUNTER.get())==ModItems.SUPREME_HUNTER_SPAWN_EGG.get(),"existing Supreme Hunter spawn dispatch unchanged");
        h.runAfterDelay(30,()->{
            for(var mob:retired){h.assertTrue(mob.isNoAi()&&mob.move()==EncounterMove.NONE&&!mob.begin(mob.kind().choose(2,0,1),cow),"retired draft stays dormant "+mob.kind());mob.discard();}
            h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"no draft damage");cow.discard();h.succeed();
        });
    }
}
