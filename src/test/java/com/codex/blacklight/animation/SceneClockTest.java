package com.codex.blacklight.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class SceneClockTest {
    @Test void backwardSyncDuringASceneIsIgnoredAndTheClockKeepsRunning() {
        var clock = new SceneClock();
        long t = clock.resolve(100, 89, true);
        assertEquals(100, t);
        assertEquals(11, clock.ahead());
        for (int i = 0; i < 5; i++) {
            long next = clock.resolve(t, t + 1, true);
            assertEquals(t + 1, next, "never pauses: a paused tick saw-tooths with the partial tick");
            t = next;
        }
    }
    @Test void outsideASceneTimeIsUntouched() {
        var clock = new SceneClock();
        assertEquals(89, clock.resolve(100, 89, false));
        assertEquals(90, clock.resolve(89, 90, false));
    }
    @Test void largeResyncAndForwardCorrectionsApply() {
        var clock = new SceneClock();
        assertEquals(10, clock.resolve(5000, 10, true));
        clock.resolve(100, 95, true);
        assertEquals(130, clock.resolve(100, 130, true));
        assertEquals(0, clock.ahead());
    }
    @Test void serverTimeReturnsAfterTheScene() {
        var clock = new SceneClock();
        assertEquals(100, clock.resolve(100, 90, true));
        assertEquals(91, clock.resolve(101, 91, false));
    }
}
