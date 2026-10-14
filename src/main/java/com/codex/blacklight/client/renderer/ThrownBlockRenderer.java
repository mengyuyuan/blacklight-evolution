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
        stack.pushPose();
        if(e.holderId()>=0&&e.level().getEntity(e.holderId()) instanceof net.minecraft.world.entity.player.Player p){
            var center=e.carryCenter(p,com.codex.blacklight.client.animation.BlockThrowPose.center(p,e.twoHanded(),partial),partial);var pos=e.getPosition(partial);stack.translate(center.x-pos.x,center.y-pos.y,center.z-pos.z);
            stack.mulPose(Axis.YP.rotationDegrees(180-com.codex.blacklight.combat.BlockThrowMotion.yaw(p,partial)));
        }
        if(e.holderId()<0){stack.mulPose(Axis.YP.rotationDegrees((e.tickCount+partial)*17));stack.mulPose(Axis.XP.rotationDegrees((e.tickCount+partial)*11));}
        stack.scale(.9F,.9F,.9F);var cargo=e.cargo();var size=cargo.size();stack.translate(-size.x*.5,-size.y*.5,-size.z*.5);
        for(var cell:cargo.cells()){stack.pushPose();stack.translate(cell.offset().getX(),cell.offset().getY(),cell.offset().getZ());blocks.renderSingleBlock(cell.state(),stack,buffers,light,OverlayTexture.NO_OVERLAY);stack.popPose();}stack.popPose();
        super.render(e,yaw,partial,stack,buffers,light);
    }
}
