package com.codex.blacklight.combat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
/** Sweeps the cargo volume, so a 3-wide fragment cannot pass through a 1-wide gap. */
public final class BlockThrowCollision {
 private BlockThrowCollision(){}
 public static Vec3 block(Level level,Entity owner,Vec3 a,Vec3 b,Vec3 half){
  Vec3 best=null;double distance=Double.POSITIVE_INFINITY;
  var swept=new AABB(a,b).inflate(half.x,half.y,half.z);
  for(var shape:level.getBlockCollisions(owner,swept))for(var box:shape.toAabbs()){
   var expanded=box.inflate(Math.max(0,half.x-.015),Math.max(0,half.y-.015),Math.max(0,half.z-.015));
   var hit=expanded.contains(a)?java.util.Optional.of(a):expanded.clip(a,b);
   if(hit.isPresent()&&a.distanceToSqr(hit.get())<distance){best=hit.get();distance=a.distanceToSqr(best);}
  }
  return best;
 }
 public static EntityHitResult entity(Level level,Entity owner,Vec3 a,Vec3 b,Vec3 half,java.util.function.Predicate<Entity> allowed){
  EntityHitResult best=null;double distance=Double.POSITIVE_INFINITY;
  for(var e:level.getEntities(owner,new AABB(a,b).inflate(half.x+.3,half.y+.3,half.z+.3),allowed)){
   var box=e.getBoundingBox().inflate(half.x,half.y,half.z);var hit=box.contains(a)?java.util.Optional.of(a):box.clip(a,b);
   if(hit.isPresent()&&a.distanceToSqr(hit.get())<distance){best=new EntityHitResult(e,hit.get());distance=a.distanceToSqr(hit.get());}
  }
  return best;
 }
}
