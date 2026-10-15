package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/** Store every age tick for saving, but broadcast age-only changes every ten ticks. */
public final class BiomassSync {
    private static final ThreadLocal<Object> AGE_ONLY = new ThreadLocal<>();
    private BiomassSync() {}

    public static boolean shouldSync(Object holder) { return AGE_ONLY.get() != holder; }

    public static void storeAge(ServerPlayer p, BiomassState value) {
        if (value.equals(BiomassSystem.state(p))) return;
        Object previous = AGE_ONLY.get();
        // Only this synchronous store is muted. Phase changes and tracking snapshots stay immediate.
        if (value.age() % 10 != 0) AGE_ONLY.set(p);
        try {
            ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,value);
        } finally {
            if (previous == null) AGE_ONLY.remove(); else AGE_ONLY.set(previous);
        }
    }
}
