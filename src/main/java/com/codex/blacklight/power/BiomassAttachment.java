package com.codex.blacklight.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Transient attachment to a physical multipart surface, not the boss's root or camera. */
public final class BiomassAttachment {
    private record Anchor(UUID victim, Entity part, Vec3 local) {}
    private static final Map<ServerPlayer,Anchor> ANCHORS=new WeakHashMap<>();
    private BiomassAttachment() {}
    public static void clear(ServerPlayer player){ANCHORS.remove(player);}

    public static boolean attach(ServerPlayer player,Mob target){
        if(!target.isMultipartEntity()||target.getParts()==null||target.getParts().length==0)return true;
        var contact=player.getBoundingBox().inflate(1.15,.65,1.15);
        Entity closest=null;double best=Double.POSITIVE_INFINITY;
        for(var part:target.getParts())if(part!=null&&!part.isRemoved()&&part.isPickable()
                &&contact.intersects(part.getBoundingBox())){
            var box=part.getBoundingBox();
            if(box.getXsize()<=0||box.getYsize()<=0||box.getZsize()<=0)continue;
            double distance=com.codex.blacklight.combat.CombatTargeting.distanceSquared(player.getEyePosition(),box);
            if(distance<best){best=distance;closest=part;}
        }
        if(closest==null)return false;
        var box=closest.getBoundingBox();var p=player.position();
        // Keep the contact offset, including a small gap outside the surface; attaching must not teleport inward.
        ANCHORS.put(player,new Anchor(target.getUUID(),closest,new Vec3(
                (p.x-box.minX)/box.getXsize(),(p.y-box.minY)/box.getYsize(),(p.z-box.minZ)/box.getZsize())));
        return true;
    }

    public static boolean attached(ServerPlayer player,Mob target){
        var anchor=ANCHORS.get(player);
        return anchor!=null&&anchor.victim().equals(target.getUUID())&&target.level()==player.level()
                &&!anchor.part().isRemoved()&&anchor.part().isPickable()
                &&com.codex.blacklight.combat.CombatTargeting.distanceSquared(player.position(),anchor.part().getBoundingBox())<=256;
    }

    public static boolean follow(ServerPlayer player,Mob target){
        if(!target.isAlive()||target.isRemoved()||target.level()!=player.level())return false;
        if(!target.isMultipartEntity()||target.getParts()==null||target.getParts().length==0)return true;
        if(!attached(player,target)&&!attach(player,target))return false;
        var anchor=ANCHORS.get(player);var box=anchor.part().getBoundingBox();var local=anchor.local();
        var at=new Vec3(box.minX+local.x*box.getXsize(),box.minY+local.y*box.getYsize(),box.minZ+local.z*box.getZsize());
        var delta=at.subtract(player.position());double distance=delta.length();
        if(!Double.isFinite(distance)||distance>16)return false;
        if(distance<1e-5)return true;
        int steps=Math.max(1,(int)Math.ceil(distance));
        for(int i=0;i<=steps;i++)if(!player.serverLevel().hasChunkAt(BlockPos.containing(player.position().lerp(at,i/(double)steps))))return false;
        // Check the swept body against terrain, not the creature we intentionally cover.
        if(player.level().getBlockCollisions(player,player.getBoundingBox().expandTowards(delta)).iterator().hasNext())return false;
        player.teleportTo(at.x,at.y,at.z);player.setDeltaMovement(Vec3.ZERO);
        return true;
    }
}
