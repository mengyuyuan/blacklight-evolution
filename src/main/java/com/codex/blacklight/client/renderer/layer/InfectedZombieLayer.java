package com.codex.blacklight.client.renderer.layer;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.model.InfectedZombieMesh;
import com.codex.blacklight.client.renderer.BlacklightBoneRendering;
import com.codex.blacklight.client.renderer.InfectedZombieRenderer;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.InfectionTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import org.joml.Matrix4f;

public final class InfectedZombieLayer extends RenderLayer<Zombie,ZombieModel<Zombie>> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/claw_white.png");
    private InfectedZombieMesh model;
    public InfectedZombieLayer(RenderLayerParent<Zombie,ZombieModel<Zombie>> parent){super(parent);}
    @Override public void render(PoseStack stack,MultiBufferSource buffer,int light,Zombie zombie,float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!BlacklightInfectionSystem.isInfected(zombie)||zombie.isInvisible())return;
        if(model==null)model=new InfectedZombieMesh();
        float t=InfectedZombieRenderer.progress(zombie,partial);
        var skeleton=PlayerMotionController.skeleton();
        var poses=skeleton.getPoseMatrices(new BlacklightPose());
        var nativeModel=getParentModel();
        for(String name:model.joints()) {
            var joint=skeleton.searchJointByName(name);if(joint==null)continue;
            net.minecraft.client.model.geom.ModelPart part=name.equals("Head")?nativeModel.head:name.equals("Chest")||name.equals("Torso")?nativeModel.body:name.endsWith("_R")&&(name.contains("Arm")||name.contains("Hand")||name.contains("Tool"))?nativeModel.rightArm:name.endsWith("_L")&&(name.contains("Arm")||name.contains("Hand")||name.contains("Tool"))?nativeModel.leftArm:name.endsWith("_R")?nativeModel.rightLeg:nativeModel.leftLeg;
            float px=-part.x/16F,py=1.501F-part.y/16F,pz=part.z/16F;
            var delta=new Matrix4f().translation(px,py,pz).rotateZ(-part.zRot).rotateY(-part.yRot).rotateX(-part.xRot).translate(-px,-py,-pz);
            var posed=new Matrix4f(delta).mul(poses[joint.getId()]);
            stack.pushPose();
            try (var owner=com.codex.blacklight.client.renderer.AdaptiveRagdoll.attachment(part)) {
                BlacklightBoneRendering.skeletonFrame(stack);BlacklightBoneRendering.apply(stack,posed);
                int overlay=OverlayTexture.pack(OverlayTexture.u(0),OverlayTexture.v(zombie.hurtTime>0||zombie.deathTime>0));
                model.render(name,stack,buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE)),light,overlay,InfectionTimeline.growth(name,t));
            }finally{stack.popPose();}
        }
    }
}
