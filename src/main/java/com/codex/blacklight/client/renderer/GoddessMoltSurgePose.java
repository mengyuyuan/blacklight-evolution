package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessRig;
import com.codex.blacklight.combat.GreeneMolt;
import com.codex.blacklight.combat.GreeneMoltSurge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The final pressure front is pushed out by the chest and both palms of the airborne body. */
final class GoddessMoltSurgePose {
    private final Matrix4f chest = new Matrix4f(), head = new Matrix4f();
    private final Matrix4f[] arms = {new Matrix4f(), new Matrix4f()};
    private final Matrix4f[] forearms = {new Matrix4f(), new Matrix4f()};
    private final Matrix4f[] palms = {new Matrix4f(), new Matrix4f()};
    private final Matrix4f bone = new Matrix4f(), target = new Matrix4f();
    private final Vector3f position = new Vector3f(), otherPosition = new Vector3f();
    private final Quaternionf rotation = new Quaternionf(), otherRotation = new Quaternionf();

    private static float ease(float tick, float lo, float hi) {
        float f = Math.max(0, Math.min(1, (tick - lo) / (hi - lo)));
        return f * f * (3 - 2 * f);
    }

    private static void hinge(Matrix4f delta, float[] pose, int joint, float x, float y, float z) {
        int p = joint * 16 + 12;
        float px = pose[p], py = pose[p + 1], pz = pose[p + 2];
        delta.translate(px, py, pz).rotateXYZ(x, y, z).translate(-px, -py, -pz);
    }

    void release(float tick, float[] pose) {
        int gather = GreeneMoltSurge.GATHER, release = GreeneMoltSurge.RELEASE;
        int end = GreeneMoltSurge.ARRIVAL;
        if (tick <= gather - 8 || tick >= end) return;
        float breathe = ease(tick, gather - 8, gather + 3) * (1 - ease(tick, release - 4, release + 2));
        float push = ease(tick, release - 4, release + 3) * (1 - ease(tick, release + 15, end));
        float fingers = ease(tick, release - 2, release + 4) * (1 - ease(tick, release + 21, end));
        chest.identity();
        hinge(chest, pose, 2, -.14F * breathe + .15F * push, 0, 0);
        head.set(chest);
        hinge(head, pose, 0, -.23F * breathe + .28F * push, 0, 0);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1 : -1;
            int shoulder = i == 0 ? 5 : 19;
            arms[i].set(chest);
            hinge(arms[i], pose, shoulder, .10F * breathe - .21F * push,
                    side * (.17F * breathe - .08F * push), side * (-.39F * breathe + .53F * push));
            forearms[i].set(arms[i]);
            hinge(forearms[i], pose, shoulder + 1, -.27F * breathe + .18F * push, 0, side * .08F * push);
            palms[i].set(forearms[i]);
            hinge(palms[i], pose, shoulder + 2, -.14F * breathe + .23F * fingers, side * -.18F * fingers, 0);
        }
        for (int j = 0; j < GreeneColonies.JOINTS; j++) {
            int carrier = j < GreeneColonies.NATIVE ? GoddessRig.carrier(j)
                    : GreeneColonies.GROUPS.get((j - GreeneColonies.NATIVE) / 12).owner();
            Matrix4f delta;
            if (carrier == 0 || carrier == 33 || carrier == 34) delta = head;
            else if (carrier == 1) delta = chest;
            else if (carrier >= 5 && carrier <= 32) {
                int side = carrier < 19 ? 0 : 1;
                int index = carrier - (side == 0 ? 5 : 19);
                delta = index == 0 ? arms[side] : index == 1 ? forearms[side] : palms[side];
            } else continue;
            bone.set(pose, 16 * j).mulLocal(delta).get(pose, 16 * j);
        }
    }

    static float settle(float tick) {
        return ease(tick, GreeneMoltSurge.RELEASE + 20, GreeneMolt.DURATION);
    }

    // Both tracks use the same 791 bind groups; on the last frame, use the actual combat
    // idle sample for this entity's clock, including the hover, hair and native infected.
    void settle(float fraction, float hover, float[] pose, float[] idle) {
        for (int j = 0; j < GreeneColonies.NATIVE; j++) {
            int index = j * 16;
            target.set(idle, index);
            target.m31(target.m31() + hover);
            if (fraction >= 1) {
                target.get(pose, index);
                continue;
            }
            bone.set(pose, index);
            bone.getTranslation(position).lerp(target.getTranslation(otherPosition), fraction);
            bone.getUnnormalizedRotation(rotation).normalize()
                    .slerp(target.getUnnormalizedRotation(otherRotation).normalize(), fraction);
            bone.translationRotate(position, rotation).get(pose, index);
        }
    }
}
