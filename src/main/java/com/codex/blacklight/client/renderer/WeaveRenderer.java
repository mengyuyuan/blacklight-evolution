package com.codex.blacklight.client.renderer;

import com.codex.blacklight.registry.ModBlockEntities;
import com.codex.blacklight.world.WeaveCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WeaveRenderer implements BlockEntityRenderer<WeaveCell> {
    public WeaveRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerBlockEntityRenderer(ModBlockEntities.WEAVE_CELL.get(),WeaveRenderer::new);
    }
    @Override public void render(WeaveCell cell,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var level=cell.getLevel();
        if(level==null)return;
        if(!cell.offering().isEmpty())item(cell,cell.offering(),0,0,.70F,pose,buffers,light,overlay);
        boolean assembled=cell.displaysMantle();
        if(assembled){
            boolean infusing=cell.weaver()!=null;
            WeaveMantle.draw(pose,buffers,light,infusing?cell.inward():null,
                    infusing?level.getGameTime()+partial-cell.started():0);
        }
        var guide=cell.guide();
        if(guide!=null && cell.weaver()==null && assembled){
            for(int z=0;z<5;z++)for(int x=0;x<5;x++){
                var expected=guide.ingredient(x,z);
                if(expected!=null && level.getBlockEntity(cell.getBlockPos().offset(x-2,0,z-2)) instanceof WeaveCell target && target.offering().isEmpty())
                    item(cell,expected.getDefaultInstance(),x-2,z-2,.33F,pose,buffers,light,overlay);
            }
        }
        if(cell.weaver()==null)return;
        var player=level.getPlayerByUUID(cell.weaver());
        if(player==null)return;
        double time=level.getGameTime()+partial-cell.started();
        WeaveTransport.draw(cell,player,time,pose,buffers,light);
    }
    private static void item(WeaveCell cell,ItemStack stack,int x,int z,float scale,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();
        boolean overCore=x==0 && z==0 && cell.getBlockState().is(com.codex.blacklight.registry.ModBlocks.WEAVE_CORE.get());
        pose.translate(x+.5,.96+(scale>.5?.065:0)+(overCore?.32:0),z+.5);
        pose.mulPose(Axis.XP.rotationDegrees(-72));pose.scale(scale,scale,scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,light,overlay,pose,buffers,cell.getLevel(),0);
        pose.popPose();
    }

}
