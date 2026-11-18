package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.HelicopterModel;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class HelicopterRenderer extends EntityRenderer<BlackwatchHelicopter> {
    private final HelicopterModel model=new HelicopterModel();
    public HelicopterRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=2.5F;}
    @Override public ResourceLocation getTextureLocation(BlackwatchHelicopter entity){return new ResourceLocation("blacklight","textures/entity/blackwatch_helicopter.png");}
    @Override public void render(BlackwatchHelicopter entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.mulPose(Axis.ZP.rotationDegrees(entity.bank()));
        model.pose(entity,partial).render(pose,buffers.getBuffer(RenderType.entityCutout(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();ropes(entity,partial,pose,buffers);super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public boolean shouldRender(BlackwatchHelicopter entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        return super.shouldRender(entity,frustum,x,y,z)||entity.deploymentData().getIntArray("Troops").length>0&&frustum.isVisible(entity.getBoundingBox().expandTowards(0,-20,0));
    }
    private static void ropes(BlackwatchHelicopter craft,float partial,PoseStack pose,MultiBufferSource buffers){
        int[] ids=craft.deploymentData().getIntArray("Troops"),sides=craft.deploymentData().getIntArray("Sides");
        var origin=craft.getPosition(partial);var lines=buffers.getBuffer(RenderType.lines());
        for(int i=0;i<Math.min(ids.length,sides.length);i++){
            var troop=craft.level().getEntity(ids[i]);if(!(troop instanceof com.codex.blacklight.entity.MilitarySoldier)||!troop.isAlive())continue;
            float yaw=net.minecraft.util.Mth.rotLerp(partial,craft.yRotO,craft.getYRot());
            var from=new net.minecraft.world.phys.Vec3(sides[i]*3.6,1.7,-.5).yRot((float)Math.toRadians(yaw));
            var to=troop.getPosition(partial).add(0,1.55,0).subtract(origin);var normal=to.subtract(from).normalize();
            lines.vertex(pose.last().pose(),(float)from.x,(float)from.y,(float)from.z).color(75,71,56,255).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
            lines.vertex(pose.last().pose(),(float)to.x,(float)to.y,(float)to.z).color(75,71,56,255).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        }
    }
}
