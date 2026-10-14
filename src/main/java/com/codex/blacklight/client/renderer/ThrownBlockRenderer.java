package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.ThrownBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;

public final class ThrownBlockRenderer extends EntityRenderer<ThrownBlock> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public ThrownBlockRenderer(EntityRendererProvider.Context context){super(context);blocks=context.getBlockRenderDispatcher();shadowRadius=.45F;}
    @Override public ResourceLocation getTextureLocation(ThrownBlock entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(ThrownBlock e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        stack.pushPose();stack.translate(0,.45,0);
        if(e.holderId()<0){stack.mulPose(Axis.YP.rotationDegrees((e.tickCount+partial)*17));stack.mulPose(Axis.XP.rotationDegrees((e.tickCount+partial)*11));}
        stack.scale(.9F,.9F,.9F);stack.translate(-.5,-.5,-.5);
        blocks.renderSingleBlock(e.block(),stack,buffers,light,OverlayTexture.NO_OVERLAY);stack.popPose();
        super.render(e,yaw,partial,stack,buffers,light);
    }
}
