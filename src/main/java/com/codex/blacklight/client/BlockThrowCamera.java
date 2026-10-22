package com.codex.blacklight.client;
import net.minecraft.client.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ViewportEvent;
/** Short shoulder offset exposes the ballistic aim guide; it never selects a camera mode. */

public final class BlockThrowCamera {
 private static double offset,back;private static long last;private static Object owner;
 public static void apply(net.minecraft.client.Camera camera,float partial){
  var mc=Minecraft.getInstance();var p=mc.player;long now=System.nanoTime();
  if(com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView()||p==null||mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK){offset=back=0;last=now;owner=p;return;}
  if(owner!=p){offset=back=0;owner=p;last=now;}
  double dt=last==0?0:Math.min(.1,(now-last)*1e-9);last=now;
  var cargo=com.codex.blacklight.combat.BlockThrowSystem.held(p);boolean held=cargo!=null;double distance=held?Math.max(1,cargo.cargo().half().x+.6):0;
  offset+=( distance-offset)*(1-Math.exp(-dt/.12));double backGoal=held?Math.max(0,Math.max(cargo.cargo().size().x,Math.max(cargo.cargo().size().y,cargo.cargo().size().z))-1)*.6:0;back+=(backGoal-back)*(1-Math.exp(-dt/.12));if(offset<.001&&back<.001)return;
  double angle=Math.toRadians(camera.getYRot());
  var wanted=camera.getPosition().add(-Math.cos(angle)*offset,.20*offset,-Math.sin(angle)*offset).add(Vec3.directionFromRotation(camera.getXRot(),camera.getYRot()).scale(-back));
  var eye=p.getEyePosition(partial);
  var hit=p.level().clip(new ClipContext(eye,wanted,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,p));
  if(hit.getType()!=HitResult.Type.MISS)wanted=eye.add(wanted.subtract(eye).normalize().scale(Math.max(0,eye.distanceTo(hit.getLocation())-.3)));
  ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)camera).blacklight$setPosition(wanted);
 }
}
