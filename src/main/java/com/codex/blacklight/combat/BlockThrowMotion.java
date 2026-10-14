package com.codex.blacklight.combat;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
/** Source-derived clocks and a shared grip frame; no independent cosmetic projectile position. */
public final class BlockThrowMotion {
 private BlockThrowMotion(){}
 public static String key(boolean two,String phase){return "block_throw_"+(two?"two_":"one_")+phase;}
 public static int pickupTicks(boolean two){return two?20:14;}
 public static int windupTicks(boolean two){return two?10:14;}
 public static int releaseTicks(boolean two){return two?1:2;}
 public static void play(ServerPlayer p,boolean two,String phase){
  String id=key(two,phase);var old=p.getData(ModAttachments.PLAYER_ACTION);
  float yaw=com.codex.blacklight.movement.TraversalSteering.cameraYaw(p);
  var state=new NativeActionState(id,p.level().getGameTime(),0,0,MotionLibrary.duration(id),yaw,phase.equals("hold"),-1,old.revision()+1);
  p.setData(ModAttachments.PLAYER_ACTION,state);p.syncData(ModAttachments.PLAYER_ACTION);
 }
 public static boolean active(Player p){var a=p.getData(ModAttachments.PLAYER_ACTION);return a.clipId().startsWith("block_throw_")&&(a.active(p.level().getGameTime())||BlockThrowSystem.held(p)!=null);}
 public static BlacklightPose pose(Player p,float partial){
  var a=p.getData(ModAttachments.PLAYER_ACTION);
  if(!a.clipId().startsWith("block_throw_"))return null;
  return MotionLibrary.pose(a.clipId(),a.seconds(p.level().getGameTime()+partial),a.looping());
 }
 public static float yaw(Player p,float partial){
  var a=p.getData(ModAttachments.PLAYER_ACTION);
  return a.clipId().endsWith("_release")?a.yaw():net.minecraft.util.Mth.rotLerp(partial,p.yRotO,p.getYRot());
 }
 public static Vector3f palm(BlacklightPose pose,String side){
  var sk=BlacklightSkeleton.get();return sk.getBoundTransformFor(pose,sk.searchJointByName("Tool_"+side)).transformPosition(new Vector3f(0,.06F,0));
 }
 public static Vector3f localCenter(BlacklightPose pose,boolean two){
  var right=palm(pose,"R");
  return two?right.add(palm(pose,"L")).mul(.5F).add(0,.16F,0):right.add(0,.03F,-.40F);
 }
 public static Vector3f gripCenter(Player p,BlacklightPose pose,boolean two){
  var center=localCenter(pose,two);var held=BlockThrowSystem.held(p);
  if(held!=null&&two){var half=held.cargo().half();center.add(0,(float)(Math.max(0,half.y-.45)+.55),(float)-(Math.max(0,half.z-.45)+.8));}
  return center;
 }
 public static Vec3 world(Player p,Vector3f point,float yaw,float partial){
  var v=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-yaw)).scale(.9375F).transformPosition(new Vector3f(point));
  return (p.level().isClientSide?p.getPosition(partial):p.position()).add(v.x,v.y,v.z);
 }
 public static Vec3 center(Player p,boolean two,float partial){
  var pose=pose(p,partial);if(pose==null)return p.getEyePosition();
  return world(p,gripCenter(p,pose,two),yaw(p,partial),partial);
 }
 public static Vec3 launchOrigin(Player p,boolean two){
  var pose=MotionLibrary.pose(key(two,"release"),releaseTicks(two)/20F,false);
  return world(p,gripCenter(p,pose,two),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p),1);
 }
 public static void clear(ServerPlayer p){if(p.getData(ModAttachments.PLAYER_ACTION).clipId().startsWith("block_throw_"))NativePlayerActionBackend.clear(p);}
}
