package com.codex.blacklight.client;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.network.BlockThrowAimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import com.codex.blacklight.forge.PacketDistributor;
public final class ClientBlockThrowAim {
 private static int lastTick=-1,lastBlock=-1;
 private ClientBlockThrowAim(){}
 public static void update(boolean force){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null)return;
  var block=BlockThrowSystem.held(mc.player);if(block==null)return;
  if(!force&&lastBlock==block.getId()&&mc.player.tickCount-lastTick<2)return;
  lastBlock=block.getId();lastTick=mc.player.tickCount;
  var cam=mc.gameRenderer.getMainCamera();var start=cam.getPosition();var dir=Vec3.directionFromRotation(cam.getXRot(),cam.getYRot());
  var hit=mc.level.clip(new ClipContext(start,start.add(dir.scale(96)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
  var point=hit.getLocation();
  PacketDistributor.sendToServer(new BlockThrowAimPayload(point.x,point.y,point.z,!SkillBindings.down(mc.options.keyShift)));
 }
}
