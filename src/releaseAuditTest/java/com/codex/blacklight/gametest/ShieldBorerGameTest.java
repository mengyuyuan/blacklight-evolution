package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ShieldBorerGameTest {
    @GameTest(template="wall_test",timeoutTicks=30,batch="shield_borer")
    public static void shieldBreaksWithLootButRespectsProtectionAndStop(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"borer-check"));
        p.setPos(h.absoluteVec(new Vec3(3.5,4,3.5)));p.setYRot(0);p.setOnGround(true);
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.SHIELD);
        state.addEvolutionPoints(1000);h.assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER),"skill purchase");
        p.setData(ModAttachments.UTILITY_POWERS,PowerForm.SHIELD.bit());
        var movement=p.getData(ModAttachments.MOVEMENT_STATE);movement.acceptInput(false,false,1,0,true,false);movement.setCameraYaw(0);
        h.assertTrue(!ShieldBorerSystem.eligible(p),"learned skill defaults to disabled");
        p.setData(ModAttachments.SKILL_SETTINGS,new SkillSettings(false,true));
        h.assertTrue(ShieldBorerSystem.groundDrive(p,true,1),"drilling owns grounded wall approach on both sides");
        p.setOnGround(false);h.assertTrue(!ShieldBorerSystem.groundDrive(p,true,1),"airborne wall traversal remains available");p.setOnGround(true);
        var at=BlockPos.containing(p.position().add(0,0,1));var floor=at.below();
        level.setBlock(floor,Blocks.STONE.defaultBlockState(),3);level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
        var effects=new BulletdiveBreakEffects();
        h.assertTrue(ShieldBorerSystem.breakBlock(p,at,effects),"shield must break the front stone despite mode mining lock");
        h.assertTrue(level.getBlockState(floor).is(Blocks.STONE),"support remains");
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,floor,effects),"cannot target support through API");
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(at).inflate(2)).stream()
                .anyMatch(item->item.getItem().is(Items.COBBLESTONE)),"normal stone loot");
        level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
        Consumer<BlockEvent.BreakEvent> protect=e->{if(e.getPlayer()==p)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BlockEvent.BreakEvent.class,protect);
        try{h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"external protection wins");}
        finally{NeoForge.EVENT_BUS.unregister(protect);}
        movement.acceptInput(false,false,1,0,false,false);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"release sprint stops destruction immediately");
        movement.acceptInput(false,false,1,0,true,false);p.setData(ModAttachments.UTILITY_POWERS,0);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"retract shield stops destruction");
        p.setData(ModAttachments.UTILITY_POWERS,PowerForm.SHIELD.bit());p.setData(ModAttachments.UTILITY_STATE,new UtilityState(0,205,0,0,0));
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"broken shield cannot bore");
        p.setData(ModAttachments.UTILITY_STATE,UtilityState.EMPTY);level.setBlock(at,Blocks.BEDROCK.defaultBlockState(),3);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"bedrock is unbreakable");
        p.setPos(p.position().add(0,.5,0));p.setOnGround(true);
        level.setBlock(at,Blocks.STONE_SLAB.defaultBlockState(),3);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,at,effects),"half-height support is preserved");
        effects.clear();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="shield_borer")
    public static void borerUpgradesAndToggleAreServerAuthoritative(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"borer-upgrade"));
        p.setPos(h.absoluteVec(new Vec3(3.5,4,3.8)));p.setYRot(0);p.setOnGround(true);
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.SHIELD);
        state.addEvolutionPoints(1050);h.assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER),"base purchase");
        p.setData(ModAttachments.UTILITY_POWERS,PowerForm.SHIELD.bit());
        var movement=p.getData(ModAttachments.MOVEMENT_STATE);movement.acceptInput(false,false,1,0,true,false);movement.setCameraYaw(0);
        p.setData(ModAttachments.SKILL_SETTINGS,new SkillSettings(false,true));
        var effects=new BulletdiveBreakEffects();var far=BlockPos.containing(p.position().add(0,0,2.45));
        level.setBlock(far,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,far,effects),"base cannot use highest tier reach");
        h.assertTrue(!state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2),"no skipping upgrades");
        h.assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_1),"boost I purchase");
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,far,effects),"tier II cannot use tier III reach");
        h.assertTrue(state.buyCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2),"boost II purchase");
        h.assertTrue(ShieldBorerSystem.breakBlock(p,far,effects),"highest tier reaches the distant stone");
        var off=new com.codex.blacklight.network.SkillSettingsPayload(com.codex.blacklight.network.SkillSettingsPayload.Setting.SHIELD_BORER_BLOCKS,false);
        p.setData(ModAttachments.SKILL_SETTINGS,off.apply(p.getData(ModAttachments.SKILL_SETTINGS)));
        level.setBlock(far,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!ShieldBorerSystem.breakBlock(p,far,effects),"disable stops break immediately while sprint is still held");
        h.assertTrue(!ShieldBorerSystem.groundDrive(p,true,1),"disable also restores ordinary wall movement prediction");
        h.assertTrue(ShieldBorerSystem.tier(state)==3,"disable preserves progression");
        h.assertTrue(!p.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking(),"toggle does not overwrite bulletdive choice");
        p.setData(ModAttachments.SKILL_SETTINGS,p.getData(ModAttachments.SKILL_SETTINGS).withShieldBorer(true));
        Consumer<BlockEvent.BreakEvent> protect=e->{if(e.getPlayer()==p)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BlockEvent.BreakEvent.class,protect);
        try{h.assertTrue(!ShieldBorerSystem.breakBlock(p,far,effects),"top tier still respects protection");}
        finally{NeoForge.EVENT_BUS.unregister(protect);}
        effects.clear();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="shield_borer")
    public static void prototypeModeAllowsOrdinaryItemAndXpPickup(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"pickup-check"));
        p.setPos(h.absoluteVec(new Vec3(3.5,4,3.5)));
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        var item=new ItemEntity(level,p.getX(),p.getY(),p.getZ(),new ItemStack(Items.DIAMOND,3));
        item.setNoPickUpDelay();level.addFreshEntity(item);item.playerTouch(p);
        h.assertTrue(p.getInventory().countItem(Items.DIAMOND)==3,"mode must allow ordinary item pickup");
        int before=p.totalExperience;p.takeXpDelay=0;
        var xp=new net.minecraft.world.entity.ExperienceOrb(level,p.getX(),p.getY(),p.getZ(),5);level.addFreshEntity(xp);xp.playerTouch(p);
        h.assertTrue(p.totalExperience==before+5,"mode must allow ordinary XP pickup");h.succeed();
    }
}
