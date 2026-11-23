package com.codex.blacklight.combat;

/** Anchor a synchronized scene once. Shadow, camera, and skin may sample partials in any order. */
final class GoddessSceneClock {
    private boolean anchored;
    private int revision, state;
    private long entityOrigin;

    void synchronize(int revision, int state, long start, long observedServerTick, int entityTick) {
        if (anchored && this.revision == revision && this.state == state) return;
        anchored = true;
        this.revision = revision;
        this.state = state;
        entityOrigin = entityTick - Math.max(0, observedServerTick - start);
    }

    float age(int entityTick, float partial) {
        return anchored ? (entityTick - entityOrigin) + partial : 0;
    }
}
