package com.codex.blacklight.client.renderer;

import java.util.*;
import java.util.function.*;

/** A wide wing or decorative child must not become the parent of its own torso. */
final class RagdollRootSelection {
    private RagdollRootSelection() {}

    static <T> T pick(List<T> parts,Function<T,Object> key,Function<Object,Object> parent,
                      ToIntFunction<T> region,ToDoubleFunction<T> size){
        if(parts.isEmpty())throw new IllegalArgumentException("Empty body");
        var keys=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var part:parts)keys.add(key.apply(part));
        var roots=new ArrayList<T>();
        for(var part:parts){
            var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            Object at=parent.apply(key.apply(part));boolean descendant=false;
            while(at!=null&&seen.add(at)){
                if(keys.contains(at)){descendant=true;break;}
                at=parent.apply(at);
            }
            if(!descendant)roots.add(part);
        }
        // Forest-shaped humanoid models have sibling head/body/limb meshes.
        // Prefer the trunk there; malformed cyclic models retain a finite fallback.
        var candidates=roots.isEmpty()?parts:roots;
        return candidates.stream().max(Comparator.<T>comparingInt(p->region.applyAsInt(p)==32?1:0)
                .thenComparingDouble(size)).orElseThrow();
    }
}
