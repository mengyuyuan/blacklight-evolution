package com.codex.blacklight.client.renderer;

import net.minecraft.world.entity.Entity;

/** Nested renders may reuse one model. Only the current actor may emit its tissue. */
public final class NativeRenderScope implements AutoCloseable {
    private static final ThreadLocal<Entity> CURRENT=new ThreadLocal<>();
    private final Entity previous;
    private NativeRenderScope(Entity actor){previous=CURRENT.get();CURRENT.set(actor);}
    public static NativeRenderScope enter(Entity actor){return new NativeRenderScope(actor);}
    public static boolean owns(Entity proxy,Entity host){var actor=CURRENT.get();return actor==null||actor==proxy||actor==host;}
    public static Entity current(){return CURRENT.get();}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
