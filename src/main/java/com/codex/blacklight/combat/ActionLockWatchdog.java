package com.codex.blacklight.combat;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Last line of defence against input locks that outlive their owner. Every lock
 * (weapon visual, grab/consume flag, devastator, long sessions) is checked each
 * server tick against the system that is supposed to own it; an orphaned or
 * runaway lock is released and synchronized, and the cause is logged.
 */
@EventBusSubscriber(modid = "blacklight")
public final class ActionLockWatchdog {
    /** No ordinary action lasts this long; a charged hold auto-releases far sooner. */
    static final int WEAPON_SESSION_LIMIT = 400;
    static final int CONSUME_LOCK_LIMIT = 260;
    static final int DEVASTATOR_GROUND_LIMIT = 320;
    static final int ORPHAN_GRACE = 3;
    private static final Map<ServerPlayer, int[]> ORPHANS = new WeakHashMap<>();

    private ActionLockWatchdog() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isRemoved()) return;
        int[] orphan = ORPHANS.computeIfAbsent(p, k -> new int[2]);

        // 1. Weapon visual without any owning session: the client keeps clearing input.
        var visual = p.getData(ModAttachments.WEAPON_VISUAL);
        if (visual.spec() != null && !WeaponCombatSystem.active(p) && !DevastatorSystem.active(p)) {
            if (++orphan[0] >= ORPHAN_GRACE) {
                warn(p, "orphaned weapon visual " + visual.spec().id);
                p.setData(ModAttachments.WEAPON_VISUAL, WeaponVisual.EMPTY);
                p.syncData(ModAttachments.WEAPON_VISUAL);
                if (p.getData(ModAttachments.CLAW_COMBAT).action() == ClawCombatState.Action.TRANSFORM)
                    p.getData(ModAttachments.CLAW_COMBAT).reset();
                NativePlayerActionBackend.clear(p);
                orphan[0] = 0;
            }
        } else orphan[0] = 0;

        // 2. Grab/consume flag without a consume session or a held block.
        boolean consuming = p.getData(ModAttachments.CONSUMING);
        int stage = p.getData(ModAttachments.GRAB_STAGE);
        if ((consuming || stage != ConsumeVisual.NONE) && !ConsumeSystem.active(p)
                && BlockThrowSystem.held(p) == null) {
            if (++orphan[1] >= ORPHAN_GRACE) {
                warn(p, "orphaned grab lock consuming=" + consuming + " stage=" + stage);
                BlockThrowSystem.clear(p);
                orphan[1] = 0;
            }
        } else orphan[1] = 0;

        // 3. Owned but runaway sessions.
        int weaponAge = WeaponCombatSystem.sessionAge(p);
        if (weaponAge > WEAPON_SESSION_LIMIT) {
            warn(p, "weapon session exceeded " + WEAPON_SESSION_LIMIT + " ticks");
            WeaponCombatSystem.cancel(p);
        }
        int consumeAge = ConsumeSystem.sessionAge(p);
        if (consumeAge > CONSUME_LOCK_LIMIT && consuming && ConsumeVisual.blocksInput(stage)) {
            warn(p, "consume stage " + stage + " held input for " + consumeAge + " ticks");
            ConsumeSystem.cancel(p);
        }
        int devastatorAge = DevastatorSystem.sessionAge(p);
        if (devastatorAge > DEVASTATOR_GROUND_LIMIT && DevastatorSystem.busy(p) && p.onGround()) {
            warn(p, "devastator kept a grounded body for " + devastatorAge + " ticks");
            DevastatorSystem.cancel(p);
        }
    }

    /** Death never carries an action into the next life or a corpse-held lock. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p)) return;
        releaseAll(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) releaseAll(p);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) releaseAll(p);
    }

    public static void releaseAll(ServerPlayer p) {
        WeaponCombatSystem.cancel(p);
        DevastatorSystem.cancel(p);
        ConsumeSystem.cancel(p);
        var block = BlockThrowSystem.held(p);
        if (block != null) block.drop();
        BlockThrowSystem.clear(p);
        if (p.getData(ModAttachments.WEAPON_VISUAL).spec() != null) {
            p.setData(ModAttachments.WEAPON_VISUAL, WeaponVisual.EMPTY);
            p.syncData(ModAttachments.WEAPON_VISUAL);
        }
        p.getData(ModAttachments.CLAW_COMBAT).reset();
        com.codex.blacklight.movement.PrototypeMovementSystem.suspendForTransport(p);
        ORPHANS.remove(p);
    }

    private static void warn(ServerPlayer p, String reason) {
        BlacklightNeoMod.LOGGER.warn("Action lock watchdog released {}: {}", p.getGameProfile().getName(), reason);
    }
}
