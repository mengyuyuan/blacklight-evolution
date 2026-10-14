package com.codex.blacklight.combat;
import net.minecraft.world.phys.Vec3;
/** The projectile, prediction and intercept solver share this discrete drag/gravity law. */
public final class BlockThrowBallistics {
 public static final double DRAG=.99,GRAVITY=.045;
 public static final int LIFE=100;
 private BlockThrowBallistics(){}
 public static Vec3 nextVelocity(Vec3 v,boolean water){return v.scale(water?.8:DRAG).add(0,-GRAVITY,0);}
 public static Vec3 at(Vec3 origin,Vec3 velocity,double ticks){
  double sum=(1-Math.pow(DRAG,ticks))/(1-DRAG);
  return origin.add(velocity.scale(sum)).add(0,-GRAVITY/(1-DRAG)*(ticks-sum),0);
 }
 private static Vec3 required(Vec3 from,Vec3 target,Vec3 motion,double ticks){
  double sum=(1-Math.pow(DRAG,ticks))/(1-DRAG);
  return target.add(motion.scale(ticks)).subtract(from).add(0,GRAVITY/(1-DRAG)*(ticks-sum),0).scale(1/sum);
 }
 public record Shot(Vec3 velocity,double ticks){}
 /** First positive root gives the low arc. Returns null when range/vertical displacement is impossible. */
 public static Shot solve(Vec3 from,Vec3 target,Vec3 motion,double speed){
  if(!finite(from)||!finite(target)||!finite(motion)||!Double.isFinite(speed)||speed<=0)return null;
  if(motion.length()>1)motion=motion.normalize();
  double previous=.01;
  for(double t=.25;t<=LIFE;t+=.25){
   if(required(from,target,motion,t).length()<=speed){
    double lo=previous,hi=t;
    for(int i=0;i<24;i++){double mid=(lo+hi)/2;if(required(from,target,motion,mid).length()>speed)lo=mid;else hi=mid;}
    return new Shot(required(from,target,motion,hi),hi);
   }
   previous=t;
  }
  return null;
 }
 public static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
