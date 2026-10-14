package com.codex.blacklight.client.renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class BlockThrowTarget {
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();
  if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui)return;
  var selected=com.codex.blacklight.client.ClientTerrainGrab.preview();
  if(!selected.isEmpty()){
   var stack=e.getPoseStack();var camera=e.getCamera().getPosition();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
   for(var pos:selected)LevelRenderer.renderLineBox(stack,lines,new net.minecraft.world.phys.AABB(pos).inflate(.006),.9F,.12F,.12F,.8F);buffers.endBatch(RenderType.lines());stack.popPose();
  }
  var held=com.codex.blacklight.combat.BlockThrowSystem.held(mc.player);if(held==null||held.aimTarget()<0)return;
  var target=mc.level.getEntity(held.aimTarget());if(target==null)return;
  var stack=e.getPoseStack();var camera=e.getCamera().getPosition();stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
  var buffers=mc.renderBuffers().bufferSource();LevelRenderer.renderLineBox(stack,buffers.getBuffer(RenderType.lines()),target.getBoundingBox().inflate(.06),1,.13F,.1F,.9F);buffers.endBatch(RenderType.lines());stack.popPose();
 }
}
