package com.codex.blacklight.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerRenderScopeTest {
    public static class Patch {
        private final Object player;
        Patch(Object player){this.player=player;}
        public Object getOriginal(){return player;}
    }
    @Test void onlyCurrentPlayerIsProtected() {
        Object player=new Object();var patch=new Patch(player);
        assertFalse(NativePlayerRenderScope.ownsPatch(patch));
        try(var scope=NativePlayerRenderScope.enter(player)) {
            assertTrue(NativePlayerRenderScope.ownsPatch(patch));
            assertFalse(NativePlayerRenderScope.ownsPatch(new Patch(new Object())));
            assertFalse(NativePlayerRenderScope.ownsPatch(new Object()));
        }
        assertFalse(NativePlayerRenderScope.ownsPatch(patch));
    }
    @Test void nestedRenderRestoresOuterOwner() {
        Object a=new Object(),b=new Object();
        try(var outer=NativePlayerRenderScope.enter(a)) {
            try(var inner=NativePlayerRenderScope.enter(b)) {
                assertTrue(NativePlayerRenderScope.ownsPatch(new Patch(b)));
                assertFalse(NativePlayerRenderScope.ownsPatch(new Patch(a)));
            }
            assertTrue(NativePlayerRenderScope.ownsPatch(new Patch(a)));
        }
    }
    @Test void exceptionNeverLeavesGlobalRenderOverride() {
        Object player=new Object();
        assertThrows(IllegalStateException.class,()->{
            try(var scope=NativePlayerRenderScope.enter(player)){throw new IllegalStateException("render failed");}
        });
        assertFalse(NativePlayerRenderScope.ownsPatch(new Patch(player)));
    }
    @Test void otherThreadDoesNotInheritRendering() throws Exception {
        Object player=new Object();
        var result=new java.util.concurrent.atomic.AtomicBoolean(true);
        try(var scope=NativePlayerRenderScope.enter(player)) {
            var thread=new Thread(()->result.set(NativePlayerRenderScope.ownsPatch(new Patch(player))));
            thread.start();thread.join();
            assertFalse(result.get());
        }
    }
}
