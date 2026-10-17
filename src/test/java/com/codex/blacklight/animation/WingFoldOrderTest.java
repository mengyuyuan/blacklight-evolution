package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class WingFoldOrderTest {
    @Test void onlyStartingAFoldConsumesARandomChoice(){
        var draws=new AtomicInteger();
        java.util.function.IntSupplier choose=()->{draws.incrementAndGet();return 1;};
        int order=WingFoldOrder.onTransition(0,true,false,choose);
        assertEquals(1,order);
        for(int i=0;i<1000;i++)order=WingFoldOrder.onTransition(order,false,false,choose);
        order=WingFoldOrder.onTransition(order,false,true,choose);
        order=WingFoldOrder.onTransition(order,true,true,choose);
        assertEquals(1,order);assertEquals(1,draws.get());
    }
    @Test void nextFlightMayCloseWithTheOppositeWingOnTop(){
        assertEquals(0,WingFoldOrder.onTransition(1,true,false,()->0));
        assertEquals(1,WingFoldOrder.onTransition(0,true,false,()->1));
    }
}
