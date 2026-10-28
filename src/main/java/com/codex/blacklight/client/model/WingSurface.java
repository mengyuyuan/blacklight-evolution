package com.codex.blacklight.client.model;
import com.codex.blacklight.animation.SovereignWingMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;
/** Shared attachment, flight clock and wake contract for both appearances. */
public interface WingSurface {
    void prepare(SovereignWingMotion.Pose motion,Vector3f strokeAxis,float deployment,float air,int order);
    void prepareRender(PoseStack stack);
    void draw(PoseStack stack,MultiBufferSource buffers,int light);
    Vector3f tip(String side,int number);
    Vector3f[] leadingEdge(String side);
}
