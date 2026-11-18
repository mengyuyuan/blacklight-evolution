package com.codex.blacklight.combat;

import net.minecraft.world.entity.Entity;

/** Only the render thread supplies a fractional picking clock; dedicated server stays at its tick. */
public final class HydraPicking {
    private static final ThreadLocal<Float> PARTIAL=ThreadLocal.withInitial(()->0F);
    public static void begin(float partial){PARTIAL.set(partial);}
    public static void end(){PARTIAL.remove();}
    public static float partial(Entity e){return e.level().isClientSide?PARTIAL.get():0;}
    private HydraPicking(){}
}
