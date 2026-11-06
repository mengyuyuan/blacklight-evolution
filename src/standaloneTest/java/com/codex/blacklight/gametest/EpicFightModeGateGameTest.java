package com.codex.blacklight.gametest;

import com.codex.blacklight.compat.EpicFightBlacklightBridge;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * CI has no Epic Fight. Post a stand-in with the same shape as its BattleModeSustainableEvent
 * (getPlayerPatch().getOriginal()) through the real Forge bus to the production Prototype gate.
 */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EpicFightModeGateGameTest {
    private static boolean registered;

    public static final class StandInPatch {
        private final Entity original;
        public StandInPatch(Entity original) { this.original = original; }
        public final Entity getOriginal() { return original; }
    }

    @Cancelable
    public static final class StandInBattleModeEvent extends Event {
        private final StandInPatch patch;
        public StandInBattleModeEvent() { this(null); }
        public StandInBattleModeEvent(StandInPatch patch) { this.patch = patch; }
        public StandInPatch getPlayerPatch() { return patch; }
    }

    private static boolean suspended(Entity entity) {
        var event = new StandInBattleModeEvent(entity == null ? null : new StandInPatch(entity));
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    @GameTest(template = "wall_test", timeoutTicks = 20)
    public static void prototypeFormAloneCancelsEpicFightBattleMode(GameTestHelper h) {
        if (!registered) {
            h.assertTrue(EpicFightBlacklightBridge.register(MinecraftForge.EVENT_BUS, StandInBattleModeEvent.class.getName()),
                    "bridge resolves the battle-mode event shape");
            registered = true;
        }
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "EpicFightGateQA"));
        p.moveTo(h.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        var state = ModAttachments.getData(p, ModAttachments.PLAYER_STATE);

        h.assertTrue(!suspended(p), "an uninfected player keeps Epic Fight mode");
        state.infect();
        h.assertTrue(!suspended(p), "an infected player in human form keeps Epic Fight mode");
        h.assertTrue(state.setPrototypeMode(true), "enter Prototype form");
        h.assertTrue(suspended(p), "Prototype form cancels Epic Fight battle mode");
        h.assertTrue(suspended(p), "Prototype form keeps cancelling on later ticks");
        state.setPrototypeMode(false);
        h.assertTrue(!suspended(p), "leaving Prototype form lets Epic Fight restore its mode");

        h.assertTrue(!suspended(h.spawn(EntityType.ZOMBIE, 4, 2, 4)), "non-player patches are ignored");
        h.assertTrue(!suspended(null), "a missing patch is ignored");
        h.succeed();
    }
}