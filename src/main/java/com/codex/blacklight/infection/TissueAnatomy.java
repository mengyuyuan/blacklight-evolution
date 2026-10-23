package com.codex.blacklight.infection;

/** Anatomical growth phases shared by authored host blueprints. No host-wide scaling. */
public final class TissueAnatomy {
    private TissueAnatomy() {}
    public static float local(long seed, float growth, int phase) {
        float delay = switch (phase) {
            case 1 -> .08F + .04F * TissueGrowth.sample(seed, 401);
            case 2 -> .36F + .05F * TissueGrowth.sample(seed, 402);
            case 3 -> .45F;
            default -> 0;
        };
        return smooth((growth - delay) / (1 - delay));
    }
    public static float smooth(float value) {
        double x = Math.clamp((double)value, 0, 1);
        return (float)(x * x * (3 - 2 * x));
    }
    /** Smooth three authored shape keys; both joins have zero derivative. */
    public static float coordinate(float initial, float middle, float mature, float growth) {
        return growth <= .5F ? initial + (middle - initial) * smooth(growth * 2)
                : middle + (mature - middle) * smooth(growth * 2 - 1);
    }
    /** Old three-key assets stay mature; generated assets carry two extra keys. */
    public static float coordinate(float[][] keys,int component,float growth,float overgrowth) {
        if(keys.length<5||overgrowth<=0)return coordinate(keys[0][component],keys[1][component],keys[2][component],growth);
        float t=Math.clamp(overgrowth,0,2);
        int from=t<1?2:3;
        return keys[from][component]+(keys[from+1][component]-keys[from][component])*(t<1?t:t-1);
    }
    public static float reveal(float growth, float start, float end) {
        return smooth((growth - start) / Math.max(.001F, end - start));
    }
}
