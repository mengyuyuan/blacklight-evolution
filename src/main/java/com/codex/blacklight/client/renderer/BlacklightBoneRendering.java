package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts vanilla's upside-down model frame into Blacklight's metre-sized Y-up skeleton. */
public final class BlacklightBoneRendering {
    private BlacklightBoneRendering() { }
    public static void skeletonFrame(PoseStack stack) {
        stack.translate(0, 1.501F, 0);
        stack.scale(-1, -1, 1);
    }
    public static void vanillaFrame(PoseStack stack) {
        stack.scale(-1, -1, 1);
        stack.translate(0, -1.501F, 0);
    }
    /** Forge 1.20.1 entity stacks include the view rotation (unlike 1.21.1). */
    public static net.minecraft.world.phys.Vec3 worldPoint(PoseStack stack,Vector3f local) {
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        var point=stack.last().pose().transformPosition(new Vector3f(local));
        if(StandalonePlayerRendering.nativeHandPass())camera.rotation().transform(point);
        else point=entityWorldRelative(stack.last().pose(),local,com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix());
        return new net.minecraft.world.phys.Vec3(point.x,point.y,point.z).add(camera.getPosition());
    }
    static Vector3f entityWorldRelative(Matrix4f modelView,Vector3f local,org.joml.Matrix3fc inverseView) {
        return inverseView.transform(modelView.transformPosition(new Vector3f(local)));
    }
    public static void apply(PoseStack stack, Matrix4f matrix) {
        Vector3f scale = matrix.getScale(new Vector3f());
        if (matrix.determinant3x3() < 0) scale.z = -scale.z;
        Matrix4f rotation = new Matrix4f(matrix).scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        stack.translate(matrix.m30(), matrix.m31(), matrix.m32());
        stack.mulPose(rotation.getNormalizedRotation(new Quaternionf()));
        stack.scale(scale.x, scale.y, scale.z);
    }
}
