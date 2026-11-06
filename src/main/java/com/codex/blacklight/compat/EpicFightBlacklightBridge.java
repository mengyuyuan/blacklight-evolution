package com.codex.blacklight.compat;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.city.HydraTransport;
import com.codex.blacklight.registry.ModAttachments;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Reflection-only boundary for optional Epic Fight integration.
 *
 * Epic Fight 1.20.1 posts a cancelable {@code BattleModeSustainableEvent} on the Forge bus
 * every player tick; cancelling it switches that player to vanilla mode, and the next
 * uncancelled tick restores Epic Fight mode. Blacklight cancels it only while the player
 * is in its active Prototype form. No Epic Fight class is linked into Blacklight.
 */
public final class EpicFightBlacklightBridge {
    static final String BATTLE_MODE_EVENT = "yesman.epicfight.api.forgeevent.BattleModeSustainableEvent";
    private static boolean warned;

    private EpicFightBlacklightBridge() {}

    /** Registers the Prototype-form gate; returns false when the installed Epic Fight lacks the event. */
    public static boolean register(IEventBus bus) {
        return register(bus, BATTLE_MODE_EVENT);
    }

    /** Registers the gate for any cancelable event with BattleModeSustainableEvent's accessor shape. */
    public static boolean register(IEventBus bus, String eventName) {
        final Class<? extends Event> type;
        final MethodHandle patch, original;
        try {
            type = Class.forName(eventName, false, EpicFightBlacklightBridge.class.getClassLoader()).asSubclass(Event.class);
            MethodHandle playerPatch = MethodHandles.publicLookup().findVirtual(type, "getPlayerPatch",
                    MethodType.methodType(type.getMethod("getPlayerPatch").getReturnType()));
            patch = playerPatch.asType(MethodType.methodType(Object.class, Event.class));
            original = originalAccessor(playerPatch.type().returnType());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            BlacklightNeoMod.LOGGER.warn("Epic Fight battle-mode event unavailable; Prototype form keeps Epic Fight mode", error);
            return false;
        }
        listen(bus, type, new Gate(patch, original));
        return true;
    }

    private static <T extends Event> void listen(IEventBus bus, Class<T> type, Gate gate) {
        // Run after every other listener, and leave events another mod already cancelled alone.
        bus.addListener(EventPriority.LOWEST, false, type, gate::accept);
    }

    /**
     * EntityPatch also declares client-only signatures such as isOutlineVisible(LocalPlayer).
     * A dedicated server cannot list those methods, so resolve only the erased getOriginal().
     */
    static MethodHandle originalAccessor(Class<?> patchType) throws ReflectiveOperationException {
        return MethodHandles.publicLookup()
                .findVirtual(patchType, "getOriginal", MethodType.methodType(Entity.class))
                .asType(MethodType.methodType(Entity.class, Object.class));
    }

    /** Blacklight's Prototype form owns combat input; Hydra transport keeps its own animation. */
    public static boolean suspendsEpicFightMode(Player player) {
        var state = ModAttachments.peekData(player, ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode() && !HydraTransport.active(player);
    }

    record Gate(MethodHandle patch, MethodHandle original) {
        void accept(Event event) {
            if (!event.isCancelable() || event.isCanceled()) return;
            try {
                Object playerPatch = (Object) patch.invokeExact(event);
                Entity entity = playerPatch == null ? null : (Entity) original.invokeExact(playerPatch);
                if (entity instanceof Player player && suspendsEpicFightMode(player)) event.setCanceled(true);
            } catch (Throwable error) {
                if (!warned) {
                    warned = true;
                    BlacklightNeoMod.LOGGER.warn("Cannot read Epic Fight battle-mode event; leaving Epic Fight mode unchanged", error);
                }
            }
        }
    }
}