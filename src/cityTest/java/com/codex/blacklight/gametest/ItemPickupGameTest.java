package com.codex.blacklight.gametest;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.SkillSettings;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class ItemPickupGameTest {
    @GameTest(template="wall_test")
    public static void pickupSwitchKeepsDropsAndVanillaRules(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(
                java.util.UUID.fromString("95e66358-7848-45be-b8e0-5c3cc652a492"),"PickupTest"));
        player.getInventory().clearContent();
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);
        ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
        ModAttachments.setData(player,ModAttachments.SKILL_SETTINGS,SkillSettings.DEFAULT.withPrototypeItemPickup(false));
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        player.setPos(pos.getX(),pos.getY(),pos.getZ());
        var item=new ItemEntity(h.getLevel(),pos.getX(),pos.getY(),pos.getZ(),new ItemStack(Items.DIAMOND,3));
        item.setNoPickUpDelay();h.getLevel().addFreshEntity(item);
        item.playerTouch(player);
        h.assertTrue(item.isAlive()&&item.getItem().getCount()==3&&player.getInventory().isEmpty(),"disabled pickup consumed the drop");
        ModAttachments.setData(player,ModAttachments.SKILL_SETTINGS,SkillSettings.DEFAULT);
        item.setPickUpDelay(20);item.playerTouch(player);
        h.assertTrue(item.isAlive()&&player.getInventory().isEmpty(),"enabled pickup bypassed vanilla delay");
        item.setNoPickUpDelay();item.playerTouch(player);
        h.assertTrue(!item.isAlive()&&player.getInventory().countItem(Items.DIAMOND)==3,"enabled pickup failed");
        ModAttachments.setData(player,ModAttachments.SKILL_SETTINGS,SkillSettings.DEFAULT.withPrototypeItemPickup(false));
        state.setPrototypeMode(false);ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
        var normal=new ItemEntity(h.getLevel(),pos.getX(),pos.getY(),pos.getZ(),new ItemStack(Items.EMERALD));
        normal.setNoPickUpDelay();h.getLevel().addFreshEntity(normal);normal.playerTouch(player);
        h.assertTrue(!normal.isAlive()&&player.getInventory().countItem(Items.EMERALD)==1,"normal mode was blocked");
        player.getInventory().clearContent();
        h.succeed();
    }
}
