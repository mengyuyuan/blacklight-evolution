package com.codex.blacklight.military.aircraft;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** ServerLevel adaptation of the one native active-level coordination manager.
 * Callers own tactic/track start and end; this class never starts flight or loads terrain. */
final class HelicopterAirspace {
    private static final Map<ServerLevel,HelicopterAirspace> WORLDS=new WeakHashMap<>();
    private final WeakReference<ServerLevel> world;
    private final HelicopterAttackLease token=new HelicopterAttackLease();
    private final HelicopterReservations reservations=new HelicopterReservations();
    private final Map<UUID,HelicopterStrafeCache> caches=new HashMap<>();
    private long lastTick=Long.MIN_VALUE;
    private HelicopterAirspace(ServerLevel level){world=new WeakReference<>(level);}
    static HelicopterAirspace forLevel(ServerLevel level){var value=WORLDS.computeIfAbsent(Objects.requireNonNull(level),HelicopterAirspace::new);value.sync();return value;}

    private boolean exists(UUID owner){var level=world.get();if(level==null)return false;var entity=level.getEntity(owner);return entity!=null&&!entity.isRemoved();}
    private boolean valid(Entity actor){return actor!=null&&actor.level()==world.get()&&!actor.isRemoved();}
    private void sync(){
        var level=world.get();if(level==null)return;long now=level.getGameTime();if(lastTick==now)return;
        if(lastTick!=Long.MIN_VALUE&&now>lastTick){
            long ticks=now-lastTick;
            // Ordinary ticks retain source float subtraction and strict-negative
            // expiry. All possible0.5/2-second leases expire within41 ticks.
            for(long i=0;i<Math.min(ticks,41);i++)token.advance(.05F);
            float seconds=ticks*.05F;for(var cache:caches.values())cache.advance(seconds);
        }
        lastTick=now;caches.keySet().removeIf(id->!exists(id));reservations.discardMissing(this::exists);
    }
    boolean tokenAvailable(Entity craft){sync();return valid(craft)&&token.available(craft.getUUID(),this::exists);}
    boolean requestToken(Entity craft){sync();return valid(craft)&&token.request(craft.getUUID(),this::exists);}
    boolean refreshToken(Entity craft){sync();return valid(craft)&&token.refresh(craft.getUUID(),this::exists);}
    private HelicopterStrafeCache cache(Entity craft){return caches.computeIfAbsent(craft.getUUID(),id->new HelicopterStrafeCache());}
    HelicopterStrafeCache.Snapshot query(Entity craft,Vec3 target,float maxAge,float maxMovement,int minimum,int maximum,boolean farthest){
        sync();return valid(craft)?cache(craft).query(loadedGrid(HelicopterFlight.finite(target)?target:craft.position()),craft.position(),target,maxAge,maxMovement,minimum,maximum,farthest):null;
    }
    HelicopterStrafeCache.Snapshot candidate(Entity craft){sync();return valid(craft)?cache(craft).candidate():null;}
    HelicopterStrafeCache.Snapshot commit(Entity craft){sync();return valid(craft)?cache(craft).commit():null;}
    HelicopterStrafeCache.Snapshot committed(Entity craft){sync();return valid(craft)?cache(craft).committed():null;}
    boolean reserveStrafe(Entity craft,HelicopterStrafeCache.Snapshot candidate,float radius){sync();return valid(craft)&&reservations.reserveStrafe(craft.getUUID(),candidate,radius);}
    boolean reservePosition(Entity craft,Vec3 position,float radius){sync();return valid(craft)&&reservations.reservePosition(craft.getUUID(),position,radius);}
    boolean reserveOpen(Entity craft,HelicopterOpenStrafe.Run run){
        sync();return valid(craft)&&run!=null&&run.stage()!=HelicopterOpenStrafe.Run.Stage.ABORTED
                &&reservations.reserveOpen(craft.getUUID(),run.reservationStart(),run.route().aim(),run.route().end(),5,8);
    }
    /** Actual ReserveVolume track refresh. This maintains the source candidate
     * corridor, not the separately committed/height-raised maneuver geometry.
     * A nonstrafe track takes an available navigation goal, else the aircraft position. */
    boolean reserveSource(Entity craft,Vec3 target,boolean strafe,float radius,Vec3 moveGoal){
        if(!valid(craft))return false;
        if(strafe)return reserveStrafe(craft,query(craft,target,Float.MAX_VALUE,Float.MAX_VALUE,-1,-1,false),radius);
        return reservePosition(craft,HelicopterFlight.finite(moveGoal)?moveGoal:craft.position(),radius);
    }
    void releaseVolume(Entity craft){if(craft!=null)reservations.release(craft.getUUID());}
    void clearCache(Entity craft){if(craft!=null)caches.remove(craft.getUUID());}
    /** Capture, destruction, action interruption or world departure; token expires naturally. */
    void forget(Entity craft){releaseVolume(craft);clearCache(craft);}
    boolean pointAvailable(Vec3 point,float queryRadius,Entity exceptOwner){sync();return reservations.pointAvailable(point,queryRadius,exceptOwner==null?null:exceptOwner.getUUID());}

    /** One Minecraft block per source grid cell is an explicit unit adaptation.
     * A local positive index avoids native truncation's negative-coordinate bias. */
    HelicopterStrafePath.Grid loadedGrid(Vec3 center){
        if(!HelicopterFlight.finite(center))return null;
        final float x=Mth.floor(center.x)-160+.5F,z=Mth.floor(center.z)-160+.5F;
        return new HelicopterStrafePath.Grid(){
            public float cellSize(){return 1;}
            public float originX(){return x;}
            public float originZ(){return z;}
            public float height(int ix,int iz){return groundAt(x+ix,z+iz);}
        };
    }
    float groundAt(Vec3 point){return HelicopterFlight.finite(point)?groundAt(point.x,point.z):Float.NaN;}
    private float groundAt(double x,double z){
        var level=world.get();if(level==null||!Double.isFinite(x)||!Double.isFinite(z)||x< -30000000||x>=30000000||z< -30000000||z>=30000000)return Float.NaN;
        int bx=Mth.floor(x),bz=Mth.floor(z);
        if(!level.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(bx,level.getMinBuildHeight(),bz)))return Float.NaN;
        var chunk=level.getChunkSource().getChunkNow(bx>>4,bz>>4);if(chunk==null)return Float.NaN;
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,bx&15,bz&15)+1;
    }
    /** Source101e1190 rounds width upward to an odd sample count. Unknown cells
     * fail closed withNaN; getChunkNow never creates or waits for a chunk. */
    float areaHeight(Vec3 center,float worldWidth){
        if(!HelicopterFlight.finite(center)||!Float.isFinite(worldWidth)||worldWidth<0||worldWidth>257)return Float.NaN;
        int width=Math.max(1,(int)Math.ceil(worldWidth));if((width&1)==0)width++;int radius=width/2;float top=-Float.MAX_VALUE;
        int x=Mth.floor(center.x),z=Mth.floor(center.z);
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            float height=groundAt((double)x+dx,(double)z+dz);if(!Float.isFinite(height))return Float.NaN;top=Math.max(top,height);
        }
        return top;
    }
}
