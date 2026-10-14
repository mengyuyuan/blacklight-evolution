package com.codex.blacklight.combat;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.core.BlockPos;import net.minecraft.world.phys.*;import net.minecraft.world.level.ClipContext;
import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.tick.PlayerTickEvent;import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="blacklight")
public final class TerrainGrabSelection {
 private static final java.util.Map<ServerPlayer,Selection> ACTIVE=new java.util.WeakHashMap<>();
 private static final class Selection{final long started,seed;final BlockPos center;long heartbeat;Selection(ServerPlayer p,BlockPos pos){started=heartbeat=p.level().getGameTime();seed=p.getRandom().nextLong();center=pos;}}
 public static boolean start(ServerPlayer p){
  if(!BlockThrowSystem.canUse(p)||BlockThrowSystem.held(p)!=null)return false;if(ACTIVE.containsKey(p))return true;
  var hit=pick(p);
  if(hit.getType()!=HitResult.Type.BLOCK||!BlockThrowSystem.selectable(p,hit.getBlockPos()))return false;
  var s=new Selection(p,hit.getBlockPos());ACTIVE.put(p,s);preview(p,s);return true;
 }
 private static BlockHitResult pick(ServerPlayer p){
  var a=p.getEyePosition();return p.level().clip(new ClipContext(a,a.add(Vec3.directionFromRotation(p.getXRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p)).scale(3.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
 }
 /** Report the terrain rejection instead of claiming there was no creature target. */
 public static String failureMessage(ServerPlayer p){
  return pick(p).getType()==HitResult.Type.BLOCK?"terrain_grab_unavailable":"terrain_grab_out_of_reach";
 }
 public static void keepAlive(ServerPlayer p){var s=ACTIVE.get(p);if(s!=null)s.heartbeat=p.level().getGameTime();}
 public static void cancel(ServerPlayer p){if(ACTIVE.remove(p)!=null)PacketDistributor.sendToPlayer(p,new com.codex.blacklight.network.TerrainGrabPreviewPayload(java.util.List.of()));}
 public static void release(ServerPlayer p){var s=ACTIVE.get(p);if(s==null)return;cancel(p);if(p.level().getGameTime()-s.heartbeat<=12&&valid(p,s))BlockThrowSystem.request(p,TerrainGrabShape.count((int)(p.level().getGameTime()-s.started),BlockThrowSystem.level(p)),s.seed,s.center);}
 private static boolean valid(ServerPlayer p,Selection s){return BlockThrowSystem.canUse(p)&&BlockThrowSystem.held(p)==null&&p.getEyePosition().distanceToSqr(Vec3.atCenterOf(s.center))<=25&&BlockThrowSystem.selectable(p,s.center);}
 private static void preview(ServerPlayer p,Selection s){PacketDistributor.sendToPlayer(p,new com.codex.blacklight.network.TerrainGrabPreviewPayload(TerrainGrabShape.grow(s.center,TerrainGrabShape.count((int)(p.level().getGameTime()-s.started),BlockThrowSystem.level(p)),s.seed,at->BlockThrowSystem.selectable(p,at))));}
 @SubscribeEvent public static void tick(PlayerTickEvent.Post e){if(!(e.getEntity() instanceof ServerPlayer p))return;var s=ACTIVE.get(p);if(s==null)return;long now=p.level().getGameTime();if(now-s.heartbeat>12||!valid(p,s)){cancel(p);return;}if(now%3==0)preview(p,s);}
}
