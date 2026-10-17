package com.codex.blacklight.combat;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Owner-scoped carry lookup shared by client/server; neither side retains a departed world. */
final class HeldObjectIndex<O,T> {
    private final WeakHashMap<O,WeakReference<T>> held = new WeakHashMap<>();
    synchronized void remember(O owner,T object){held.put(owner,new WeakReference<>(object));}
    synchronized T get(O owner,Predicate<T> valid){
        var reference=held.get(owner);
        var object=reference==null?null:reference.get();
        if(object!=null&&valid.test(object))return object;
        held.remove(owner);
        return null;
    }
}
