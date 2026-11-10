package com.codex.blacklight.infection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Hard crowd limits around players and per chunk, plus slow trimming of idle ambient spawns far from everyone. */
public final class PopulationGuard {
    private PopulationGuard(){}
    public static final String AMBIENT="blacklight:outbreak_spawn";
    static boolean below(int count,int cap){return cap<=0||count<cap;}
    /** Only unnamed, idle natural births that no player is near may be removed. */
    static boolean trimmable(boolean ambient,boolean named,boolean hasTarget,boolean passenger,double nearestPlayerSq,int trimDistance){
        return ambient&&!named&&!hasTarget&&!passenger&&nearestPlayerSq>(double)trimDistance*trimDistance;
    }
    private static boolean infected(Mob mob){return mob.isAlive()&&BlacklightInfectionSystem.isInfected(mob);}
    public static boolean allows(ServerLevel level,net.minecraft.core.BlockPos pos){
        int chunkCap=OutbreakConfig.CHUNK_CAP.get(),crowdCap=OutbreakConfig.PLAYER_CROWD_CAP.get();
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        var chunk=new AABB(cx<<4,level.getMinBuildHeight(),cz<<4,(cx<<4)+16,level.getMaxBuildHeight(),(cz<<4)+16);
        if(!below(level.getEntitiesOfClass(Mob.class,chunk,PopulationGuard::infected).size(),chunkCap))return false;
        for(Player player:level.players()){
            if(player.isSpectator()||player.distanceToSqr(pos.getCenter())>64*64)continue;
            if(!below(level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(48),PopulationGuard::infected).size(),crowdCap))return false;
        }
        return true;
    }
    /** Every ten seconds, discard at most a few idle ambient births beyond the trim distance. */
    public static void trim(ServerLevel level){
        if(level.getGameTime()%200!=0||level.players().isEmpty())return;
        int distance=OutbreakConfig.TRIM_DISTANCE.get();if(distance<=0)return;
        int removed=0;
        for(var entity:level.getAllEntities()){
            if(removed>=16)break;
            if(!(entity instanceof Mob mob)||!infected(mob))continue;
            var nearest=level.getNearestPlayer(mob,-1);
            double sq=nearest==null?Double.MAX_VALUE:nearest.distanceToSqr(mob);
            if(trimmable(mob.getPersistentData().getBoolean(AMBIENT),mob.hasCustomName(),mob.getTarget()!=null,mob.isPassenger()||mob.isVehicle(),sq,distance)){
                mob.discard();removed++;
            }
        }
    }
}
