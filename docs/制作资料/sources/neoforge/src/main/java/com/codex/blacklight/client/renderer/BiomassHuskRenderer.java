package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BiomassHusk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class BiomassHuskRenderer extends MobRenderer<BiomassHusk,PlayerModel<BiomassHusk>> {
    public BiomassHuskRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false),.35F);}
    @Override public ResourceLocation getTextureLocation(BiomassHusk e){
        var connection=Minecraft.getInstance().getConnection();var info=connection==null||e.owner()==null?null:connection.getPlayerInfo(e.owner());
        return info==null?DefaultPlayerSkin.get(e.owner()==null?e.getUUID():e.owner()).texture():info.getSkin().texture();
    }
    @Override public void render(BiomassHusk e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(!e.erupted()){super.render(e,yaw,partial,stack,buffers,light);return;}
        stack.pushPose();
        double age=e.eruption(partial)*12;
        int count=e.debrisCount(),arms=e.worldTissue()?count:Math.max(8,count);
        // A bounded, low-poly block silhouette: no basal puddle and no CPU
        // deformation of the finisher's thousands of authored vertices.
        for(int arm=0;arm<arms;arm++){
            double angle=arm*2.3999632297;
            double growth=Math.max(0,Math.min(1,(age-(arm%3)*.5)/7));
            double extent=1-Math.pow(1-growth,3);
            Vec3 root=new Vec3(0,-.65,0);
            double radius=2.4+(arm%3)*.65;
            Vec3 target=new Vec3(Math.cos(angle)*radius,.8+(arm%4)*.6,Math.sin(angle)*radius);
            if(e.worldTissue()){
                var pos=e.tissuePosition(arm);
                if(!e.level().hasChunkAt(pos)||!e.level().getBlockState(pos).equals(e.debris(arm)))continue;
                target=Vec3.atCenterOf(pos).subtract(e.position());
            }
            Vec3 end=root.lerp(target,extent);
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
            BiomassGeometry.tendril(stack,out,light,root,end,age,extent);
            if(!e.worldTissue()&&arm<count&&extent>.01){
                stack.pushPose();stack.translate(end.x-.5,end.y-.5,end.z-.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(com.codex.blacklight.registry.ModBlocks.TISSUE_FIBER.get().defaultBlockState(),stack,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                stack.popPose();
            }
        }
        stack.popPose();
    }
    @Override public boolean shouldRender(BiomassHusk e,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        return e.erupted()?e.shouldRender(x,y,z)&&frustum.isVisible(e.getBoundingBox().inflate(14,9,14)):super.shouldRender(e,frustum,x,y,z);
    }
}
