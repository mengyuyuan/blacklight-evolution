package com.codex.blacklight.infection;

/** Persistent anatomy from UUID + saved tissue age; never chosen again per frame or observer. */
public final class TissueOrgans {
    public enum Kind { TENDRIL, MUSCLE, CARAPACE, BLADE }
    public record Organ(Kind kind, float growth, float length, float radius, float offset) {}
    public record Mutation(float x, float y, float z) {
        public boolean active() { return x != 1 || y != 1 || z != 1; }
    }
    public static final int WINDUP = 8, HIT = 12, DURATION = 28;
    public static final int GRAB_RELEASE=23;
    private TissueOrgans() {}
    public static long seed(java.util.UUID id) { return id.getMostSignificantBits() ^ id.getLeastSignificantBits(); }
    public static int count(long seed) { return 4 + (TissueGrowth.sample(seed, 91) > .55F ? 1 : 0); }
    public static Organ organ(long seed, long age, int slot) {
        return organ(seed,age,slot,TissueEvolution.sample(seed,age));
    }
    public static Organ organ(long seed,long age,int slot,TissueEvolution.State evolution) {
        Kind kind = switch(slot) { case 0 -> Kind.TENDRIL; case 1, 3 -> Kind.MUSCLE; case 4 -> Kind.BLADE; default -> Kind.CARAPACE; };
        float delay = .12F + slot * .075F + TissueGrowth.sample(seed, slot + 100) * .08F;
        float growth = smooth((TissueGrowth.progress(age) - delay) / (1 - delay));
        float length = growth * (.9F + .9F * TissueGrowth.sample(seed, slot + 120));
        float radius = (float)Math.sqrt(growth) * (.16F + .13F * TissueGrowth.sample(seed, slot + 140));
        float excess=TissueGrowth.overgrowth(age);
        length*=1+.30F*excess;radius*=1+.25F*excess;
        length*=TissueEvolution.tendrilLength(evolution);radius*=TissueEvolution.tendrilGirth(evolution);
        if(kind == Kind.CARAPACE) { length *= .55F; radius *= 1.8F; }
        return new Organ(kind, growth, length, radius, TissueGrowth.sample(seed, slot + 160));
    }
    public static float bodyFactor(float width, float height) {
        return Math.clamp(Math.max(width, height) * .65F, .65F, 1.6F);
    }
    /** Extra distance outside the host box. The visible tendril can reach this exact distance. */
    public static float reach(long seed, long age, float width, float height) {
        return reach(seed,age,width,height,1);
    }
    /** width/height are the actual grown collider; remove its scale before fitting the root. */
    public static float reach(long seed,long age,float width,float height,float bodyScale) {
        return reach(seed,age,width,height,bodyScale,TissueEvolution.sample(seed,age));
    }
    public static float reach(long seed,long age,float width,float height,float bodyScale,TissueEvolution.State evolution) {
        var organ = organ(seed, age, 0,evolution);
        return organ.growth < .25F ? 0 : organ.length * bodyFactor(width/bodyScale, height/bodyScale)*bodyScale;
    }
    public static int dominantArm(long seed) { return TissueGrowth.sample(seed, 181) < .5F ? 2 : 3; }
    public static Mutation mutation(long seed, long age, int role) {
        float g = smooth((TissueGrowth.progress(age) - .2F) / .8F);
        if(role == 0) return new Mutation(1 + .22F * g, 1 + .04F * g, 1 + .13F * g);
        if(role == 1) return new Mutation(1 + .08F * g, 1 + .04F * g, 1 + .12F * g);
        if(role == dominantArm(seed)) return new Mutation(1 + .72F * g, 1 + .52F * g, 1 + .55F * g);
        return new Mutation(1, 1, 1);
    }
    public static float extension(float elapsed) {
        if(elapsed < WINDUP || elapsed >= DURATION) return 0;
        if(elapsed < HIT) return smooth((elapsed - WINDUP) / (HIT - WINDUP));
        return 1 - smooth((elapsed - HIT) / (DURATION - HIT));
    }
    private static float smooth(float x) { float t = Math.clamp(x, 0, 1); return t * t * (3 - 2 * t); }
}
