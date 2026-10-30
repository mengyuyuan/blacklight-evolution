package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.infection.WorldOutbreakSystem;
import com.codex.blacklight.registry.ModEntities;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.city.MilitaryCampaignData.*;

/** New incoming patrols have new identities and finite loadouts; existing garrisons are never refilled. */
final class FieldReinforcementSystem {
    static final String FIELD="BlacklightCampaignField",AGE="BlacklightCampaignAge";
    private static final Map<ServerLevel,Integer> CURSORS=new WeakHashMap<>();
    static boolean ambient(MilitarySoldier soldier){return soldier.getPersistentData().getBoolean(FIELD);}
    static void retire(ServerLevel level,List<Vec3> observers){
        var candidates=new ArrayList<MilitarySoldier>();
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&ambient(soldier)
                &&soldier.isAlive()&&level.isPositionEntityTicking(soldier.blockPosition()))candidates.add(soldier);
        for(var soldier:candidates){
            if(MilitaryHomeSystem.ordered(soldier)||soldier.isPersistenceRequired()||soldier.hasCustomName())continue;
            var tag=soldier.getPersistentData();int age=Math.min(240000,tag.getInt(AGE)+20);tag.putInt(AGE,age);
            // Real, unattended ambient patrols can leave. Never remove a soldier being fought, carried or consumed.
            if((age>=MilitaryCampaignConfig.FIELD_LIFETIME.get()||!MilitaryCampaignSystem.active(soldier))
                    &&soldier.tacticsAvailable()&&soldier.getTarget()==null
                    &&observers.stream().noneMatch(p->p.distanceToSqr(soldier.position())<48*48))soldier.discard();
        }
    }
    static boolean reinforce(ServerLevel level,List<Vec3> observers){
        var data=get(level);
        if(!MilitaryCampaignSystem.enabled(level)||!MilitaryCampaignConfig.REINFORCEMENTS.get()
                ||!WorldOutbreakSystem.active(level)||level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL
                ||!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                ||data.operations.size()>=MilitaryCampaignConfig.ACTIVE.get())return false;
        int total=0,field=0;
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&soldier.isAlive()){total++;if(ambient(soldier))field++;}
        int size=Math.min(MilitaryCampaignConfig.SQUAD.get(),Math.min(MilitaryCampaignConfig.FIELD_LIMIT.get()-field,CityConfig.MILITARY_GARRISON_LIMIT.get()-total));
        if(size<2)return false;
        // One finite candidate list per dimension, not one unbounded spawn loop per player.
        var districts=new LinkedHashMap<CityEcologyData.Key,BlockPos>();
        for(var observer:observers)for(int[] offset:new int[][]{{0,0},{64,0},{-64,0},{0,64},{0,-64}}){
            var at=BlockPos.containing(observer).offset(offset[0],0,offset[1]);
            if(MilitaryCampaignSystem.loaded(level,at)&&CityEcology.zone(level,at)==DistrictThreat.Zone.RED)districts.putIfAbsent(CityEcology.key(level,at),at);
        }
        var candidates=new ArrayList<>(districts.entrySet());
        if(!candidates.isEmpty()){
            int first=Math.floorMod(CURSORS.getOrDefault(level,0),candidates.size());
            Collections.rotate(candidates,-first);CURSORS.put(level,(first+1)%candidates.size());
        }
        for(var entry:candidates){
            if(data.operations.values().stream().anyMatch(op->CityEcology.key(level,op.target).equals(entry.getKey())))continue;
            var center=entry.getValue();var nearest=MilitaryBaseAccess.nearby(level,Vec3.atCenterOf(center)).stream()
                    .filter(b->b.center().distSqr(center)<256*256).findFirst().orElse(null);
            // Destroying or infiltrating the local command post really cuts its support.
            var baseState=nearest==null?null:data.bases.get(nearest.id());
            var original=nearest==null?null:CityMilitaryData.get(level).bases.get(nearest.id());
            if(nearest!=null&&(nearest.loaded()&&!nearest.operational()||original!=null&&original.health.inactive()
                    ||baseState!=null&&(baseState.destroyed||baseState.sabotage>0)))continue;
            // An unobserved distant base is not evidence of destruction and is not a local supply source.
            if(nearest!=null&&!nearest.loaded())nearest=null;
            String source=nearest!=null?nearest.id():"cordon:"+UUID.nameUUIDFromBytes(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
            var state=data.base(source);if(state==null||state.sabotage>0)continue;
            var incoming=new ArrayList<MilitarySoldier>();
            UUID id=UUID.randomUUID();BlockPos origin=null;
            for(int attempt=0;attempt<12&&incoming.isEmpty();attempt++){
                double angle=(attempt+level.getGameTime()/400L)*2.399963229728653;
                int x=center.getX()+(int)(Math.cos(angle)*64),z=center.getZ()+(int)(Math.sin(angle)*64);
                var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
                int y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
                var generator=level.getChunkSource().getGenerator();
                if(CityMilitaryTerrain.supported(generator)){
                    var ground=CityMilitaryTerrain.survey(generator,level.getSeed()).at(x,z);
                    if(ground.water()||ground.blocked()||Math.abs(y-ground.ground()-1)>3)continue;
                }else if(generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator){
                    int ground=generator.getFirstOccupiedHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState());
                    if(Math.abs(y-ground-1)>4)continue;
                }
                var entryPoint=new BlockPos(x,y,z);if(!MilitaryCampaignSystem.loaded(level,entryPoint))continue;
                if(observers.stream().anyMatch(p->p.distanceToSqr(Vec3.atCenterOf(entryPoint))<32*32))continue;
                for(int slot=0;slot<size;slot++){
                    var at=entryPoint.offset((slot%2)*2,0,(slot/2)*2);
                    if(!MilitaryCampaignSystem.loaded(level,at)||!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),net.minecraft.core.Direction.UP)
                            ||!level.getFluidState(at).isEmpty())continue;
                    MilitarySoldier soldier=(nearest!=null&&nearest.faction()==com.codex.blacklight.military.MilitaryFaction.MILITARY
                            ?ModEntities.MILITARY_SOLDIER.get():slot==0?ModEntities.BLACKWATCH_HEAVY.get():ModEntities.BLACKWATCH_SOLDIER.get()).create(level);
                    if(soldier==null)continue;
                    soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                    if(!level.noCollision(soldier))continue;
                    soldier.getPersistentData().putBoolean(FIELD,true);
                    soldier.getPersistentData().putString(MilitaryHomeSystem.MODE_TAG,"field");
                    soldier.getPersistentData().putLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG,entryPoint.asLong());
                    soldier.getPersistentData().putString(MilitaryHomeSystem.FIELD_DIMENSION_TAG,level.dimension().location().toString());
                    soldier.setAutomaticUnitJoin(false);
                    if(!soldier.initializeNativeLoadout("campaign:"+id+":"+slot))continue;
                    incoming.add(soldier);
                }
                if(incoming.size()<2){incoming.clear();continue;}origin=entryPoint;
            }
            if(incoming.size()<2)continue;
            var objective=MilitaryCampaignSystem.objective(level,source,center);
            var op=new Operation(id,source,objective==null?Kind.PATROL:objective.kind,objective==null?center:objective.target,
                    objective==null?null:objective.entity,objective==null?-1:objective.hive);
            op.origin=origin;op.expedition=true;
            // Publish membership before EntityJoin listeners run; a canceled join is never a successful member.
            data.operations.put(op.id,op);
            for(var soldier:incoming){op.members.add(soldier.getUUID());if(!level.addFreshEntity(soldier))op.members.remove(soldier.getUUID());}
            if(op.members.isEmpty()){data.operations.remove(op.id);continue;}
            data.setDirty();return true;
        }
        return false;
    }
    private FieldReinforcementSystem(){}
}
