package com.codex.blacklight.client.animation;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;

/** Read-only discovery for third-party models exposing parts as fields instead of a root method. */
public final class ReflectedBoneRoots {
    private static final int MAX_FIELDS=256,MAX_NODES=4096;
    private ReflectedBoneRoots() {}

    public static <T> Map<String,T> find(Object model,Class<T> boneType,Function<T,Collection<T>> children) {
        Map<String,T> named=new TreeMap<>();Set<T> referenced=Collections.newSetFromMap(new IdentityHashMap<>());
        int fields=0,depth=0;
        for(Class<?> type=model.getClass();type!=null&&type!=Object.class&&depth++<16;type=type.getSuperclass()){
            var declared=type.getDeclaredFields();Arrays.sort(declared,Comparator.comparing(java.lang.reflect.Field::getName));
            for(var field:declared){
                if(Modifier.isStatic(field.getModifiers())||field.isSynthetic()||!boneType.isAssignableFrom(field.getType()))continue;
                if(++fields>MAX_FIELDS)return Map.of();
                try{
                    if(!field.trySetAccessible())continue;T bone=boneType.cast(field.get(model));
                    if(bone==null||!referenced.add(bone))continue;
                    String name=field.getName();int suffix=2;while(named.containsKey(name))name=field.getName()+"_"+suffix++;
                    named.put(name,bone);
                }catch(IllegalAccessException|RuntimeException ignored){/* Unsupported fields retain their original renderer. */}
            }
        }
        Set<T> visited=Collections.newSetFromMap(new IdentityHashMap<>()),parented=Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<T> pending=new ArrayDeque<>(named.values());
        while(!pending.isEmpty()){
            T bone=pending.removeFirst();if(!visited.add(bone))continue;if(visited.size()>MAX_NODES)return Map.of();
            for(T child:children.apply(bone))if(child!=null){parented.add(child);pending.addLast(child);}
        }
        named.values().removeIf(parented::contains);
        return Collections.unmodifiableMap(named);
    }
}
