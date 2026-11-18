package com.codex.blacklight.client.renderer;
import com.codex.blacklight.entity.HydraDebris;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;

public final class HydraDebrisRenderer extends EntityRenderer<HydraDebris> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public HydraDebrisRenderer(EntityRendererProvider.Context c){super(c);blocks=c.getBlockRenderDispatcher();shadowRadius=.8F;}
    @Override public ResourceLocation getTextureLocation(HydraDebris e){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(HydraDebris e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
        p.pushPose();var offset=e.center(partial).subtract(e.getPosition(partial));p.translate(offset.x,offset.y,offset.z);
        p.mulPose(Axis.YP.rotationDegrees(e.flightAge(partial)*16));p.mulPose(Axis.XP.rotationDegrees(e.flightAge(partial)*11));
        float size=e.size();p.scale(size,size,size);p.translate(-.5,-.5,-.5);
        blocks.renderSingleBlock(e.material(),p,buffers,light,OverlayTexture.NO_OVERLAY);p.popPose();super.render(e,yaw,partial,p,buffers,light);
    }
}
