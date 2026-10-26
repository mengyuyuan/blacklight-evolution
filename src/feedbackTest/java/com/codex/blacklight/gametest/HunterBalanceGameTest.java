package com.codex.blacklight.gametest;
import com.codex.blacklight.combat.HunterBalance;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.power.CombatScale;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HunterBalanceGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void newHunterReceivesEliteHealthAndArmor(GameTestHelper h){
        var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());hunter.setNoAi(true);hunter.setPos(h.absolutePos(new net.minecraft.core.BlockPos(3,3,3)).getCenter());h.getLevel().addFreshEntity(hunter);
        try{h.assertTrue(hunter.getMaxHealth()==2500,"new hunter has 2500 actual health after blood unit conversion");h.assertTrue(hunter.getAttributeValue(Attributes.ATTACK_DAMAGE)==28,"attack attribute is 28");h.assertTrue(hunter.getArmorValue()==8&&hunter.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==2,"elite armor and toughness are applied");}
        finally{hunter.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void legacyStockHuntersMigrateWithoutHealingOrRepeatedScaling(GameTestHelper h){
        for(int version=0;version<=1;version++){
            var old=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());var fresh=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());var reloaded=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());
            try{
                old.getAttribute(Attributes.MAX_HEALTH).setBaseValue(version==0?48:96);old.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(version==0?7:14);
                old.getAttribute(Attributes.ARMOR).setBaseValue(0);old.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);CombatScale.attributes(old,true);old.setHealth(old.getMaxHealth()*.35F);
                var tag=new CompoundTag();old.saveWithoutId(tag);tag.putInt("BlacklightHunterBalanceVersion",version);fresh.load(tag);
                h.assertTrue(fresh.getMaxHealth()==2500&&Math.abs(fresh.getHealth()/fresh.getMaxHealth()-.35F)<.001,"old stock version "+version+" upgrades while keeping injury");
                h.assertTrue(fresh.getArmorValue()==8,"old armor migrates");var saved=new CompoundTag();fresh.saveWithoutId(saved);reloaded.load(saved);
                h.assertTrue(reloaded.getMaxHealth()==2500&&reloaded.getHealth()==fresh.getHealth(),"repeated load never compounds health or heals");
            }finally{old.discard();fresh.discard();reloaded.discard();}
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void customizedLegacyAttributesArePreserved(GameTestHelper h){
        var old=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());var fresh=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());
        try{old.getAttribute(Attributes.MAX_HEALTH).setBaseValue(888);old.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(90);old.setHealth(333);var tag=new CompoundTag();old.saveWithoutId(tag);tag.putInt("BlacklightHunterBalanceVersion",1);fresh.load(tag);
            h.assertTrue(fresh.getAttribute(Attributes.MAX_HEALTH).getBaseValue()==888&&fresh.getAttributeValue(Attributes.ATTACK_DAMAGE)==90&&fresh.getHealth()==333,"server-authored custom variants are not overwritten by stock migration");}
        finally{old.discard();fresh.discard();}h.succeed();
    }
}
