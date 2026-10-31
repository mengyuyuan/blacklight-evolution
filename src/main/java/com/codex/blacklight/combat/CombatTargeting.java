package com.codex.blacklight.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.player.Player;
import com.codex.blacklight.registry.ModAttachments;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import net.neoforged.neoforge.entity.PartEntity;

/** Selection is independent of camera pitch when the player explicitly locks a target. */
public final class CombatTargeting {
    private static final Map<ServerPlayer, Entity> LOCKS = new WeakHashMap<>();
    private CombatTargeting() {}
    /** Search loaded, visible actors. This does not enlarge a weapon's contact volume. */
    public static double viewRange(ServerPlayer player) {
        return Math.clamp(player.serverLevel().getServer().getPlayerList().getViewDistance()*16.0,32,256);
    }
    public static boolean inFrontView(ServerPlayer player,LivingEntity target) {
        return !target.isInvisibleTo(player)&&aimScore(player,target)>.5;
    }
    public static LivingEntity locked(ServerPlayer player) {
        return lockedEntity(player) instanceof LivingEntity living?living:null;
    }
    public static Entity lockedEntity(ServerPlayer player) {
        var target=LOCKS.get(player);
        if (!ExplicitTarget.enabled(player)
                || !ExplicitTarget.selectable(player,target) || !(ExplicitTarget.distanceSquared(player,target)<=viewRange(player)*viewRange(player)
                || target instanceof LivingEntity&&BulletdiveSteering.retainAltitudeLock(player.position(),player.onGround(),target.position(),viewRange(player)))) {
            LOCKS.remove(player); syncLock(player, -1); return null;
        }
        return target;
    }
    /** Explicit camera lock only; ordinary crosshair focus is never authoritative. */
    public static void requestLock(ServerPlayer player, int entityId) {
        if(entityId<0){ConsumeSystem.releaseUncommitted(player,true);LOCKS.remove(player);syncLock(player,-1);return;}
        var entity=ExplicitTarget.resolve(player.serverLevel().getEntity(entityId));
        if(ExplicitTarget.enabled(player)
                && entity != null && ExplicitTarget.selectable(player,entity)
                && ExplicitTarget.visibleWithinRange(player,entity,viewRange(player))) {
            LOCKS.put(player,entity);syncLock(player,entity.getId());
        } else {LOCKS.remove(player);syncLock(player,-1);}
    }
    private static void syncLock(ServerPlayer player,int id) {
        if(player.getData(ModAttachments.TARGET_LOCK_ID)==id)return;
        player.setData(ModAttachments.TARGET_LOCK_ID,id);
        if(!player.isRemoved())player.syncData(ModAttachments.TARGET_LOCK_ID);
    }
    public static boolean damageable(Player player, LivingEntity target) {
        return damageableFrom(player,target,player);
    }
    /** Persistent owned attacks resolve in their origin's world even after the owner travels. */
    public static boolean damageableFrom(Player player, LivingEntity target, Entity origin) {
        return target!=null && target!=player && target.level()==origin.level()
                && target.isAlive() && !target.isRemoved() && !target.isSpectator() && !target.isInvulnerable()
                && !player.isAlliedTo(target) && !target.getData(ModAttachments.CONSUME_VISUAL).active()
                && (!(target instanceof Player other) || !other.getAbilities().invulnerable && player.canHarmPlayer(other)
                    && (!(player instanceof ServerPlayer server) || server.server.isPvpAllowed()));
    }
    public static boolean selectable(Player player, LivingEntity target) {
        return damageable(player,target) && !target.isInvisibleTo(player);
    }
    public static java.util.Comparator<LivingEntity> contactPriority(ServerPlayer player) {
        var target=locked(player);
        return java.util.Comparator.comparingInt((LivingEntity v)->v==target?0:1)
                .thenComparingDouble(v->distanceSquared(player.getEyePosition(),v)).thenComparingInt(LivingEntity::getId);
    }
    public static LivingEntity resolveTarget(Entity entity) {
        if(entity instanceof PartEntity<?> part)entity=part.getParent();
        return entity instanceof LivingEntity living?living:null;
    }
    public static boolean multipart(LivingEntity target) {
        return target instanceof BlacklightInfectedHost host&&host.hasMultipartBody()
                || target.isMultipartEntity() && target.getParts()!=null && target.getParts().length>0;
    }
    public static List<AABB> hitboxes(LivingEntity target) {
        if(target instanceof BlacklightInfectedHost host&&host.hasMultipartBody())return host.combatHitboxes();
        if(target.isMultipartEntity() && target.getParts()!=null) {
            var boxes=new ArrayList<AABB>();
            for(var part:target.getParts())if(part!=null&&!part.isRemoved()&&part.isPickable()) {
                var box=part.getBoundingBox();
                if(box.getXsize()>0&&box.getYsize()>0&&box.getZsize()>0)boxes.add(box);
            }
            if(!boxes.isEmpty())return boxes;
        }
        return List.of(target.getBoundingBox());
    }
    /** Broad phase finds loaded parent entities; the original query must still
     * intersect a real limb. The large search margin never becomes weapon reach. */
    public static List<LivingEntity> candidates(Player player,AABB query,Predicate<LivingEntity> predicate) {
        var result=new LinkedHashSet<LivingEntity>();
        // Uncapped dive energy can span thousands of chunks. Walking the spatial
        // index's coordinate range scales with empty space. Visit loaded actors
        // once instead, retaining the same real hitbox/predicate contract.
        if(player.level() instanceof net.minecraft.server.level.ServerLevel level
                && Math.max(query.getXsize(),Math.max(query.getYsize(),query.getZsize()))>512) {
            for(var entity:level.getAllEntities())
                if(entity instanceof LivingEntity target && hitboxes(target).stream().anyMatch(query::intersects)
                        && predicate.test(target))result.add(target);
            return new ArrayList<>(result);
        }
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,query,predicate))
            if(!multipart(target)||hitboxes(target).stream().anyMatch(query::intersects))result.add(target);
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,query.inflate(80),
                host->multipart(host)&&predicate.test(host)))
            if(hitboxes(target).stream().anyMatch(query::intersects))result.add(target);
        return new ArrayList<>(result);
    }
    public static Vec3 closestPoint(Vec3 point,AABB box) {
        return new Vec3(Math.clamp(point.x,box.minX,box.maxX),Math.clamp(point.y,box.minY,box.maxY),
                Math.clamp(point.z,box.minZ,box.maxZ));
    }
    public static Vec3 closestPoint(Vec3 point,LivingEntity target) {
        Vec3 result=target.getBoundingBox().getCenter();double best=Double.POSITIVE_INFINITY;
        for(var box:hitboxes(target)) {
            var candidate=closestPoint(point,box);double distance=point.distanceToSqr(candidate);
            if(distance<best){best=distance;result=candidate;}
        }
        return result;
    }
    /** Pursuit retains ordinary foot destinations, but approaches a boss's local
     * surface instead of dragging the attacker through it toward the root. */
    public static Vec3 positionNear(Vec3 point,LivingEntity target) {
        return multipart(target)?closestPoint(point,target):target.position();
    }
    public static double distanceSquared(Vec3 point,LivingEntity target) {
        double distance=Double.POSITIVE_INFINITY;
        for(var box:hitboxes(target))distance=Math.min(distance,distanceSquared(point,box));
        return distance;
    }
    public static double distanceSquared(Vec3 point,AABB box) {
        double x=Math.clamp(point.x,box.minX,box.maxX),y=Math.clamp(point.y,box.minY,box.maxY),z=Math.clamp(point.z,box.minZ,box.maxZ);
        return point.distanceToSqr(new Vec3(x,y,z));
    }
    public static boolean inReach(ServerPlayer player,LivingEntity target,double range) {
        return target!=null&&player.level()==target.level()&&distanceSquared(player.getEyePosition(),target)<=range*range+1e-6;
    }
    public static boolean visible(Player player,Vec3 from,AABB box) {
        return clearRay(player,from,closestPoint(from,box));
    }
    public static boolean visibleWithinRange(Player player,LivingEntity target,double range) {
        if(target==null||target.level()!=player.level()||range<0)return false;
        Vec3 eye=player.getEyePosition();double limit=range*range+1e-6;
        if(!multipart(target))return distanceSquared(eye,target)<=limit&&visible(player,target);
        // The exposed part must itself be in range: a hidden nearby foot must
        // not borrow visibility from an out-of-range wing on the other side.
        for(AABB box:hitboxes(target))if(distanceSquared(eye,box)<=limit){
            Vec3 point=visiblePoint(player,eye,box);
            if(point!=null&&eye.distanceToSqr(point)<=limit)return true;
        }
        return false;
    }
    /** A root column can be underground or between legs while a limb is visible.
     * Probe only points on the actual damage box, never its enclosing empty space. */
    private static Vec3 visiblePoint(Player player,Vec3 from,AABB box) {
        Vec3 nearest=closestPoint(from,box);
        if(clearRay(player,from,nearest))return nearest;
        Vec3 centre=box.getCenter();
        Vec3[] points={centre,new Vec3(box.minX,centre.y,centre.z),new Vec3(box.maxX,centre.y,centre.z),
                new Vec3(centre.x,box.minY,centre.z),new Vec3(centre.x,box.maxY,centre.z),
                new Vec3(centre.x,centre.y,box.minZ),new Vec3(centre.x,centre.y,box.maxZ)};
        Vec3 result=null;double best=Double.POSITIVE_INFINITY;
        for(Vec3 point:points){double distance=from.distanceToSqr(point);
            if(distance<best&&clearRay(player,from,point)){best=distance;result=point;}}
        return result;
    }
    /** Stable focus on the closest exposed body surface. Ordinary mob framing is unchanged. */
    public static Vec3 lockPoint(Player player,LivingEntity target) {
        if(!multipart(target))return target.getBoundingBox().getCenter();
        Vec3 eye=player.getEyePosition(),result=null;double best=Double.POSITIVE_INFINITY;
        for(AABB box:hitboxes(target)){
            if(distanceSquared(eye,box)>=best)continue;
            Vec3 point=visiblePoint(player,eye,box);
            if(point!=null&&eye.distanceToSqr(point)<best){result=point;best=eye.distanceToSqr(point);}
        }
        return result==null?closestPoint(eye,target):result;
    }
    public static boolean visibleContact(Player player,LivingEntity target,AABB contact) {
        if(!multipart(target))return visible(player,target);
        for(var box:hitboxes(target))if(box.intersects(contact)&&visible(player,player.getEyePosition(),box))return true;
        return false;
    }
    public static boolean clearRay(Player player,Vec3 from,Vec3 to) {
        // Nothing can occupy block space above the dimension's build height.
        // Clip that empty prefix before DDA; a retained high-altitude lock must
        // not walk millions of air blocks on either logical side.
        double ceiling=player.level().getMaxBuildHeight();
        if(from.y>ceiling){
            if(to.y>=ceiling)return true;
            from=from.lerp(to,(from.y-ceiling)/(from.y-to.y));
        }
        net.minecraft.world.level.BlockGetter blocks=player.level();
        if(player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            // A visibility check must not synchronously generate chunks between
            // distant loaded players. Treat missing terrain as an opaque boundary.
            blocks=new net.minecraft.world.level.BlockGetter() {
                public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
                    var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                    return chunk==null?net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState():chunk.getBlockState(pos);
                }
                public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) {return getBlockState(pos).getFluidState();}
                public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) {
                    var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                    return chunk==null?null:chunk.getBlockEntity(pos);
                }
                public int getHeight(){return level.getHeight();}
                public int getMinBuildHeight(){return level.getMinBuildHeight();}
            };
        }
        return blocks.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }
    public static boolean visible(Player player,LivingEntity target) {
        if(target==null||target.level()!=player.level())return false;
        if(!multipart(target))return clearRay(player,player.getEyePosition(),target.getBoundingBox().getCenter());
        for(var box:hitboxes(target))if(visible(player,player.getEyePosition(),box))return true;
        return false;
    }
    public static double aimScore(ServerPlayer player,LivingEntity target) {
        if(!multipart(target))return player.getLookAngle().dot(target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize());
        double best=-1;var eye=player.getEyePosition();
        for(var box:hitboxes(target))if(visible(player,eye,box)) {
            var delta=closestPoint(eye,box).subtract(eye);
            best=Math.max(best,delta.lengthSqr()<1e-6?1:player.getLookAngle().dot(delta.normalize()));
        }
        return best;
    }
}
