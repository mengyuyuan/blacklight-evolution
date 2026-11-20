package com.codex.blacklight.combat;

/** Animation intervals from the behemoth FIG, sampled on the same clock by the skin and its hit volumes. */
final class GoliathInjuryPose {
    private GoliathInjuryPose() {}

    // Pairs are Minecraft ticks and original 30 Hz animation frames respectively.
    private record Track(String clip, int... keys) {
        float[][] pose(float age) {
            for (int i = 2; i < keys.length; i += 2) {
                if (age <= keys[i]) {
                    float fraction = Math.max(0, (age - keys[i - 2]) / (keys[i] - keys[i - 2]));
                    return frame(clip, keys[i - 1] + fraction * (keys[i + 1] - keys[i - 1]));
                }
            }
            return frame(clip, keys[keys.length - 1]);
        }
    }

    // P2 behemoth.p3d animation records at 5451640, 5452580, 5454332.
    private static final Track LEG_BREAK = new Track("collapse", 0,0, 14,8, 29,27, 43,42);
    // 5494944, 5496784, 5497140.
    private static final Track ARM_BREAK = new Track("arm_fall", 0,0, 16,12, 40,56, 58,95);
    // The extension and stuck-arm recovery are distinct native animation tracks.
    private static final Track ARM_STRIKE = new Track("spike_windup", 0,30, 27,84, 39,90);
    private static final Track ARM_EMBEDDED = new Track("ERUPTION", 0,90, 16,118, 44,165, 58,195);
    // 5532728 through 5538752. Server gravity owns translation during the airborne interval.
    private static final Track LEAP = new Track("back_slam", 0,37, 7,46, 15,58, 25,72, 45,85, 63,96, 64,100);
    private static final Track LAND = new Track("back_slam", 0,101, 7,107, 27,130);
    private static final Track HEAD_LOWERED = new Track("TAUNT", 0,77, 9,94, 49,162, 60,198);
    private static final Track LEG_STUN = new Track("exposed", 0,25, 57,213);

    private static float[][] frame(String clip, float frame) {
        return GoliathMotion.sample(clip, frame * 20F / GoliathMotion.table().clip(clip).fps(), false);
    }

    static float[][] window(GoliathWindow window, float age, int duration, float idleAge) {
        return switch (window) {
            case HEAD -> HEAD_LOWERED.pose(age);
            case KNEE -> {
                var pose = LEG_STUN.pose(age);
                if (age < 5) pose = GoliathMotion.blendPose(frame("TAUNT", 126), pose, age / 5);
                if (duration-age < 8) pose = GoliathMotion.blendPose(pose, GoliathMotion.sample("idle", idleAge, true), 1-(duration-age)/8);
                yield pose;
            }
            case LEG_FALL -> {
                var pose = LEG_BREAK.pose(age);
                if (age > duration-5) pose = GoliathMotion.blendPose(pose, GoliathMotion.sample("crawl_idle", idleAge, true), 1-(duration-age)/5);
                yield pose;
            }
            case ARM -> {
                var pose = ARM_EMBEDDED.pose(age);
                if (age > duration-8) pose = GoliathMotion.blendPose(pose, GoliathMotion.sample("crawl_idle", idleAge, true), 1-(duration-age)/8);
                yield pose;
            }
            case ARM_FALL -> {
                var pose = ARM_BREAK.pose(age);
                if (age > duration-8) pose = GoliathMotion.blendPose(pose, frame("crippled_walk", 0), 1-(duration-age)/8);
                yield pose;
            }
            case FACE -> {
                if (age < 27) yield LAND.pose(age);
                // Frame 130 of the landing and frame 0 of the stunned head share the supine pose.
                var pose = GoliathMotion.sample("face_stun", age-27, true);
                float recovery = age-(duration-18);
                if (recovery > 0) {
                    var rise = frame("back_recover", 240+Math.min(1,recovery/18)*75);
                    pose = GoliathMotion.blendPose(pose, rise, recovery/6);
                    if (recovery>12) pose=GoliathMotion.blendPose(pose,frame("crippled_walk",0),(recovery-12)/6);
                }
                yield pose;
            }
            case FINISH -> GoliathMotion.sample("finish_idle", age, true);
            case NONE -> throw new IllegalArgumentException("No Goliath exposure");
        };
    }

    static float[][] attack(boolean armMissing, float age, float idleAge) {
        var pose = (armMissing ? LEAP : ARM_STRIKE).pose(age);
        if (age < 5) {
            var idle = armMissing ? frame("crippled_walk", 0) : GoliathMotion.sample("crawl_idle", idleAge, true);
            pose = GoliathMotion.blendPose(idle, pose, age/5);
        }
        return pose;
    }

    static float[][] crawl(boolean armMissing, GoliathGait gait, float partial, float idleAge) {
        var idle = armMissing ? frame("crippled_walk", 0) : GoliathMotion.sample("crawl_idle", idleAge, true);
        float weight = gait.weight(partial);
        if (weight <= 0) return idle;
        // P2's crawl blends into, and cycles, frames 28..58. Frames 0..27 are its entry.
        // Both tracks complete a stride in 30 source frames (20 game ticks).
        String clip = armMissing ? "crippled_walk" : "crawl";
        var step = frame(clip, (armMissing ? 0 : 28) + gait.phase(partial, 20)*1.5F);
        return weight >= 1 ? step : GoliathMotion.blendPose(idle, step, weight);
    }
}
