package com.codex.blacklight.city;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** Loads unrelated keys outside the cache lock; concurrent callers share one load per key. */
final class BoundedLoadingCache<K,V> {
    private final LinkedHashMap<K,V> ready=new LinkedHashMap<>(16,.75f,true);
    private final java.util.HashMap<K,CompletableFuture<V>> loading=new java.util.HashMap<>();
    private final IntSupplier capacity;
    BoundedLoadingCache(IntSupplier capacity){this.capacity=capacity;}
    V get(K key,Function<K,V> loader){
        CompletableFuture<V> future;boolean owner=false;
        synchronized(this){
            trim();
            V value=ready.get(key);if(value!=null)return value;
            future=loading.get(key);
            if(future==null){future=new CompletableFuture<>();loading.put(key,future);owner=true;}
        }
        if(owner){
            try{
                V value=java.util.Objects.requireNonNull(loader.apply(key));
                synchronized(this){ready.put(key,value);trim();loading.remove(key);}
                future.complete(value);
            }catch(Throwable failure){
                synchronized(this){loading.remove(key);}
                future.completeExceptionally(failure);
            }
        }
        return future.join();
    }
    private void trim(){
        int limit=Math.max(1,capacity.getAsInt());
        while(ready.size()>limit){var it=ready.keySet().iterator();it.next();it.remove();}
    }
    synchronized int size(){return ready.size();}
}
