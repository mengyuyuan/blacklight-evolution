package com.codex.blacklight.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Victim side of hitstop: the struck body hangs on the contact frame, then the knockback lands. */
@EventBusSubscriber(modid = "blacklight")
public final class HitStop {
    private record Hold(Vec3 velocity, int until) { }
    private static final Map<LivingEntity, Hold> HELD = new WeakHashMap<>();
    private HitStop() { }

    public static void victim(LivingEntity v, int ticks) {
        if (com.codex.blacklight.BlacklightNeoMod.LOGGER.isDebugEnabled()) com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("HitStop victim {} ticks={} alive={} multipart={}", v.getType().getDescriptionId(), ticks, v.isAlive(), CombatTargeting.multipart(v));
        // Player movement is client-predicted; a server freeze only rubber-bands the victim.
        if (ticks <= 0 || v.level().isClientSide || !v.isAlive() || CombatTargeting.multipart(v) || v instanceof net.minecraft.world.entity.player.Player) return;
        var previous = HELD.get(v);
        // A later hit during the hold keeps the newer launch, never stacks the freeze.
        int until = Math.max(previous == null ? 0 : previous.until(), v.tickCount + ticks);
        HELD.put(v, new Hold(v.getDeltaMovement(), Math.min(until, v.tickCount + 8)));
        // The hold owns the whole body from the next entity tick on; it must not erase the
        // contact frame's own impulse here. Zeroing eagerly hid every launch from anything
        // that reads the victim in the same tick (observers, follow-up rules, tests) even
        // though the hold had already stored the exact velocity to restore. The entity-tick
        // handler below still pins the body every held tick, so no motion leaks out.
        v.hurtMarked = true;
    }

    public static boolean held(LivingEntity v) { return HELD.containsKey(v); }
    public static Vec3 launchVelocity(LivingEntity v){var hold=HELD.get(v);return hold==null?v.getDeltaMovement():hold.velocity();}
    public static int remainingTicks(LivingEntity v){var hold=HELD.get(v);return hold==null||!v.isAlive()?0:Math.clamp(hold.until()-v.tickCount,0,8);}

    @SubscribeEvent
    public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity v) || v.level().isClientSide) return;
        var hold = HELD.get(v);
        if (hold == null) return;
        if (v.tickCount >= hold.until() || !v.isAlive() || v.isRemoved()) {
            HELD.remove(v);
            if (v.isAlive() && !v.isRemoved()) { v.setDeltaMovement(hold.velocity()); v.hurtMarked = true; }
            return;
        }
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
    }
}
