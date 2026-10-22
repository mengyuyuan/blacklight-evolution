package com.codex.blacklight.client.renderer;

import java.lang.reflect.Method;
import java.util.Optional;

/** Reentrant per-thread ownership, limited to the player currently drawn by Blacklight. */
public final class NativePlayerRenderScope implements AutoCloseable {
    private static final ThreadLocal<Object> CURRENT=new ThreadLocal<>();
    private static final ClassValue<Optional<Method>> ORIGINAL=new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            try{return Optional.of(type.getMethod("getOriginal"));}
            catch(NoSuchMethodException e){return Optional.empty();}
        }
    };
    private final Object previous;
    private NativePlayerRenderScope(Object player){previous=CURRENT.get();CURRENT.set(player);}
    public static NativePlayerRenderScope enter(Object player){return new NativePlayerRenderScope(player);}
    public static boolean active(){return CURRENT.get()!=null;}
    public static boolean ownsPatch(Object patch) {
        Object player=CURRENT.get();
        if(player==null||patch==null)return false;
        try {
            var method=ORIGINAL.get(patch.getClass());
            return method.isPresent()&&method.get().invoke(patch)==player;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    @Override public void close(){
        if(previous==null)CURRENT.remove();else CURRENT.set(previous);
    }
}
