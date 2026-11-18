package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SprayEmitterPathTest {
    @Test void earlyDropsRetainTheirWorldPositionAsTheReceiverMovesAndTurns(){
        var forward=new Vec3(1,0,0);
        var emitter=new SprayEmitterPath(Vec3.ZERO,forward,true);
        emitter.sample(1,new Vec3(1,0,0),new Vec3(0,0,1));
        var early=emitter.at(.025);
        assertEquals(.5,early.point().x,1e-8);
        emitter.sample(3,new Vec3(3,0,0),new Vec3(0,1,0));
        assertSame(early,emitter.at(.025));
        assertEquals(3,emitter.at(.15).point().x,1e-8);
        assertEquals(forward,emitter.at(0).axis());
        emitter.sample(4,new Vec3(100,100,100),forward);
        assertTrue(emitter.complete());
        assertEquals(3,emitter.at(.32).point().x,1e-8);
    }
}
