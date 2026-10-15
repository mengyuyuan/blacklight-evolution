package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedLoadingCacheTest {
    @Test void unrelatedLoadsOverlapAndSameKeyLoadsOnce() throws Exception {
        var cache=new BoundedLoadingCache<Integer,Integer>(()->2);
        var pool=Executors.newFixedThreadPool(3);
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        java.util.function.Function<Integer,Integer> loader=k->{
            calls.incrementAndGet();entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("serialized loads");}
            catch(InterruptedException e){throw new RuntimeException(e);}return k;
        };
        try{
            var a=pool.submit(()->cache.get(1,loader));var b=pool.submit(()->cache.get(2,loader));
            var duplicate=pool.submit(()->cache.get(1,loader));
            assertTrue(entered.await(3,TimeUnit.SECONDS),"independent tile loads serialized");release.countDown();
            assertEquals(1,a.get(3,TimeUnit.SECONDS));assertEquals(2,b.get(3,TimeUnit.SECONDS));
            assertEquals(1,duplicate.get(3,TimeUnit.SECONDS));assertEquals(2,calls.get());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void leastRecentlyUsedEvictedAndBudgetCanShrink(){
        var capacity=new AtomicInteger(3);var cache=new BoundedLoadingCache<Integer,Integer>(capacity::get);
        var calls=new AtomicInteger();java.util.function.Function<Integer,Integer> load=k->{calls.incrementAndGet();return k;};
        cache.get(1,load);cache.get(2,load);cache.get(3,load);cache.get(1,load);cache.get(4,load);
        assertEquals(3,cache.size());cache.get(1,load);assertEquals(4,calls.get());
        cache.get(2,load);assertEquals(5,calls.get());
        capacity.set(1);cache.get(2,load);assertEquals(1,cache.size());
    }
    @Test void failedLoadDoesNotPoisonRetries(){
        var cache=new BoundedLoadingCache<Integer,Integer>(()->1);
        assertThrows(CompletionException.class,()->cache.get(1,k->{throw new IllegalStateException("bad tile");}));
        assertEquals(0,cache.size());assertEquals(7,cache.get(1,k->7));
    }
}
