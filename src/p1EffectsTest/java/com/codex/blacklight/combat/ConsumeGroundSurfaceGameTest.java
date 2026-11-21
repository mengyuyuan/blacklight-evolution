package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ConsumeGroundSurfaceGameTest {
    @GameTest(template="wall_test", timeoutTicks=160)
    public static void groundPressureFollowsRealSupports(GameTestHelper h){
        var base=h.absolutePos(new BlockPos(1,118,1));
        GroundCases.setup(h.getLevel(),base);
        var results=GroundCases.verify(h.getLevel(),base);
        h.assertTrue(results.size()==12,"checked every contact setup");
        h.succeed();
    }
}
