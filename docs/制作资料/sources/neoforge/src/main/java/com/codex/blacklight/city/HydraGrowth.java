package com.codex.blacklight.city;

/** Pure bounded progression shared by persistence, visuals and server simulation. */
public final class HydraGrowth {
    public static final int REQUIRED_FEEDS = 5;
    public static final int MATURE_TICKS = 2400;
    public static final int RADIUS = 6;
    public static int feeds(int value) { return Math.clamp(value, 0, REQUIRED_FEEDS); }
    public static int age(int value) { return Math.clamp(value, 0, MATURE_TICKS); }
    public static float progress(int age, int feeds) {
        return Math.min(age(age)/(float)MATURE_TICKS, feeds(feeds)/(float)REQUIRED_FEEDS);
    }
    public static boolean mature(int age, int feeds) { return progress(age,feeds)>=1; }
    public static boolean inside(int x,int z) { return x*x+z*z<=RADIUS*RADIUS; }
    private HydraGrowth() {}
}
