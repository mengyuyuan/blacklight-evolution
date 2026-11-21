package com.codex.blacklight.infection;

import com.codex.blacklight.city.CityEcology;
import com.codex.blacklight.city.DistrictThreat;
import com.codex.blacklight.city.MilitaryCampaignSystem;
import com.codex.blacklight.compat.NaturalInfectionProtection;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.MilitaryUav;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Open-air births share the campaign's loaded chunks and scheduled population limits. */
public final class AirPopulation {
    private AirPopulation() {}

    public static boolean openSky(ServerLevel level,BlockPos point){
        return level.hasChunkAt(point) && level.getHeight(Heightmap.Types.MOTION_BLOCKING,point.getX(),point.getZ())<=point.getY();
    }

    public static boolean placeInOpenAir(ServerLevel level, Mob mob, BlockPos point) {
        mob.setPos(point.getX()+.5, point.getY(), point.getZ()+.5);
        var space=mob.getBoundingBox().inflate(.75, .5, .75);
        if (space.minY<level.getMinBuildHeight() || space.maxY>=level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(space) || !level.isPositionEntityTicking(point)) return false;
        for (int x=Mth.floor(space.minX)>>4; x<=Mth.floor(space.maxX)>>4; x++)
            for (int z=Mth.floor(space.minZ)>>4; z<=Mth.floor(space.maxZ)>>4; z++)
                if (level.getChunkSource().getChunkNow(x,z)==null) return false;
        return openSky(level,point) && !level.containsAnyLiquid(space)
                && level.noCollision(mob,space) && level.getEntities(mob,space).isEmpty();
    }

    static boolean wantsFlyer(int stage,int roll) {
        int chance=stage<=0?0:stage==1?10:stage==2?20:30;
        return roll<chance;
    }

    static boolean spawnFlyer(ServerLevel level,BlockPos surface) {
        if (!level.hasChunkAt(surface)
                || level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,surface.getX(),surface.getZ())>surface.getY()
                || !NaturalInfectionProtection.allowed(level,surface)
                || WorldOutbreakSystem.sanctuary(level,surface)
                || level.getEntitiesOfClass(EncounterMob.class,new AABB(surface).inflate(80),
                   mob->mob.isAlive() && mob.kind()==EncounterKind.FLYER).size()>=2) return false;
        var flyer=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(level);
        int canopy=level.getHeight(Heightmap.Types.MOTION_BLOCKING,surface.getX(),surface.getZ());
        var point=new BlockPos(surface.getX(),Math.max(surface.getY(),canopy)+5+level.random.nextInt(5),surface.getZ());
        if (flyer==null || !placeInOpenAir(level,flyer,point)) return false;
        flyer.getPersistentData().putBoolean(PopulationGuard.AMBIENT,true);
        return WorldOutbreakSystem.addScheduled(level,flyer);
    }

    public static void patrol(ServerLevel level,List<Vec3> observers) {
        if (observers.isEmpty() || !MilitaryCampaignSystem.enabled(level) || !WorldOutbreakSystem.active(level)
                || level.getDifficulty()==Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        var observer=observers.get(level.random.nextInt(observers.size()));
        int drones=0;
        for(var entity:level.getAllEntities()) if(entity instanceof MilitaryUav drone && drone.isAlive()) {
            if(++drones>=3 || drone.distanceToSqr(observer)<96*96) return;
        }
        for(int attempt=0;attempt<8;attempt++) {
            double bearing=level.random.nextDouble()*Math.PI*2;
            int range=32+level.random.nextInt(25);
            int x=Mth.floor(observer.x+Math.cos(bearing)*range), z=Mth.floor(observer.z+Math.sin(bearing)*range);
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk==null)continue;
            var point=new BlockPos(x,chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+7,z);
            if(Math.abs(observer.y-point.getY())>32 || WorldOutbreakSystem.sanctuary(level,point)
                    || CityEcology.zone(level,point)==DistrictThreat.Zone.GREEN
                    || observers.stream().anyMatch(other->other.distanceToSqr(Vec3.atCenterOf(point))<24*24))continue;
            var drone=EncounterEntities.UAV.get().create(level);
            if(drone!=null && placeInOpenAir(level,drone,point) && WorldOutbreakSystem.addScheduled(level,drone))return;
        }
    }
}
