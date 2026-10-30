package com.codex.blacklight.animation;

/** Landing feedback braces the impact without leasing a planted pose over live travel. */
public final class LandingMotionPolicy {
    public static final float MOVING_CUSHION_SECONDS=.30F;
    private LandingMotionPolicy() {}

    public static boolean isLanding(String clip) {
        return "basic_land_moving".equals(clip)||"basic_land_light".equals(clip)
                ||"basic_land_heavy".equals(clip)||"basic_land_devastating".equals(clip);
    }

    public static float movingCushionWeight(float seconds) {
        return 1-com.codex.blacklight.forge.WingMath.clamp(seconds/MOVING_CUSHION_SECONDS,0,1);
    }

    public static boolean permitsStride(String clip,float seconds) {
        return switch(clip==null?"":clip) {
            case "basic_land_moving","basic_land_light" -> true;
            case "basic_land_heavy" -> seconds>=.15F;
            case "basic_land_devastating" -> seconds>=.30F;
            default -> false;
        };
    }

    public static float leaseDuration(String clip,float authoredDuration) {
        return "basic_land_moving".equals(clip)?Math.min(authoredDuration,MOVING_CUSHION_SECONDS):authoredDuration;
    }

    public static boolean shouldRelease(String clip,float seconds,boolean grounded,boolean moving) {
        if(!isLanding(clip))return false;
        if(!grounded)return true;
        // Moving landings keep their brief upper-body cushion even though the legs run immediately.
        if("basic_land_moving".equals(clip))return seconds>=MOVING_CUSHION_SECONDS;
        // The stride mask already releases the legs. Keep its upper cushion until
        // the fade reaches zero, so a server clear cannot cut the torso blend short.
        return moving&&permitsStride(clip,seconds)&&seconds>=MOVING_CUSHION_SECONDS;
    }
}
