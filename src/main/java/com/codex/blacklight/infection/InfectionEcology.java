package com.codex.blacklight.infection;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.city.*;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Local sources and eligibility; no terrain writes, chunk requests or population removal. */
public final class InfectionEcology {
    private record Release(BlockPos pos,long until){}
    private static final Map<ServerLevel,Deque<Release>> RELEASES=new WeakHashMap<>();
    private InfectionEcology(){}
    public static void released(ServerLevel level,BlockPos pos){
        var entries=RELEASES.computeIfAbsent(level,k->new ArrayDeque<>());prune(entries,level.getGameTime());
        entries.removeIf(r->r.pos().distSqr(pos)<=16);entries.addLast(new Release(pos.immutable(),level.getGameTime()+1200));
        while(entries.size()>128)entries.removeFirst();
    }
    private static void prune(Deque<Release> entries,long now){entries.removeIf(r->r.until()<=now);}
    public static boolean naturalEligible(LivingEntity target){
        if(!BlacklightInfectionSystem.canInfect(target)||!(target.level() instanceof ServerLevel level)
                ||!InfectionEcologyConfig.naturalDimension(level)||!com.codex.blacklight.compat.NaturalInfectionProtection.allowed(level,target.blockPosition())||target.isInvulnerable()
                ||target.getPersistentData().getBoolean("BlacklightNaturalInfectionProtected"))return false;
        if(InfectionEcologyConfig.value(InfectionEcologyConfig.PROTECT_NAMED)&&target.hasCustomName())return false;
        if(InfectionEcologyConfig.value(InfectionEcologyConfig.PROTECT_PETS)&&target instanceof OwnableEntity pet&&pet.getOwnerUUID()!=null)return false;
        return !InfectionEcologyConfig.value(InfectionEcologyConfig.PROTECT_TRADERS)||!(target instanceof AbstractVillager)||target.getTags().contains("BlacklightCityPopulation");
    }
    public static boolean hasSource(ServerLevel level,BlockPos pos){
        if(!InfectionEcologyConfig.value(InfectionEcologyConfig.LOCAL_SOURCE))return true;
        int radius=InfectionEcologyConfig.value(InfectionEcologyConfig.SOURCE_RADIUS);
        var releases=RELEASES.get(level);if(releases!=null){prune(releases,level.getGameTime());for(var r:releases)if(r.pos().distSqr(pos)<=(double)radius*radius)return true;}
        if(!level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(radius),m->m.isAlive()&&BlacklightInfectionSystem.isInfected(m)&&m.distanceToSqr(pos.getCenter())<=(double)radius*radius).isEmpty())return true;
        // Existing colony metadata provides a bounded path to physically verified cores.
        for(var colony:BuildingInfectionData.get(level).colonies)if(!colony.dead&&colony.origin.distSqr(pos)<=(double)radius*radius
                &&level.getChunkSource().getChunkNow(colony.origin.getX()>>4,colony.origin.getZ()>>4)!=null&&level.getBlockState(colony.origin).is(ModBlocks.BUILDING_CORE.get()))return true;
        if(CityTravel.isCity(level.dimension())){var city=CityData.get(level);for(int i=0;i<2;i++){
            var port=CitySystem.port(level,i,0);
            if(!city.destroyed(i)&&CityHiveSupply.ready(city,i)&&port.distSqr(pos)<=(double)radius*radius
                    &&level.getChunkSource().getChunkNow(port.getX()>>4,port.getZ()>>4)!=null&&level.getBlockState(port).is(CityBlocks.HATCH.get()))return true;
        }}
        int r=InfectionEcologyConfig.value(InfectionEcologyConfig.TISSUE_RADIUS);
        for(var p:BlockPos.betweenClosed(pos.offset(-r,-r,-r),pos.offset(r,r,r))){
            if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null||!level.isInWorldBounds(p))continue;
            var block=level.getBlockState(p);
            if(block.is(CityBlocks.FLESH.get())||block.is(ModBlocks.BUILDING_CORE.get())||block.is(ModBlocks.TISSUE_ATTACHMENT.get())||block.is(ModBlocks.HIVE_FLESH.get())
                    ||block.is(ModBlocks.INFECTED_SOIL.get())||block.is(ModBlocks.INFECTED_STONE.get())||block.is(ModBlocks.HIVE_VEIN.get())||block.is(ModBlocks.TISSUE_FIBER.get())
                    ||block.is(ModBlocks.BUILDING_BIOMASS.get()))return true;
        }
        return false;
    }
}
