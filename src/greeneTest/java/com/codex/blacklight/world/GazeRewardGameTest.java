package com.codex.blacklight.world;

import com.codex.blacklight.combat.PlayerGazeField;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.util.FakePlayerFactory;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GazeRewardGameTest {
    @GameTest(templateNamespace="blacklight",template="blackwatch_arena",batch="gaze_reward",timeoutTicks=80)
    public static void queuedWinSurvivesReloadWithoutPayingTwice(GameTestHelper h){
        var level=h.getLevel();
        var profile=new GameProfile(UUID.randomUUID(),"HeadClaim");
        var player=new ServerPlayer(level.getServer(),level,profile);
        player.connection=FakePlayerFactory.get(level,profile).connection;
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        h.assertTrue(!state.hasUnlocked(PowerForm.GAZE_VACUOLE),"locked before underground victory");
        var boss=EntityType.HUSK.create(level);var data=BossSiteData.get(level);
        var site=h.absolutePos(new BlockPos(8,5,8));data.reserve(site,boss,player);
        BossSiteData.complete(boss,player,4);
        h.assertTrue(!state.hasUnlocked(PowerForm.GAZE_VACUOLE),"offline result waits for participant");
        var restored=BossSiteData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        restored.deliver(player);
        h.assertTrue(state.hasUnlocked(PowerForm.GAZE_VACUOLE) && SurvivalGuide.done(player,"greene_outbreak"),"reloaded claim awards inheritance and underground advancement");
        int xp=player.totalExperience;
        data.deliver(player);
        h.assertTrue(xp==350 && player.totalExperience==xp,"repeated delivery cannot grant additional XP");
        var oldSave=new com.codex.blacklight.state.BlacklightState();
        ModAttachments.setData(player,ModAttachments.PLAYER_STATE,oldSave);
        SurvivalGuide.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        h.assertTrue(ModAttachments.getData(player,ModAttachments.PLAYER_STATE).hasUnlocked(PowerForm.GAZE_VACUOLE) && player.totalExperience==xp,"old victory without unlock is migrated on login");
        oldSave.cure();oldSave.infect();
        h.assertTrue(oldSave.hasUnlocked(PowerForm.GAZE_VACUOLE),"memory survives cure and reinfection");
        System.out.println("PLAYER_GAZE_REWARD xp="+xp+" offline_replay=true old_save=true retained=true");
        boss.discard();h.succeed();
    }
}
