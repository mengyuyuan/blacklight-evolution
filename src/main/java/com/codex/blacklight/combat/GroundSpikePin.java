package com.codex.blacklight.combat;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The shaft is fixed to a collision face. A surviving mob can be held only at that shaft. */
final class GroundSpikePin {
    private record Hold(Vec3 root,Vec3 feet,long start,long release,java.util.UUID owner,long session) {}
    private static final Map<Mob,Hold> HELD=new IdentityHashMap<>();
    private GroundSpikePin() {}

    static Vec3 groundAt(ServerPlayer player,Vec3 feet) {
        var hit=ClawSpikeField.floor(player,feet);
        if(!ClawSpikeField.supported(hit))return null;
        double above=feet.y-hit.getLocation().y;
        return above>=-.1&&above<=1.8?hit.getLocation():null;
    }

    static boolean touches(LivingEntity target,Vec3 root) {
        if(root==null)return false;
        var shaft=new AABB(root.x-.24,root.y-.05,root.z-.24,root.x+.24,root.y+1.8,root.z+.24);
        return CombatTargeting.hitboxes(target).stream().anyMatch(shaft::intersects);
    }

    static boolean catchMob(LivingEntity target,Vec3 root,int ticks,float yaw) {
        return catchMob(target,root,ticks,yaw,null,0);
    }
    static boolean catchMob(LivingEntity target,Vec3 root,int ticks,float yaw,ServerPlayer owner,long session) {
        if(!(target instanceof Mob mob)||ticks<=0||!mob.isAlive()||mob.isRemoved()||mob.isPassenger()
                ||CombatTargeting.anchoredBody(mob)||!touches(mob,root))return false;
        if(held(mob))return true;
        if(!ImpactControl.begin(mob,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.UP,ticks,0,0,false),yaw))return false;
        long now=mob.level().getGameTime();
        HELD.put(mob,new Hold(root,mob.position(),now,now+ticks,owner==null?null:owner.getUUID(),session));
        mob.setJumping(false);mob.setDeltaMovement(Vec3.ZERO);mob.hurtMarked=true;
        return true;
    }

    static void releaseSession(ServerPlayer owner,long session) {
        for(var mob:java.util.List.copyOf(HELD.keySet())){
            var h=HELD.get(mob);
            if(h!=null&&owner.getUUID().equals(h.owner())&&session==h.session())ImpactControl.cancel(mob);
        }
    }
    static boolean held(LivingEntity entity){return entity instanceof Mob mob&&HELD.containsKey(mob);}
    static void forget(Mob mob){HELD.remove(mob);}

    private static boolean clearFor(Mob mob,Vec3 feet) {
        var body=mob.getBoundingBox().move(feet.subtract(mob.position()));
        return !mob.level().getBlockCollisions(mob,body).iterator().hasNext();
    }
    static boolean tick(Mob mob) {
        var hold=HELD.get(mob);
        if(hold==null)return false;
        long now=mob.level().getGameTime();
        // End for external teleports, dismount/capture transitions and loss of support.
        var root=hold.root();
        var support=new AABB(root.x-.01,root.y-.035,root.z-.01,root.x+.01,root.y-.005,root.z+.01);
        if(now>=hold.release()||mob.isPassenger()||mob.position().distanceToSqr(hold.feet())>4
                ||!mob.level().getBlockCollisions(mob,support).iterator().hasNext()){
            forget(mob);return false;
        }
        double lift=.34*Math.min(1,(now-hold.start())/3.0);
        Vec3 feet=hold.feet(),at=feet.add(0,lift,0);
        if(!clearFor(mob,at))at=feet;
        if(clearFor(mob,at)){
            mob.setPos(at.x,at.y,at.z);
        }else{
            forget(mob);return false;
        }
        mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);mob.setJumping(false);
        mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        return true;
    }
}
