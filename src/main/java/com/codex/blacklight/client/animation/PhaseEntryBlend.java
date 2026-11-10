package com.codex.blacklight.client.animation;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/** Enter blends counted from the tick this client first saw a phase, not the server start tick.
 * A remote viewer whose packet arrives late would otherwise find the blend already complete
 * and snap from its previous pose to the scene in one frame. */
public final class PhaseEntryBlend {
    public static final PhaseEntryBlend CONSUME_ACTOR=new PhaseEntryBlend(),CONSUME_GRIP=new PhaseEntryBlend(),RECEIVER=new PhaseEntryBlend();
    private final Map<Entity,long[]> seen=new WeakHashMap<>();
    /** Pure form for tests: weight after seeing a phase at {@code seenTick}. */
    public static float weight(long seenTick,long now,float partial,float ticks){
        if(ticks<=0)return 1;
        return Math.max(0,Math.min(1,(now-seenTick+partial)/ticks));
    }
    public synchronized float weight(Entity entity,long started,int stage,float partial,float ticks){
        long phase=started*16+stage,now=entity.level().getGameTime();
        long[] entry=seen.get(entity);
        // Never start before the server did; a client that sees it on time blends exactly as before.
        if(entry==null||entry[0]!=phase){entry=new long[]{phase,Math.max(now,started)};seen.put(entity,entry);}
        return weight(entry[1],now,partial,ticks);
    }
}