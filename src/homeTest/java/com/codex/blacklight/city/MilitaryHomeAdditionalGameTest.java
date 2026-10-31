package com.codex.blacklight.city;

import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryHomeAdditionalGameTest {
    @GameTest(template="blackwatch_arena",batch="homePersistence5",timeoutTicks=180)
    public static void aFarAwayPlayerCannotDespawnAnIndependentSoldierBeforeHomeInitialization(GameTestHelper h){
        var f=new MilitaryHomeGameTest.Fixture(h,72000,false,false);var spawn=new BlockPos(72032,81,32);var playerPosition=spawn.offset(200,0,0);f.fieldFloor(spawn,15);f.fieldFloor(playerPosition,8);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.isPositionEntityTicking(spawn)&&f.level.isPositionEntityTicking(playerPosition),"both real player and distant soldier regions must tick"))
            .thenExecute(()->{
                var player=FakePlayerFactory.get(f.level,new GameProfile(UUID.randomUUID(),"home-despawn"));player.setGameMode(GameType.CREATIVE);player.setPos(Vec3.atBottomCenterOf(playerPosition));f.level.addNewPlayer(player);f.entities.add(player);
                h.assertTrue(f.level.players().contains(player),"the distant observer is an actual player in this server level");
                f.primary=f.soldier(false,spawn);h.assertTrue(f.primary.isPersistenceRequired(),"despawn protection must apply immediately on join, before the twenty-tick identity delay");
                h.assertTrue(!f.primary.getPersistentData().contains(MilitaryHomeSystem.FIELD_ANCHOR_TAG),"immediate persistence must not steal delayed native ownership initialization");
            }).thenIdle(5).thenExecute(()->h.assertTrue(f.primary.isAlive()&&!f.primary.isRemoved(),"the real Monster survives its first ticks despite the only player being over 128 blocks away"))
            .thenIdle(40).thenExecute(()->{
                h.assertTrue(f.primary.isAlive()&&f.primary.getPersistentData().contains(MilitaryHomeSystem.FIELD_ANCHOR_TAG),"ordinary later initialization still creates the persistent field assignment");f.close();
            }).thenSucceed();
    }
    @GameTest(template="blackwatch_arena",batch="homePersistence5",timeoutTicks=180)
    public static void aSavedFieldAnchorDoesNotFollowAnOutOfBoundsSoldiersCombatCenter(GameTestHelper h){
        var f=new MilitaryHomeGameTest.Fixture(h,74000,false,false);var anchor=new BlockPos(74032,81,32);var displaced=anchor.offset(80,0,0);f.fieldFloor(displaced,10);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.isPositionEntityTicking(displaced),"out-of-bounds saved entity region must actually tick"))
            .thenExecute(()->{
                // Arrange an entity loaded away from an already saved field anchor. This is not an arrival/movement proof.
                f.primary=f.soldier(false,displaced);f.primary.setNoAi(true);var tag=f.primary.getPersistentData();
                tag.putString(MilitaryHomeSystem.MODE_TAG,"field");tag.putLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG,anchor.asLong());tag.putString(MilitaryHomeSystem.FIELD_DIMENSION_TAG,f.level.dimension().location().toString());
            }).thenIdle(45).thenExecute(()->{
                h.assertTrue(f.primary.getPersistentData().getLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG)==anchor.asLong(),"loading outside the patrol does not shift its persisted center");
                h.assertTrue(!BlackwatchSquadSystem.withinTerritory(f.primary,f.primary.position()),"field combat remains outside its saved forty-eight-block territory until the soldier returns");
                h.assertTrue(!BlackwatchSquadSystem.withinTerritory(f.primary,f.primary.position().add(8,0,0)),"the target boundary cannot slide forward with a distant displaced soldier");f.close();
            }).thenSucceed();
    }
    private MilitaryHomeAdditionalGameTest(){}
}
