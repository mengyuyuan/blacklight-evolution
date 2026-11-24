package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.SpiderAnatomy;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Applies the last combat stance and the spider's bellow to the original 39-joint molt track. */
final class SpiderMoltPose {
    static final float END = 8F;
    private final int count = SpiderAnatomy.MOLT_JOINTS.size();
    private final int[] parents = new int[count];
    private final Vector3f[] pivots = new Vector3f[count];
    private final Matrix4f[] capture = matrices(count);
    private final Matrix4f[] authored = matrices(count);
    private final Matrix4f[] result = matrices(count);
    private final Matrix4f start = new Matrix4f(), goal = new Matrix4f(), local = new Matrix4f();
    private final Matrix4f anchorChange = new Matrix4f();
    private final Vector3f anchor = new Vector3f(), to = new Vector3f(), scale = new Vector3f();
    private final Quaternionf rotation = new Quaternionf(), endRotation = new Quaternionf();
    private final float[] encoded = new float[16];

    SpiderMoltPose() {
        var joints = SpiderAnatomy.MOLT_JOINTS;
        for (int i = 0; i < count; i++) {
            String name = joints.get(i);
            String parent = SpiderAnatomy.PARENTS.get(name);
            parents[i] = parent == null ? -1 : joints.indexOf(parent);
            pivots[i] = SpiderAnatomy.PIVOTS.get(name).toVector3f();
        }
    }

    private static Matrix4f[] matrices(int count) {
        var values = new Matrix4f[count];
        Arrays.setAll(values, ignored -> new Matrix4f());
        return values;
    }

    private static float ease(float t, float start, float end) {
        float u = Math.max(0, Math.min(1, (t - start) / (end - start)));
        return u * u * (3 - 2 * u);
    }

    static float settle(float seconds, int joint) {
        if (joint >= 13 && joint <= 28) {
            float delay = ((joint - 13) / 2 % 4) * .12F;
            return ease(seconds, .95F + delay, 3.6F + delay);
        }
        return joint <= 1 ? ease(seconds, .45F, 4.2F) : ease(seconds, .65F, 2.55F);
    }

    private void relative(Matrix4f[] world, int joint, Matrix4f out) {
        int parent = parents[joint];
        if (parent < 0) out.set(world[joint]);
        else world[parent].invert(out).mul(world[joint]);
    }

    void sample(float seconds, int broken, int[] lastPose, float[] bones) {
        boolean hasCapture = lastPose.length == count * 16;
        for (int i = 0; i < count; i++) {
            authored[i].set(bones, i * 16);
            if (hasCapture) {
                for (int n = 0; n < 16; n++) encoded[n] = Float.intBitsToFloat(lastPose[i * 16 + n]);
                capture[i].set(encoded);
            }
        }
        for (int i = 0; i < count; i++) {
            relative(authored, i, goal);
            float weight = hasCapture ? settle(seconds, i) : 1;
            if (hasCapture && i == 0 && broken != 0)
                weight = ease(seconds, 3.0F, END);
            Vector3f pivot = pivots[i];
            if (weight < 1) {
                relative(capture, i, start);
                if (weight == 0) local.set(start);
                else blendAtPivot(weight, pivot);
                // A fatal hit during a leap still has to land before the legs take the load.
                if (i == 0 && start.m31() > 2.2F) {
                    float landing = ease(seconds, 0, 1.15F);
                    local.m31(start.m31() + (goal.m31() - start.m31()) * landing);
                }
            } else local.set(goal);

            if (seconds > 0 && seconds < 6.4F)
                bellow(i, seconds, pivot);
            int parent = parents[i];
            if (parent < 0) result[i].set(local);
            else result[i].set(result[parent]).mul(local);
            result[i].get(bones, i * 16);
        }
        // All eight bases of the 214 authored suction tracks are vertices of the carapace.
        // Their far ends still meet the captured creatures at the authored times.
        result[1].mul(authored[1].invert(goal), anchorChange);
    }

    private void blendAtPivot(float weight, Vector3f pivot) {
        start.transformPosition(pivot, anchor).lerp(goal.transformPosition(pivot, to), weight);
        start.getUnnormalizedRotation(rotation).normalize()
            .slerp(goal.getUnnormalizedRotation(endRotation).normalize(), weight);
        start.getScale(scale).lerp(goal.getScale(to), weight);
        local.translationRotateScale(anchor, rotation, scale).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private void bellow(int joint, float seconds, Vector3f p) {
        if(joint<2 || joint>=13 && joint<=28)return;
        // The jaw and shoulder lead on the audible beat. Crest, hanging veil and fingers
        // continue their arc after the bellow, then follow the inward pull of the first tendrils.
        float delay=switch(joint){
            case 31,34,37 -> .075F;
            case 32 -> .18F;
            case 35,38 -> .13F;
            default -> 0F;
        };
        float t=seconds-delay;
        float brace=ease(t,0,.43F)*(1-ease(t,.43F,.80F));
        float roar=ease(t,.43F,.80F)*(1-ease(t,1.24F,2.16F));
        float draw=ease(t,1.26F,2.08F)*(1-ease(t,2.30F,3.72F));
        float haul=ease(t,2.65F,3.48F)*(1-ease(t,3.82F,4.60F))
                -.62F*ease(t,4.02F,4.72F)*(1-ease(t,5.18F,6.1F));
        float x = 0, y = 0, z = 0;
        if (joint >= 2 && joint <= 12) {
            // Hinge the mouth plates away from their own edge of the opening.
            float dx = p.x - .82F, dy = p.y - 4.04F;
            float radius = (float)Math.sqrt(dx * dx + dy * dy);
            float spread = (-.10F * brace + .67F * roar + .20F * draw + .08F * haul) / radius;
            x = dy * spread;
            y = -dx * spread;
        } else switch (joint) {
            case 29 -> { x = -.31F * brace + .34F * roar - .29F * draw; z = .085F * brace - .10F * roar; y=.27F*haul; }
            case 30 -> { x = -.16F * brace + .27F * roar - .13F * draw; y = -.11F * roar - .19F*haul; }
            case 31, 32 -> {x = .11F * brace - .18F * roar + .20F * draw;z=(joint==31?.12F:.26F)*haul;}
            case 33, 36 -> {
                float side = joint == 33 ? 1 : -1;
                x = -.10F * brace + .29F * roar - .24F * draw;
                z = side * (-.22F * brace + .59F * roar - .31F * draw)+.24F*haul;
            }
            case 34, 37 -> x = .34F * brace - .32F * roar + .38F * draw+(joint==34?.20F:-.18F)*haul;
            case 35, 38 -> y = (joint == 35 ? 1 : -1) * (.28F * roar - .20F * draw) -.24F*haul;
            default -> { return; }
        }
        local.translate(p).rotateXYZ(x, y, z).translate(-p.x, -p.y, -p.z);
    }

    void alignTendrils(float[] paths) {
        int points = 16;
        for (int row = 0; row < paths.length; row += points * 3) {
            anchor.set(paths[row], paths[row + 1], paths[row + 2]);
            anchorChange.transformPosition(anchor, to).sub(anchor);
            for (int i = 0; i < points - 1; i++) {
                float strength = 1 - ease(i, 0, points - 1);
                int at = row + i * 3;
                paths[at] += to.x * strength;
                paths[at + 1] += to.y * strength;
                paths[at + 2] += to.z * strength;
            }
        }
    }
}
