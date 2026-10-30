package com.codex.blacklight.client.animation;

import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import java.util.*;

/** Synthetic parent only: original part identities, bind poses and child maps remain untouched. */
final class NativeModelRoots {
    private static final Map<EntityModel<?>,Optional<ModelPart>> CACHE=new WeakHashMap<>();
    private NativeModelRoots() {}
    static ModelPart root(EntityModel<?> model){
        return CACHE.computeIfAbsent(model,m->{
            var roots=ReflectedBoneRoots.find(m,ModelPart.class,p->((ModelPartChildrenAccessor)(Object)p).blacklight$children().values());
            return roots.isEmpty()?Optional.empty():Optional.of(new ModelPart(List.of(),roots));
        }).orElse(null);
    }
    static void clear(){CACHE.clear();}
}
