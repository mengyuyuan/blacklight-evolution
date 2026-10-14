package com.codex.blacklight.combat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
/** Server chooses exposed, reachable targets. Aim assist changes launch velocity only, never homes in flight. */
public final class BlockThrowAim {
 public record Result(Vec3 velocity,int target){}
 private BlockThrowAim(){}
 public static Result solve(ServerPlayer p,Vec3 origin,Vec3 aim,boolean assist,double speed){
  Vec3 direction=aim.subtract(p.getEyePosition()).normalize();
  if(direction.lengthSqr()<.5)direction=p.getLookAngle();
  if(assist){
   var locked=CombatTargeting.locked(p);var result=target(p,origin,locked,speed);
   if(result!=null)return result;
   final Vec3 view=direction;
   var candidates=p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(96),e->CombatTargeting.selectable(p,e)&&p.hasLineOfSight(e)
    &&e.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize().dot(view)>.9659);
   candidates.sort(java.util.Comparator.comparingDouble((LivingEntity e)->-view.dot(e.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize())*8+p.distanceTo(e)/96.0).thenComparingInt(LivingEntity::getId));
   for(int i=0;i<Math.min(8,candidates.size());i++){result=target(p,origin,candidates.get(i),speed);if(result!=null)return result;}
  }
  var shot=BlockThrowBallistics.solve(origin,aim,Vec3.ZERO,speed);
  return new Result(shot==null?direction.scale(speed):shot.velocity(),-1);
 }
 private static Result target(ServerPlayer p,Vec3 origin,LivingEntity target,double speed){
  if(!CombatTargeting.selectable(p,target)||!p.hasLineOfSight(target)||p.distanceToSqr(target)>96*96)return null;
  Vec3 point=CombatTargeting.lockPoint(p,target),motion=target.getDeltaMovement();
  var cargo=BlockThrowSystem.held(p);if(cargo!=null)point=new Vec3(point.x,Math.max(point.y,target.getBoundingBox().minY+cargo.cargo().half().y+.1),point.z);
  if(target.onGround())motion=new Vec3(motion.x,0,motion.z);
  var shot=BlockThrowBallistics.solve(origin,point,motion,speed);
  if(shot==null||!clear(p,origin,shot))return null;
  return new Result(shot.velocity(),target.getId());
 }
 static boolean clear(ServerPlayer p,Vec3 origin,BlockThrowBallistics.Shot shot){
  Vec3 a=origin;var held=BlockThrowSystem.held(p);Vec3 half=held==null?new Vec3(.45,.45,.45):held.cargo().half();
  for(int t=1;t<=Math.ceil(shot.ticks());t++){
   Vec3 b=BlockThrowBallistics.at(origin,shot.velocity(),Math.min(t,shot.ticks()));
   if(p.level().getFluidState(net.minecraft.core.BlockPos.containing(b)).is(net.minecraft.tags.FluidTags.WATER)||!p.level().hasChunkAt(net.minecraft.core.BlockPos.containing(b))||BlockThrowCollision.block(p.level(),p,a,b,half)!=null)return false;
   a=b;
  }
  return true;
 }
}
