package com.codex.blacklight.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Armed run/sprint arm layer. Walking and bare hands always keep the authored stride.
 * With a weapon the arms keep the authored rhythm at reduced amplitude around the clip's
 * own average pose, shifted slightly toward the weapon guard so claws and blades do not
 * flail. Arms are never lifted into a full guard while running.
 */
public final class RunningArmLayer {
    private RunningArmLayer() { }
    static final String[] UPPER = {"Shoulder_R", "Arm_R", "Elbow_R", "Shoulder_L", "Arm_L", "Elbow_L"};
    static final String[] HANDS = {"Hand_R", "Tool_R", "Hand_L", "Tool_L"};
    /** Share of the authored swing kept around the stride's mean pose. */
    public static final float SWING_KEEP = .75F;
    /** How far the mean arm pose moves toward the guard. Small: the arms stay low. */
    public static final float ARM_TOWARD_GUARD = .2F;
    /** Wrists/weapons follow the guard more so the blade or claw points forward. */
    public static final float HAND_TOWARD_GUARD = .5F;
    private static final int MEAN_SAMPLES = 32;
    private static final Map<String, Map<String, BoneTransform>> MEANS = new ConcurrentHashMap<>();

    public static boolean applies(String key, boolean armed) {
        if (!armed) return false;
        return key.equals("basic_run") || key.startsWith("basic_sprint") && !key.equals("basic_sprint_brake");
    }

    public static BlacklightPose apply(BlacklightPose pose, String key, String guard) {
        if (guard == null || !applies(key, !guard.equals("basic_idle"))) return pose;
        var strideMean = mean(key);
        var guardMean = mean(guard);
        var result = pose.copy();
        for (String joint : UPPER) layer(result, pose, joint, strideMean, guardMean, ARM_TOWARD_GUARD);
        for (String joint : HANDS) layer(result, pose, joint, strideMean, guardMean, HAND_TOWARD_GUARD);
        return result;
    }

    private static void layer(BlacklightPose result, BlacklightPose pose, String joint,
                              Map<String, BoneTransform> strideMean, Map<String, BoneTransform> guardMean, float shift) {
        var current = pose.get(joint);
        var mean = strideMean.get(joint);
        if (current == null || mean == null) return;
        var guard = guardMean.get(joint);
        var base = guard == null ? mean : BoneTransform.blend(mean, guard, shift);
        // Deviation of this frame from the stride's mean, scaled down; keeps the swing phase.
        var deviation = new Quaternionf(mean.rotation()).conjugate().mul(current.rotation()).normalize();
        if (deviation.w < 0) deviation.set(-deviation.x, -deviation.y, -deviation.z, -deviation.w);
        var scaled = new Quaternionf().slerp(deviation, SWING_KEEP).normalize();
        var rotation = new Quaternionf(base.rotation()).mul(scaled).normalize();
        var translation = new Vector3f(current.translation()).sub(mean.translation()).mul(SWING_KEEP).add(base.translation());
        result.getJointTransformData().put(joint, new BoneTransform(translation, rotation, current.scale()));
    }

    static Map<String, BoneTransform> mean(String key) {
        return MEANS.computeIfAbsent(key, RunningArmLayer::computeMean);
    }

    private static Map<String, BoneTransform> computeMean(String key) {
        float duration = MotionLibrary.duration(key);
        var sums = new HashMap<String, float[]>();
        var reference = new HashMap<String, Quaternionf>();
        for (int i = 0; i < MEAN_SAMPLES; i++) {
            var pose = MotionLibrary.pose(key, duration * i / MEAN_SAMPLES, true);
            for (String joint : UPPER) accumulate(pose, joint, sums, reference);
            for (String joint : HANDS) accumulate(pose, joint, sums, reference);
        }
        var means = new HashMap<String, BoneTransform>();
        sums.forEach((joint, s) -> {
            var rotation = new Quaternionf(s[3], s[4], s[5], s[6]).normalize();
            means.put(joint, new BoneTransform(new Vector3f(s[0], s[1], s[2]).div(MEAN_SAMPLES), rotation, new Vector3f(1)));
        });
        return Map.copyOf(means);
    }

    private static void accumulate(BlacklightPose pose, String joint, Map<String, float[]> sums, Map<String, Quaternionf> reference) {
        var transform = pose.get(joint);
        if (transform == null) return;
        var q = new Quaternionf(transform.rotation());
        var first = reference.computeIfAbsent(joint, ignored -> new Quaternionf(q));
        if (first.dot(q) < 0) q.set(-q.x, -q.y, -q.z, -q.w);
        var s = sums.computeIfAbsent(joint, ignored -> new float[7]);
        var t = transform.translation();
        s[0] += t.x; s[1] += t.y; s[2] += t.z; s[3] += q.x; s[4] += q.y; s[5] += q.z; s[6] += q.w;
    }
}