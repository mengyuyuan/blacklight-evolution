package com.codex.blacklight.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

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
        if (ticks <= 0 || v.level().isClientSide || !v.isAlive() || CombatTargeting.multipart(v)) return;
        var previous = HELD.get(v);
        // A later hit during the hold keeps the newer launch, never stacks the freeze.
        int until = Math.max(previous == null ? 0 : previous.until(), v.tickCount + ticks);
        HELD.put(v, new Hold(v.getDeltaMovement(), Math.min(until, v.tickCount + 8)));
        v.setDeltaMovement(Vec3.ZERO);
        v.hurtMarked = true;
    }

    public static boolean held(LivingEntity v) { return HELD.containsKey(v); }

    @SubscribeEvent
    public static void tick(ForgeEntityTickEvent.Pre event) {
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
