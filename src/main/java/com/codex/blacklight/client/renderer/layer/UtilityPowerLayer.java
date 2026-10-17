package com.codex.blacklight.client.renderer.layer;

import com.codex.blacklight.client.model.BlockShieldModel;
import com.codex.blacklight.client.model.BlockArmorModel;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;

public final class UtilityPowerLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private BlockShieldModel shield;
    public UtilityPowerLayer(net.minecraft.client.renderer.entity.RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}
    @Override public void render(PoseStack stack,MultiBufferSource buffer,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!(getParentModel() instanceof com.codex.blacklight.client.model.BlacklightPlayerModel parent))return;
        var poses=parent.matrices();if(poses==null)return;
        stack.pushPose();
        try{com.codex.blacklight.client.renderer.BlacklightBoneRendering.skeletonFrame(stack);renderSkeleton(player,stack,buffer,light,poses,partial,false);}
        finally{stack.popPose();}
    }
    public void renderFirstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial) {
        renderSkeleton(player,stack,buffer,light,poses,partial,true,-1);
    }
    public void renderFirstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial,int side) {
        renderSkeleton(player,stack,buffer,light,poses,partial,true,side);
    }
    private void renderSkeleton(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial,boolean firstPerson) {
        renderSkeleton(player,stack,buffer,light,poses,partial,firstPerson,-1);
    }
    private void renderSkeleton(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffer,int light,org.joml.Matrix4f[] poses,float partial,boolean firstPerson,int side) {
        if (player.isInvisible()) return;
        var armor = BlockArmorModel.get();
        var form = player.getData(ModAttachments.PLAYER_STATE).getActivePower();
        boolean mercer=com.codex.blacklight.client.model.MercerArmorMesh.active(player);
        if(mercer) {
            var custom=com.codex.blacklight.client.model.MercerArmorMesh.get();
            if(firstPerson)custom.renderFirstPerson(player,stack,buffer,light,partial,poses,side);
            else if(getParentModel() instanceof com.codex.blacklight.client.model.BlacklightPlayerModel model&&model.mercerFrame()!=null)
                custom.render(player,stack,buffer,light,partial,model.mercerFrame().skin(),-1);
        }
        if (!mercer && armor.visible(player, partial)) for (String name : armor.joints()) {
            var joint = com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName(name); if (joint == null) continue;
            boolean limb = name.startsWith("Arm_") || name.startsWith("Hand_");
            if(firstPerson&&(!limb||side>=0&&!name.endsWith(side==1?"_L":"_R")))continue;
            boolean weaponArm = limb && com.codex.blacklight.client.renderer.WeaponMorphTracker.sample(player,partial).coverage(name.endsWith("_L"))>=.72F;
            stack.pushPose();
            try { apply(stack, poses[joint.getId()]); armor.renderJoint(name, player, stack, buffer, light, partial, weaponArm); }
            finally { stack.popPose(); }
        }
        if(firstPerson)return;
        if (shield == null) shield = new BlockShieldModel();
        var joint = com.codex.blacklight.animation.PlayerMotionController.skeleton().searchJointByName("Tool_L"); if (joint == null) return;
        stack.pushPose();
        try {
            apply(stack, poses[joint.getId()]);
            com.codex.blacklight.client.animation.NativeShieldGrip.apply(player, stack, partial);
            shield.render(player, stack, buffer, light, partial);
        }
        finally { stack.popPose(); }
    }
    private static void apply(PoseStack stack, org.joml.Matrix4f matrix) {
        com.codex.blacklight.client.renderer.BlacklightBoneRendering.apply(stack,matrix);
    }
}
