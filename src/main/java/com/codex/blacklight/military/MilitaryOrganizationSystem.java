package com.codex.blacklight.military;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Server-thread facade for persistent rosters and bounded loaded-neighbour registration. */
public final class MilitaryOrganizationSystem {
    private static final Map<ServerLevel,Runtime> RUNTIMES=new WeakHashMap<>();
    private static final class Runtime {
        final MilitarySavedData data;
        final MilitaryWorkBudget budget=new MilitaryWorkBudget();
        final Set<UUID> registration=new HashSet<>();
        final Map<UUID,Long> forming=new HashMap<>();
        final Map<Long,Set<UUID>> cells=new HashMap<>();
        final Map<UUID,Long> locations=new HashMap<>();
        Runtime(MilitarySavedData data){this.data=data;}
    }
    private MilitaryOrganizationSystem(){}
    private static Runtime runtime(ServerLevel level){return RUNTIMES.computeIfAbsent(level,key->new Runtime(MilitarySavedData.get(key)));}
    public static MilitaryOrganization organization(ServerLevel level){return runtime(level).data.organization();}
    public static MilitarySavedData data(ServerLevel level){return runtime(level).data;}
    public static MilitaryWorkBudget budget(ServerLevel level){return runtime(level).budget;}
    public static void track(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||soldier.isRemoved())return;
        var s=runtime(level);long key=net.minecraft.world.level.ChunkPos.asLong(soldier.blockPosition().getX()>>4,soldier.blockPosition().getZ()>>4);
        var old=s.locations.put(soldier.getUUID(),key);if(old!=null&&old==key)return;
        if(old!=null){var cell=s.cells.get(old);if(cell!=null){cell.remove(soldier.getUUID());if(cell.isEmpty())s.cells.remove(old);}}
        s.cells.computeIfAbsent(key,ignored->new HashSet<>()).add(soldier.getUUID());
    }
    public static List<MilitarySoldier> nearby(MilitarySoldier soldier){
        var level=(ServerLevel)soldier.level();var s=runtime(level);var result=new ArrayList<MilitarySoldier>();
        int x=soldier.blockPosition().getX()>>4,z=soldier.blockPosition().getZ()>>4;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(var id:s.cells.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x+dx,z+dz),Set.of())){
            if(level.getEntity(id) instanceof MilitarySoldier other&&other!=soldier&&other.tacticsAvailable()&&other.faction()==soldier.faction()&&soldier.distanceToSqr(other)<=16*16&&soldier.hasLineOfSight(other))result.add(other);
        }return result;
    }

    public static void ensureMember(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||!soldier.isAlive()||soldier.isRemoved())return;
        track(soldier);
        var state=runtime(level);var org=state.data.organization();var owner=org.leafOf(soldier.getUUID());
        if(BlacklightInfectionSystem.isInfected(soldier)){
            owner.ifPresent(id->org.removeMember(soldier.getUUID()));soldier.setUnitId(null);return;
        }
        if(owner.isPresent()){
            var unit=org.unit(owner.get()).orElseThrow();
            if(unit.faction()==soldier.faction()){
                soldier.setUnitId(unit.id());soldier.finishDeployment();
                boolean changed=unit.members().get(soldier.getUUID())!=soldier.rank();org.rank(soldier.getUUID(),soldier.rank());
                if(changed&&!state.data.commanderManual(unit.id()))appointHighest(org,unit.id());
                processRegistration(level,state);return;
            }
            org.removeMember(soldier.getUUID());
        }
        if(!soldier.deployment().isEmpty()){
            var origin=soldier.deploymentOrigin();String key=level.dimension().location()+":"+soldier.faction().id()+":"+soldier.deployment()+":"+origin;
            var id=UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(org.unit(id).isEmpty()&&org.create(id,soldier.faction(),MilitaryUnitType.TACTICAL_SQUAD,"garrison-"+id,soldier.deploymentCapacity(),origin))state.data.setCommanderManual(id,false);
            if(org.assign(id,soldier.getUUID(),soldier.rank())){soldier.setUnitId(id);if(!state.data.commanderManual(id))appointHighest(org,id);soldier.finishDeployment();return;}
            soldier.finishDeployment();
        }
        var hint=soldier.unitId();
        if(hint!=null){
            var unit=org.unit(hint);
            if(unit.isPresent()&&unit.get().faction()==soldier.faction()&&org.assign(hint,soldier.getUUID(),soldier.rank())){
                if(state.data.automatic(hint)&&!state.data.commanderManual(hint))appointHighest(org,hint);
                processRegistration(level,state);return;
            }
            soldier.setUnitId(null);
        }
        if(!soldier.automaticUnitJoin()||soldier.isNoAi())return;
        state.registration.add(soldier.getUUID());state.budget.enqueue(MilitaryWorkBudget.Kind.DISCOVERY,soldier.getUUID(),level.getGameTime());
        processRegistration(level,state);
    }
    /** Role conversions update the roster's rank; explicit/manual appointments retain priority. */
    public static void roleChanged(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;
        var state=runtime(level);var org=state.data.organization();
        org.leafOf(soldier.getUUID()).ifPresent(unit->{
            if(org.unit(unit).orElseThrow().faction()!=soldier.faction())return;
            org.rank(soldier.getUUID(),soldier.rank());
            if(!state.data.commanderManual(unit))appointHighest(org,unit);
        });
    }
    private static void processRegistration(ServerLevel level,Runtime state){
        long now=level.getGameTime();var org=state.data.organization();
        for(var id:state.budget.drain(MilitaryWorkBudget.Kind.DISCOVERY,now)){
            if(!state.registration.remove(id)||!(level.getEntity(id) instanceof MilitarySoldier soldier)||!soldier.isAlive()||soldier.isRemoved()
                    ||BlacklightInfectionSystem.isInfected(soldier)||!soldier.automaticUnitJoin()||org.leafOf(id).isPresent())continue;
            var candidates=new ArrayList<>(nearby(soldier));
            candidates.sort(Comparator.<MilitarySoldier>comparingDouble(soldier::distanceToSqr).thenComparing(MilitarySoldier::getUUID));
            UUID chosen=null;
            for(var candidate:candidates){
                var unit=org.unit(candidate.unitId());
                if(unit.isPresent()&&unit.get().faction()==soldier.faction()&&state.data.automatic(unit.get().id())&&unit.get().type().leaf()&&unit.get().members().size()<unit.get().capacity()){
                    if(org.assign(unit.get().id(),id,soldier.rank())){chosen=unit.get().id();break;}
                }
            }
            if(chosen==null){
                chosen=UUID.randomUUID();var home=soldier.home();
                if(!org.create(chosen,soldier.faction(),MilitaryUnitType.TACTICAL_SQUAD,soldier.faction().id()+"-"+chosen,8,
                        new MilitaryOrder.Point(home.getX()+.5,home.getY(),home.getZ()+.5)))continue;
                if(!org.assign(chosen,id,soldier.rank())){org.removeUnit(chosen);continue;}
                state.data.automatic(chosen,true);state.forming.put(chosen,now);
            }
            soldier.setUnitId(chosen);
            if(state.data.automatic(chosen)&&!state.data.commanderManual(chosen))appointHighest(org,chosen);
        }
        state.forming.entrySet().removeIf(entry->now-entry.getValue()>20||org.unit(entry.getKey()).isEmpty());
    }
    public static void appointHighest(MilitaryOrganization org,UUID unit){
        org.allMembers(unit).stream().min(Comparator.<UUID>comparingInt(id->{var leaf=org.leafOf(id).orElseThrow();return org.unit(leaf).orElseThrow().members().get(id).ordinal();})
                .reversed().thenComparing(Comparator.naturalOrder())).ifPresent(id->org.appoint(unit,id));
    }
    public static Optional<UUID> commander(ServerLevel level,UUID unit){
        if(unit==null)return Optional.empty();
        var org=organization(level);var available=new HashSet<UUID>();
        for(var id:org.allMembers(unit))if(level.getEntity(id) instanceof MilitarySoldier soldier&&soldier.tacticsAvailable())available.add(id);
        return org.actingCommander(unit,available);
    }
    /** Run a complete edit on a private draft; commit and synchronize only on success. */
    public static boolean transaction(ServerLevel level,Predicate<MilitaryOrganization> edit){
        var state=runtime(level);var before=state.data.organization();var draft=before.copy();
        if(!edit.test(draft))return false;
        var affected=new HashSet<UUID>();for(var unit:before.units())affected.addAll(unit.members().keySet());
        for(var unit:draft.units())affected.addAll(unit.members().keySet());
        state.data.replace(draft);
        for(var id:affected)if(level.getEntity(id) instanceof MilitarySoldier soldier){
            var leaf=draft.leafOf(id);soldier.setUnitId(leaf.orElse(null));
            leaf.ifPresent(unit->soldier.setRank(draft.unit(unit).orElseThrow().members().get(id)));
        }
        cleanupEmptyAutomatic(state);return true;
    }
    public static void manual(ServerLevel level,UUID unit){
        var state=runtime(level);
        for(var view:state.data.organization().units())if(state.data.organization().ancestors(view.id()).contains(unit)){
            state.data.automatic(view.id(),false);state.forming.remove(view.id());
        }
    }
    private static void cleanupEmptyAutomatic(Runtime state){
        var org=state.data.organization();
        for(var unit:org.units())if(state.data.automatic(unit.id())&&unit.members().isEmpty()&&unit.children().isEmpty()){
            org.removeUnit(unit.id());state.data.automatic(unit.id(),false);state.forming.remove(unit.id());
        }
    }
    private static void permanentlyRemove(MilitarySoldier soldier,ServerLevel level){
        var state=RUNTIMES.get(level);if(state==null&&soldier.unitId()==null)return;
        if(state==null)state=runtime(level);
        state.data.organization().removeMember(soldier.getUUID());state.registration.remove(soldier.getUUID());state.budget.remove(soldier.getUUID());
        soldier.setUnitId(null);cleanupEmptyAutomatic(state);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){
        if(event.getEntity() instanceof MilitarySoldier soldier&&soldier.level() instanceof ServerLevel level)permanentlyRemove(soldier,level);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
        if(!(event.getEntity() instanceof MilitarySoldier soldier)||!(event.getLevel() instanceof ServerLevel level))return;
        var state=RUNTIMES.get(level);
        if(state!=null){state.registration.remove(soldier.getUUID());state.budget.remove(soldier.getUUID());var key=state.locations.remove(soldier.getUUID());if(key!=null){var cell=state.cells.get(key);if(cell!=null){cell.remove(soldier.getUUID());if(cell.isEmpty())state.cells.remove(key);}}}
        var reason=soldier.getRemovalReason();
        if(reason==net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION||reason!=null&&reason.shouldDestroy())permanentlyRemove(soldier,level);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof MilitarySoldier soldier&&!event.getLevel().isClientSide())track(soldier);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)RUNTIMES.remove(level);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){RUNTIMES.clear();}
}
