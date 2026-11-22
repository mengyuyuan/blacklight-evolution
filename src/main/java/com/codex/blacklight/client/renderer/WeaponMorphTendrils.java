package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.animation.WeaponMorphTiming;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Surface fibres travel with the animated limb, including the bend at the elbow. */
public final class WeaponMorphTendrils {
    private static final Vec3 SIDE = new Vec3(1, 0, 0);
    private static final int STEPS = 18;
    private WeaponMorphTendrils() {}

    public static void arms(WeaponMorphTracker.Sample morph, Matrix4f[] pose, PoseStack stack,
                            MultiBufferSource buffers, int light, int viewSide, float fit) {
        if (!morph.transitioning() || morph.flow() < .01F || VisionPowerRenderer.drawingSilhouettes()) return;
        var rig = PlayerMotionController.skeleton();
        for (int side = 0; side < 2; side++) {
            if (viewSide >= 0 && viewSide != side) continue;
            boolean left = side == 1;
            var form = morph.projectionForm(left);
            if (form == PowerForm.NONE) continue;
            float coverage = morph.coverage(left);
            boolean forming = WeaponMorphTiming.arm(morph.target(), left);
            // On a rapid switch the previous weapon is already partly grown.
            double front = forming ? Math.max(morph.phase(), coverage) : coverage;
            var upper = pose[(left ? rig.armL : rig.armR).getId()];
            var elbow = pose[(left ? rig.handL : rig.handR).getId()];
            var out = buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
            int strands = form == PowerForm.HAMMERFISTS ? 7 : form == PowerForm.WHIPFIST ? 4 : 5;
            for (int lane = 0; lane < strands; lane++) {
                double reach = Math.min(1, front * (1.30 - .055 * (lane % 4)));
                if (reach < .015) continue;
                var path = new Vec3[STEPS + 1];
                var widths = new double[path.length];
                for (int i = 0; i <= STEPS; i++) {
                    double u = i / (double) STEPS, t = u * reach;
                    double turn = (lane + .3) * 2 * Math.PI / strands + t * (5.6 + lane % 3)
                            + .38 * Math.sin(6 * t + lane * 1.9) + .34 * morph.phase();
                    if (left) turn = -turn;
                    double x = Math.cos(turn), z = Math.sin(turn);
                    double swell = form == PowerForm.HAMMERFISTS ? .14 : form == PowerForm.MUSCLE_MASS ? .085 : .045;
                    double radius = fit * (.13 / Math.max(Math.abs(x), Math.abs(z)) + swell * coverage * t
                            + morph.flow() * (.032 + .031 * Math.sin(Math.PI * u)));
                    path[i] = armPoint(upper, elbow, t, radius * x, radius * z, fit);
                    widths[i] = fit * morph.flow() * (lane % 3 == 0 ? .039 : .025)
                            * Math.pow(Math.sin(Math.PI * u), .62) * (1.25 - .45 * u);
                }
                draw(stack, out, light, lane, path, widths);
            }
        }
    }

    private static Vec3 armPoint(Matrix4f upper, Matrix4f elbow, double t, double x, double z, float fit) {
        Vec3 bend = point(elbow, x, 0, z);
        if (t < .46) return point(upper, x, .018, z).lerp(bend, t / .46);
        return point(elbow, x, (t - .46) / .54 * .44 * fit, z);
    }

    static void socket(PoseStack stack, MultiBufferSource buffers, int light, float growth, float flow) {
        if (flow < .01F || VisionPowerRenderer.drawingSilhouettes()) return;
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        for (int lane = 0; lane < 2; lane++) {
            var path = new Vec3[11];
            var widths = new double[path.length];
            for (int i = 0; i < path.length; i++) {
                double u = i / 10.0, t = u * growth, angle = lane * Math.PI + t * 7.4;
                double radius = .05 + .026 * Math.sin(Math.PI * u);
                path[i] = new Vec3(Math.cos(angle) * radius, -.06 + .31 * t, Math.sin(angle) * radius);
                widths[i] = .023 * flow * Math.sin(Math.PI * u);
            }
            draw(stack, out, light, lane, path, widths);
        }
    }

    private static Vec3 point(Matrix4f matrix, double x, double y, double z) {
        var v = matrix.transformPosition((float)x, (float)y, (float)z, new Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    private static void draw(PoseStack stack, VertexConsumer out, int light, int lane, Vec3[] path, double[] widths) {
        int tint = lane % 4 == 1 ? 0xffbd9692 : 0xffe6e2e5;
        ConsumeTendrilSegmentMesh.draw(stack, out, light, path, widths, SIDE, tint, false);
    }
}
