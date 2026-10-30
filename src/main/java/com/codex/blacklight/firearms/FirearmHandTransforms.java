package com.codex.blacklight.firearms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Actual PlayerModel FK, including the standard/slim shoulder joint height. */
public final class FirearmHandTransforms {
    private FirearmHandTransforms(){}
    public static void feedback(PoseStack pose,FirearmViewState.Frame frame,boolean left){
        int sign=left?-1:1;
        pose.translate(0,-frame.reload()*.11,frame.recoil()*.065);
        pose.mulPose(Axis.XP.rotationDegrees(frame.recoil()*1.103638F+frame.reload()*8));
        pose.mulPose(Axis.ZP.rotationDegrees(sign*frame.reload()*7));
    }
    public static Vector3f wrist(ModelPart arm,boolean left,boolean slim){
        var bind=new PoseStack();arm.translateAndRotate(bind);
        return bind.last().pose().transformPosition(new Vector3f((left?1:-1)*(slim?.5F:1F)/16,10/16F,0));
    }
    public static void apply(PoseStack pose,FirearmHandPose.Hand hand,ModelPart arm,boolean slim){
        pose.translate(hand.x(),hand.y(),hand.z());pose.mulPose(Axis.ZP.rotation((float)hand.roll()));
        pose.mulPose(Axis.YP.rotation((float)hand.yaw()));pose.mulPose(Axis.XP.rotation((float)hand.pitch()));
        var wrist=wrist(arm,hand.left(),slim);pose.translate(-wrist.x,-wrist.y,-wrist.z);
    }
}
