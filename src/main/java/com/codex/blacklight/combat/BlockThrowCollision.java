package com.codex.blacklight.combat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
/** Sweeps the cargo volume, so a 3-wide fragment cannot pass through a 1-wide gap. */
public final class BlockThrowCollision {
 private BlockThrowCollision(){}
 /** The location is the cargo centre at first contact; contact.point is the
  * actual receiving surface. Retain the region through the damage callback. */
 public static final class AnimatedHit extends EntityHitResult {
  private final HydraHurtContact.Contact contact;
  public AnimatedHit(Entity target,Vec3 center,HydraHurtContact.Contact contact){super(target,center);this.contact=contact;}
  public HydraHurtContact.Contact contact(){return contact;}
 }
 public static boolean hurt(EntityHitResult hit,net.minecraft.world.damagesource.DamageSource source,float amount){
  var body=AnimatedHurtBody.of(hit.getEntity());
  return hit instanceof AnimatedHit posed&&body!=null?body.hurtAt(source,amount,posed.contact()):hit.getEntity().hurt(source,amount);
 }
 /** Never let a sweep query synchronously load a missing neighbouring chunk. */
 public static boolean loaded(Level level,Vec3 a,Vec3 b,Vec3 half){
  var box=new AABB(a,b).inflate(half.x+1,half.y,half.z+1);
  int minX=net.minecraft.util.Mth.floor(box.minX)>>4,maxX=net.minecraft.util.Mth.floor(box.maxX)>>4;
  int minZ=net.minecraft.util.Mth.floor(box.minZ)>>4,maxZ=net.minecraft.util.Mth.floor(box.maxZ)>>4;
  for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
   if(level instanceof net.minecraft.server.level.ServerLevel server){if(server.getChunkSource().getChunkNow(x,z)==null)return false;}
   else if(!level.hasChunk(x,z))return false;
  }
  return true;
 }
 public static Vec3 block(Level level,Entity owner,Vec3 a,Vec3 b,Vec3 half){
  if(!loaded(level,a,b,half))return a;
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
  var query=new AABB(a,b).inflate(half.x+.000001,half.y+.000001,half.z+.000001);
  for(var e:CombatTargeting.candidates(owner,query,target->allowed.test(target)||allowedPart(target,allowed))){
   var body=AnimatedHurtBody.of(e);
   if(body!=null){
    var spider=e instanceof com.codex.blacklight.entity.EncounterMob mother && mother.spider.feeding()?mother:null;
    var throat=spider!=null && owner instanceof net.minecraft.world.entity.Mob cargo && SpiderPhase.evolved(cargo) && cargo.getPersistentData().getBoolean(SpiderPhase.TUMOR)
        ?spider.spider.aperture(a,b,half,0):null;
    if(throat!=null && throat.fraction()<=1 && a.distanceToSqr(throat.center())<distance){
        distance=a.distanceToSqr(throat.center());
        best=new AnimatedHit(e,throat.center(),new HydraHurtContact.Contact("mouth",throat.center(),throat.normal(),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
    }
    for(var region:body.hurtRegions(0))for(var box:region.boxes()){
     if(!query.intersects(box.bounds.inflate(.000001)))continue;
     var hit=box.sweepBox(a,b,half);if(hit==null)continue;double d=a.distanceToSqr(hit.center());
     if(throat!=null && (region.bone().equals("rim")||region.bone().equals("mouth")||region.bone().equals("shell")||region.bone().startsWith("feed_")) && hit.center().distanceToSqr(throat.center())<3*3)continue;
     if(d<distance-1e-9){best=new AnimatedHit(e,hit.center(),new HydraHurtContact.Contact(region.bone(),hit.point(),hit.normal(),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));distance=d;}
    }
   }else for(var shape:CombatTargeting.hitboxes(e)){
    var box=shape.inflate(half.x,half.y,half.z);var hit=box.contains(a)?java.util.Optional.of(a):box.clip(a,b);
    if(hit.isPresent()&&a.distanceToSqr(hit.get())<distance){best=new EntityHitResult(e,hit.get());distance=a.distanceToSqr(hit.get());}
   }
  }
  for(var craft:level.getEntitiesOfClass(com.codex.blacklight.entity.BlackwatchHelicopter.class,query,allowed::test)){
   for(var box:com.codex.blacklight.military.aircraft.HelicopterSkinContact.boxes(craft)){
    if(!query.intersects(box.bounds))continue;
    var hit=box.sweepBox(a,b,half);
    if(hit==null)continue;
    double d=a.distanceToSqr(hit.center());
    if(d<distance){best=new EntityHitResult(craft,hit.center());distance=d;}
   }
  }
  return best;
 }
 private static boolean allowedPart(net.minecraft.world.entity.LivingEntity target,java.util.function.Predicate<Entity> allowed){
  if(AnimatedHurtBody.of(target)==null||!target.isMultipartEntity()||target.getParts()==null)return false;
  for(var part:target.getParts())if(part.isPickable()&&allowed.test(part))return true;return false;
 }
}
