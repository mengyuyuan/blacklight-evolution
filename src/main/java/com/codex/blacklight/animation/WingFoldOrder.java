package com.codex.blacklight.animation;

import java.util.function.IntSupplier;

/** Choose once on the server when flight ends; render passes never reroll. */
public final class WingFoldOrder {
    public static int onTransition(int current, boolean wasFlying, boolean flying, IntSupplier choose) {
        return wasFlying && !flying ? (choose.getAsInt() & 1) : (current & 1);
    }
    private WingFoldOrder() {}
}
