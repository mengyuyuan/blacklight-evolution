package com.codex.blacklight.world;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GazeRewardGameTest {
    @GameTest(template="blackwatch_arena",batch="den_party_route",timeoutTicks=280)
    public static void creditedPlayerWithoutTissueCanStartDenAndOldVictoryCanReplay(GameTestHelper h){
        var city=h.getLevel().getServer().getLevel(com.codex.blacklight.city.CityTravel.WORLD);
        var site=GreeneHiveCave.site(city.getSeed(),1);
        var chunk=new net.minecraft.world.level.ChunkPos(site);
        boolean ticket=city.setChunkForced(chunk.x,chunk.z,true);
        city.getChunk(chunk.x,chunk.z);
        var profile=new GameProfile(UUID.randomUUID(),"DenSurvivor");
        var player=new ServerPlayer(city.getServer(),city,profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=FakePlayerFactory.get(city,profile).connection;
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(site));
        player.tickCount=20;
        var sites=BossSiteData.get(city);
        var tick=new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player);
        h.startSequence().thenWaitUntil(()->h.assertTrue(city.isPositionEntityTicking(site),"den is entity ticking")).thenExecute(()->{
            SurvivalGuide.outbreak(tick);
            h.assertTrue(!sites.occupied(site),"arriving without pump credit cannot start the encounter");
            com.codex.blacklight.power.GrowthSystem.award(player,"plaza_cleared");
            h.assertTrue(player.getInventory().isEmpty(),"credited participant carries no items");
        }).thenWaitUntil(()->{
            SurvivalGuide.outbreak(tick);
            h.assertTrue(sites.occupied(site),"a participant who lost the sample can start the spider");
        }).thenExecute(()->{
            for(var mob:city.getEntitiesOfClass(com.codex.blacklight.entity.EncounterMob.class,new net.minecraft.world.phys.AABB(site).inflate(20)))mob.discard();
            com.codex.blacklight.power.GrowthSystem.award(player,"greene_outbreak");
            h.assertTrue(!SurvivalGuide.followingGreene(player),"existing victory stays quiet by default");
            player.setShiftKeyDown(true);
            var tissue=new net.minecraft.world.item.ItemStack(com.codex.blacklight.registry.ModItems.GREENE_TISSUE.get());
            tissue.finishUsingItem(city,player);
            h.assertTrue(SurvivalGuide.followingGreene(player),"listening at the den opts an unverified old victory into a replay");
            SurvivalGuide.outbreak(tick);
            h.assertTrue(sites.occupied(site),"replay starts the underground fight");
            for(var mob:city.getEntitiesOfClass(com.codex.blacklight.entity.EncounterMob.class,new net.minecraft.world.phys.AABB(site).inflate(20)))mob.discard();
            if(ticket)city.setChunkForced(chunk.x,chunk.z,false);
        }).thenSucceed();
    }
    @GameTest(template="blackwatch_arena",batch="pump_orphan",timeoutTicks=80)
    public static void restoredEscapeRecoversItsStationAndCreditsTheOfflineFinisher(GameTestHelper h){
        var level=h.getLevel();
        var station=h.absolutePos(new BlockPos(5,3,5));
        var host=com.codex.blacklight.registry.EncounterEntities.GREENE_HOST.get().create(level);
        host.moveTo(station.getX()+.5,station.getY(),station.getZ()+.5,0,0);
        var profile=new GameProfile(UUID.randomUUID(),"PumpRecovery");
        var player=new ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=FakePlayerFactory.get(level,profile).connection;
        var sites=BossSiteData.get(level);
        sites.reserve(station,host,player);
        var old=host.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        old.putLong("Station",station.asLong());
        old.putInt("PlazaStage",GreeneHost.ESCAPING);
        old.putInt("Elapsed",GreeneHost.ESCAPE_END-1);
        sites.release(host);
        host.load(old);
        level.addFreshEntity(host);
        host.tick();
        sites.deliver(player);
        h.assertTrue(host.isRemoved() && !sites.occupied(station) && SurvivalGuide.done(player,"plaza_cleared"),"saved escape repairs a missing reservation and pays the original participant");
        h.assertTrue(!SurvivalGuide.done(player,"greene_outbreak") && !BossSiteData.headConfirmed(player),"plaza recovery cannot issue the head reward");
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="gaze_reward",timeoutTicks=80)
    public static void queuedWinSurvivesReloadWithoutPayingTwice(GameTestHelper h){
        var level=h.getLevel();
        var profile=new GameProfile(UUID.randomUUID(),"HeadClaim");
        var player=new ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=FakePlayerFactory.get(level,profile).connection;
        var state=player.getData(ModAttachments.PLAYER_STATE);
        h.assertTrue(!state.hasUnlocked(PowerForm.GAZE_VACUOLE),"locked before underground victory");
        var city=level.getServer().getLevel(com.codex.blacklight.city.CityTravel.WORLD);
        var site=GreeneHiveCave.site(city.getSeed(),0);
        var data=BossSiteData.get(level.getServer().overworld());
        var sites=BossSiteData.get(city);
        var type=com.codex.blacklight.registry.EncounterEntities.CREATURES.get(com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE).get();
        var boss=type.create(city);
        boss.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(site));
        boss.startGreeneOutbreak();
        sites.reserve(site,boss,player);
        BossSiteData.defeated(boss,player);
        data.deliver(player);
        h.assertTrue(sites.occupied(site) && !SurvivalGuide.done(player,"greene_outbreak"),"a spider death cannot certify the goddess victory");
        var wave=boss.greeneBattle().save();
        wave.putInt("ArmourWave",2);
        wave.putInt("WaveCount",2);
        wave.putBoolean("OpeningPending",false);
        boss.greeneBattle().load(wave);
        var goddess=boss.goddess.save();
        goddess.putBoolean("Initialized",true);
        goddess.putInt("Removed",3);
        boss.goddess.load(goddess);
        BossSiteData.defeated(boss,player);
        data.deliver(player);
        h.assertTrue(sites.occupied(site) && !state.hasUnlocked(PowerForm.GAZE_VACUOLE),"both arms without the head still pay nothing");
        goddess.putInt("Removed",7);
        goddess.putInt("Before",3);
        goddess.putInt("Part",com.codex.blacklight.combat.GoddessVitals.HEAD);
        goddess.putInt("Action",com.codex.blacklight.combat.GoddessEncounter.Action.EXTRACTING.ordinal());
        goddess.putBoolean("Committed",true);
        goddess.putInt("Age",138);
        boss.goddess.load(goddess);
        BossSiteData.defeated(boss,player);
        data.deliver(player);
        h.assertTrue(sites.occupied(site) && !state.hasUnlocked(PowerForm.GAZE_VACUOLE),"severing the head has not yet finished the extraction");
        boss.goddess.tick();
        h.assertTrue(!sites.occupied(site),"resuming the final head extraction releases the underground site");
        h.assertTrue(!state.hasUnlocked(PowerForm.GAZE_VACUOLE),"offline result waits for participant");
        var restored=BossSiteData.load(data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
        restored.deliver(player);
        h.assertTrue(state.hasUnlocked(PowerForm.GAZE_VACUOLE) && SurvivalGuide.done(player,"greene_outbreak") && BossSiteData.headConfirmed(player),"reloaded claim awards inheritance and underground advancement");
        int xp=player.totalExperience;
        data.deliver(player);
        h.assertTrue(xp==350 && player.totalExperience==xp,"repeated delivery cannot grant additional XP");
        var oldSave=new com.codex.blacklight.state.BlacklightState();
        player.setData(ModAttachments.PLAYER_STATE,oldSave);
        SurvivalGuide.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        h.assertTrue(player.getData(ModAttachments.PLAYER_STATE).hasUnlocked(PowerForm.GAZE_VACUOLE) && player.totalExperience==xp,"old victory without unlock is migrated on login");
        oldSave.cure();oldSave.infect();
        h.assertTrue(oldSave.hasUnlocked(PowerForm.GAZE_VACUOLE),"memory survives cure and reinfection");
        System.out.println("PLAYER_GAZE_REWARD xp="+xp+" offline_replay=true old_save=true retained=true");
        boss.discard();h.succeed();
    }
}
