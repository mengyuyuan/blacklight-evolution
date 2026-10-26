package com.codex.blacklight.gametest;

import com.codex.blacklight.movement.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TerrainSprintGameTest {
    private static ServerPlayer player(GameTestHelper h) {
        for(int x=2;x<=9;x++)for(int z=2;z<=9;z++) {
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<=7;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        h.setBlock(5,2,3,Blocks.STONE);
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"step-sprint"));
        player.setPos(h.absoluteVec(new Vec3(5.5,3,3.5)));player.setYRot(0);player.setOnGround(true);player.tickCount=100;
        var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);
        player.setData(ModAttachments.PLAYER_STATE,state);player.setHealth(player.getMaxHealth());
        PrototypeMovementSystem.acceptInput(player,false,false,1,0,true,false);
        return player;
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void steppingDownKeepsTheActualServerSprintMotor(GameTestHelper h) {
        var player=player(h);
        MovementTuningSettings.load();
        player.setDeltaMovement(0,0,MovementTuningSettings.sprintMaxSpeed()*.7);
        PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(player));
        double speed=player.getDeltaMovement().horizontalDistance();
        for(int tick=0;tick<5;tick++) {
            player.tickCount++;
            player.setPos(h.absoluteVec(new Vec3(5.5,3-tick*(tick+1)*.04,4.5+tick*.3)));
            player.setOnGround(false);
            // Reproduce vanilla's post-travel drag before the sprint motor runs.
            player.setDeltaMovement(0,-.08*(tick+1),speed*.55);
            h.assertTrue(TerrainTraversal.sprintSupported(player),"one-block descent retains support at step "+tick);
            PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(player));
            h.assertTrue(player.isSprinting(),"short descending step must not clear sprint flag");
            h.assertTrue(player.getDeltaMovement().horizontalDistance()+1e-6>=speed,"short descending step lost sprint speed");
            speed=player.getDeltaMovement().horizontalDistance();
        }
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void terrainSprintDoesNotBecomeAirAccelerationOrLevitation(GameTestHelper h) {
        var player=player(h);player.setOnGround(false);player.tickCount=101;
        var movement=player.getData(ModAttachments.MOVEMENT_STATE);
        movement.rememberSprintDrive(100,new Vec3(0,0,.5));
        player.setPos(h.absoluteVec(new Vec3(5.5,2.6,5.5)));player.setDeltaMovement(0,-.2,.3);
        h.assertTrue(TerrainTraversal.sprintSupported(player),"nearby floor continues an existing sprint");
        player.setDeltaMovement(0,.4,.3);
        h.assertTrue(!TerrainTraversal.sprintSupported(player),"ascending jump must not use terrain drive");
        player.setDeltaMovement(0,-.2,.3);player.setPos(h.absoluteVec(new Vec3(5.5,6,5.5)));
        h.assertTrue(!TerrainTraversal.sprintSupported(player),"cliff fall must not use terrain drive");
        player.setPos(h.absoluteVec(new Vec3(5.5,2.6,5.5)));player.getAbilities().flying=true;
        h.assertTrue(!TerrainTraversal.sprintSupported(player),"creative flight cannot use terrain drive");
        player.getAbilities().flying=false;movement.acceptInput(true,false,1,0,true,false);
        h.assertTrue(!TerrainTraversal.sprintSupported(player),"held jump/glide must not use terrain drive");
        movement.acceptInput(false,false,1,0,true,false);player.tickCount=102;
        h.assertTrue(!TerrainTraversal.sprintSupported(player),"expired sprint drive cannot be revived in midair");
        h.succeed();
    }
}
