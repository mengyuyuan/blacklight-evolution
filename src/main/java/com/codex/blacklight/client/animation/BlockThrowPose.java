package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
/** Carry geometry and both hands sample the same authored source clock. */
public final class BlockThrowPose {
 private static final java.util.Map<Player,Blend> BLENDS=new java.util.WeakHashMap<>();
 private static final class Blend {String key="";long began;BlacklightPose from,last;}
 private BlockThrowPose(){}
 public static BlacklightPose sample(Player p,float partial){
  if(!BlockThrowMotion.active(p)){BLENDS.remove(p);return null;}
  var a=p.getData(ModAttachments.PLAYER_ACTION);var pose=BlockThrowMotion.pose(p,partial);if(pose==null)return null;
  var b=BLENDS.computeIfAbsent(p,v->new Blend());
  if(!b.key.equals(a.clipId())){b.from=b.last;b.began=p.level().getGameTime();b.key=a.clipId();}
  if(b.from!=null&&!a.clipId().endsWith("_release"))pose=BlacklightPose.interpolatePose(b.from,pose,Math.min(1,(p.level().getGameTime()-b.began+partial)/4F));
  b.last=pose.copy();
  var block=BlockThrowSystem.held(p);
  if(block!=null&&block.twoHanded()){
   var center=BlockThrowMotion.gripCenter(p,pose,true);var axis=BlockThrowMotion.palm(pose,"L").sub(BlockThrowMotion.palm(pose,"R"));
   if(axis.lengthSquared()>.001F){axis.set(-.46F,0,0);
    var sk=BlacklightSkeleton.get();
    for(String side:new String[]{"R","L"}){
     var goal=new Vector3f(center).add(new Vector3f(axis).mul(side.equals("L")?1:-1)).add(0,(float)-(block.cargo().half().y-.15),(float)(block.cargo().half().z+.05));
     BlacklightBoneIk.reach(sk,pose,side,goal,new Vector3f(0,.06F,0),8);
    }
   }
  }
  // Let the current gait take over progressively during the authored follow-through.
  if(a.clipId().endsWith("_release")){
   float end=Math.max(0,Math.min(1,(a.duration()-a.seconds(p.level().getGameTime()+partial))/.22F));
   if(end<1)pose=BlacklightPose.interpolatePose(MotionLibrary.pose(p.getDeltaMovement().horizontalDistance()>.02?"basic_run":"basic_idle",(p.tickCount+partial)/20F,true),pose,end);
  }
  return pose;
 }
 public static net.minecraft.world.phys.Vec3 center(Player p,boolean two,float partial){
  // The unmodified source grip is cached before the fitting IK changes wrist spacing.
  sample(p,partial);var b=BLENDS.get(p);var pose=b==null?BlockThrowMotion.pose(p,partial):b.last;
  return pose==null?p.getEyePosition():BlockThrowMotion.world(p,BlockThrowMotion.gripCenter(p,pose,two),BlockThrowMotion.yaw(p,partial),partial);
 }
}
