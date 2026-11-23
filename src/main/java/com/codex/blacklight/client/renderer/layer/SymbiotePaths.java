package com.codex.blacklight.client.renderer.layer;

import static com.codex.blacklight.client.renderer.ConsumeAnatomy.*;

/** Vertical ranges in the skin's rest pose, in model pixels. */
final class SymbiotePaths {
    static final int CHEST=1, LEGS=2, FEET=4, CROWN=8;
    static final int FULL=CHEST | LEGS | FEET | CROWN;
    record Flow(int slots, int region, double low, double high) {}
    static final Flow[] FLOWS={
        new Flow(CROWN,HEAD,-7.4,-1.2),
        new Flow(CHEST,BODY,.5,11.5), new Flow(CHEST,BODY,2,10.7),
        new Flow(CHEST,LEFT_ARM,.5,5.7), new Flow(CHEST,RIGHT_ARM,.5,5.7),
        new Flow(LEGS,LEFT_LEG,12.3,19.7), new Flow(LEGS,RIGHT_LEG,12.3,19.7),
        new Flow(FEET,LEFT_LEG,20.2,23.8), new Flow(FEET,RIGHT_LEG,20.2,23.8),
        new Flow(FULL,LEFT_ARM,4,11.6), new Flow(FULL,RIGHT_ARM,4,11.6),
        new Flow(FULL,LEFT_LEG,15,22), new Flow(FULL,RIGHT_LEG,15,22),
        new Flow(CROWN,HEAD,-6.8,-2.0),
        new Flow(CHEST,BODY,.8,8.2), new Flow(CHEST,BODY,3.5,11.6),
        new Flow(CHEST,LEFT_ARM,1.5,6.5), new Flow(CHEST,RIGHT_ARM,1.5,6.5),
        new Flow(LEGS,LEFT_LEG,13.7,20), new Flow(LEGS,RIGHT_LEG,13.7,20),
        new Flow(FEET,LEFT_LEG,20.6,23.6), new Flow(FEET,RIGHT_LEG,20.6,23.6)
    };
    // SplitMix64 finalizer; deterministic across frames and independent of render order.
    static double noise(long key) {
        key=(key ^ (key >>> 30))*0xbf58476d1ce4e5b9L;
        key=(key ^ (key >>> 27))*0x94d049bb133111ebL;
        return ((key ^ (key >>> 31)) >>> 11)*0x1.0p-53;
    }
    private SymbiotePaths() {}
}
