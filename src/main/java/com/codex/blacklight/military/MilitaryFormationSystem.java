package com.codex.blacklight.military;

import com.codex.blacklight.entity.BlackwatchSquad;
import com.codex.blacklight.entity.MilitarySoldier;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Loaded-only hierarchy movement, immutable sighting relays and a single navigation work queue. */
public final class MilitaryFormationSystem {
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    private record Job(Vec3 point,double speed,long at){}
    private record Alarm(BlackwatchSquad.Contact contact,Vec3 relay,Set<UUID> visited){}
    private record CommandKey(UUID unit,UUID scope){}
    private static final class Route {
        MilitaryOrder intent;Vec3 start,center,heading=new Vec3(0,0,1),patrol;long tick=-1,nextPatrol;
    }
    private static final class State {
        final Map<UUID,Job> jobs=new HashMap<>();
        final Map<UUID,Route> routes=new HashMap<>();
        final Map<UUID,Alarm> alarms=new LinkedHashMap<>();
        final Map<UUID,Long> relayed=new HashMap<>(),planned=new HashMap<>();
        final Set<UUID> failed=new HashSet<>();
        final Map<CommandKey,UUID> movingCommanders=new HashMap<>();
        long commandTick=-1,commandRevision=-1;
        long planTick=-1,relayTick=-1,total;int inTick,peak,shortest=Integer.MAX_VALUE;
    }
    public record Metrics(long plans,int peakPlans,int shortestInterval,int plannedMembers){}
    private MilitaryFormationSystem(){}
    private static State state(ServerLevel level){return LEVELS.computeIfAbsent(level,ignored->new State());}
    public static Metrics metrics(ServerLevel level){var s=state(level);return new Metrics(s.total,s.peak,s.shortest,s.planned.size());}
    public static UUID scope(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||soldier.unitId()==null)return null;
        var org=MilitaryOrganizationSystem.organization(level);var ancestors=org.ancestors(soldier.unitId());
        for(var id:ancestors)if(org.unit(id).orElseThrow().order()!=null)return id;
        return ancestors.isEmpty()?null:ancestors.get(ancestors.size()-1);
    }
    private static Vec3 vector(MilitaryOrder.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static MilitarySoldier commander(ServerLevel level,UUID unit){
        var id=MilitaryOrganizationSystem.commander(level,unit);return id.isPresent()&&level.getEntity(id.get()) instanceof MilitarySoldier s?s:null;
    }
    private static MilitarySoldier movingCommander(ServerLevel level,UUID unit,UUID scope){
        if(unit==null||scope==null)return null;var org=MilitaryOrganizationSystem.organization(level);var state=state(level);long now=level.getGameTime();
        if(state.commandTick!=now||state.commandRevision!=org.revision()){
            state.commandTick=now;state.commandRevision=org.revision();state.movingCommanders.clear();
        }
        var key=new CommandKey(unit,scope);
        if(state.movingCommanders.containsKey(key)){
            var id=state.movingCommanders.get(key);if(id==null)return null;
            if(level.getEntity(id) instanceof MilitarySoldier s&&s.tacticsAvailable()&&scope.equals(scope(s)))return s;
        }
        var available=new HashSet<UUID>();
        for(var id:org.allMembers(unit))if(level.getEntity(id) instanceof MilitarySoldier s&&s.tacticsAvailable()&&scope.equals(scope(s)))available.add(id);
        var id=org.actingCommander(unit,available).orElse(null);state.movingCommanders.put(key,id);
        return id!=null&&level.getEntity(id) instanceof MilitarySoldier s?s:null;
    }
    /** An independently ordered child keeps its formal officer; the parent's remaining troops use an operational deputy. */
    public static Optional<UUID> orderCommander(ServerLevel level,UUID unit){
        var org=MilitaryOrganizationSystem.organization(level);var ancestors=org.ancestors(unit);if(ancestors.isEmpty())return Optional.empty();
        UUID scope=ancestors.get(ancestors.size()-1);for(var id:ancestors)if(org.unit(id).orElseThrow().order()!=null){scope=id;break;}
        var captain=movingCommander(level,unit,scope);return captain==null?Optional.empty():Optional.of(captain.getUUID());
    }
    private static Route route(MilitarySoldier soldier,UUID scope){
        var level=(ServerLevel)soldier.level();var unit=MilitaryOrganizationSystem.organization(level).unit(scope).orElseThrow();
        var intent=unit.order()==null?new MilitaryOrder(MilitaryOrder.Mode.PATROL,null):unit.order();
        var r=state(level).routes.computeIfAbsent(scope,ignored->new Route());var captain=movingCommander(level,scope,scope);long now=level.getGameTime();
        if(!intent.equals(r.intent)){
            r.intent=intent;r.start=captain==null?vector(unit.origin()):captain.position();
            r.center=intent.mode()==MilitaryOrder.Mode.MOVE?r.start:intent.mode()==MilitaryOrder.Mode.RETURN||intent.target()==null?vector(unit.origin()):vector(intent.target());r.patrol=null;r.tick=-1;
            if(intent.target()!=null){var delta=vector(intent.target()).subtract(r.start).multiply(1,0,1);if(delta.lengthSqr()>1)r.heading=delta.normalize();}
        }
        if(r.tick!=now){
            r.tick=now;
            if(intent.mode()==MilitaryOrder.Mode.MOVE&&captain!=null&&captain.getTarget()==null){
                var end=vector(intent.target());var direction=end.subtract(r.start);double length=direction.length();
                if(length>0){direction=direction.scale(1/length);double progress=Math.max(0,Math.min(length,captain.position().subtract(r.start).dot(direction)));
                    var wanted=r.start.add(direction.scale(progress));var step=wanted.subtract(r.center);r.center=r.center.add(step.length()>1?step.normalize():step);}
            }
        }
        return r;
    }
    /** Hierarchical offsets are stable in route direction and leave the HQ branch on the center line. */
    private static Vec3 offset(ServerLevel level,UUID scope,UUID leaf,UUID commander,Vec3 heading){
        var org=MilitaryOrganizationSystem.organization(level);Vec3 result=Vec3.ZERO;var side=new Vec3(heading.z,0,-heading.x);
        for(var id:org.ancestors(leaf)){
            if(id.equals(scope))break;var child=org.unit(id).orElseThrow();if(child.parent()==null)break;var parent=org.unit(child.parent()).orElseThrow();
            var children=new ArrayList<>(parent.children());UUID headquarters=null;
            for(var c:children)if(commander!=null&&org.allMembers(c).contains(commander)){headquarters=c;break;}
            if(id.equals(headquarters))continue;children.remove(headquarters);int slot=children.indexOf(id);
            double spacing=parent.type().level()==1?6:parent.type().level()==2?13:22;
            result=result.add(side.scale((slot%2==0?-1:1)*(1+slot/2)*spacing)).subtract(heading.scale(spacing*.6));
        }
        return result;
    }
    public static Vec3 territory(MilitarySoldier soldier){
        UUID scope=scope(soldier);if(scope==null)return Vec3.atBottomCenterOf(soldier.home());
        var level=(ServerLevel)soldier.level();var r=route(soldier,scope);var captain=movingCommander(level,scope,scope);
        return r.center.add(offset(level,scope,soldier.unitId(),captain==null?null:captain.getUUID(),r.heading));
    }
    public static boolean patrol(MilitarySoldier soldier){
        if(com.codex.blacklight.city.MilitaryCampaignSystem.active(soldier))return true;
        UUID scope=scope(soldier);if(scope==null)return false;
        var level=(ServerLevel)soldier.level();var r=route(soldier,scope);var captain=movingCommander(level,scope,scope);var leafCaptain=movingCommander(level,soldier.unitId(),scope);
        if(captain==null||leafCaptain==null){soldier.getNavigation().stop();return true;}
        Vec3 destination;
        if(soldier==captain){
            var unit=MilitaryOrganizationSystem.organization(level).unit(scope).orElseThrow();
            destination=switch(r.intent.mode()){
                case MOVE->vector(r.intent.target());case RETURN,REGROUP,GUARD->r.center;
                case PATROL->{
                    if((r.patrol==null||soldier.position().distanceToSqr(r.patrol)<3||state(level).failed.contains(soldier.getUUID()))&&level.getGameTime()>=r.nextPatrol){
                        state(level).failed.remove(soldier.getUUID());
                        r.nextPatrol=level.getGameTime()+60;double angle=((scope.getLeastSignificantBits()&255)+level.getGameTime()/60)*2.399963;
                        r.patrol=r.center.add(Math.cos(angle)*8,0,Math.sin(angle)*8);
                    }yield r.patrol==null?r.center:r.patrol;
                }
            };
        }else if(soldier==leafCaptain)destination=captain.position().add(offset(level,scope,soldier.unitId(),captain.getUUID(),r.heading));
        else{
            var members=new ArrayList<>(MilitaryOrganizationSystem.organization(level).allMembers(soldier.unitId()));members.remove(leafCaptain.getUUID());int slot=members.indexOf(soldier.getUUID());
            var right=new Vec3(r.heading.z,0,-r.heading.x);destination=leafCaptain.position().subtract(r.heading.scale(2.5+slot/4*2.5)).add(right.scale(((slot%4)-1.5)*2.5));
        }
        if(soldier.position().distanceToSqr(destination)>3)navigate(soldier,destination,.9);
        else {state(level).jobs.remove(soldier.getUUID());soldier.getNavigation().stop();}
        relay(level);return true;
    }
    public static void navigate(MilitarySoldier soldier,Vec3 destination,double speed){
        if(!(soldier.level() instanceof ServerLevel level)||!soldier.tacticsAvailable()||!Double.isFinite(destination.lengthSqr()))return;
        request(soldier,destination,speed);var s=state(level);var budget=MilitaryOrganizationSystem.budget(level);
        for(var id:budget.drain(MilitaryWorkBudget.Kind.NAVIGATION,level.getGameTime())){
            var job=s.jobs.remove(id);
            if(job==null||level.getGameTime()-job.at()>100||!(level.getEntity(id) instanceof MilitarySoldier member)||!member.tacticsAvailable())continue;
            Vec3 delta=job.point().subtract(member.position());var local=delta.length()>16?member.position().add(delta.normalize().scale(16)):job.point();
            var point=standing(member,local);if(point==null){s.failed.add(id);continue;}
            if(s.planTick!=level.getGameTime()){s.planTick=level.getGameTime();s.inTick=0;}
            s.peak=Math.max(s.peak,++s.inTick);s.total++;var before=s.planned.put(id,level.getGameTime());if(before!=null)s.shortest=Math.min(s.shortest,(int)(level.getGameTime()-before));
            var path=((MilitaryGroundNavigation)member.getNavigation()).plan(point);
            if(path!=null&&path.canReach()){member.getNavigation().moveTo(path,job.speed());s.failed.remove(id);}else s.failed.add(id);
        }
        s.jobs.entrySet().removeIf(e->level.getGameTime()-e.getValue().at()>100);
    }
    static void request(MilitarySoldier soldier,Vec3 destination,double speed){
        if(!(soldier.level() instanceof ServerLevel level)||!soldier.tacticsAvailable()||!Double.isFinite(destination.lengthSqr()))return;
        state(level).jobs.put(soldier.getUUID(),new Job(destination,speed,level.getGameTime()));
        MilitaryOrganizationSystem.budget(level).enqueue(MilitaryWorkBudget.Kind.NAVIGATION,soldier.getUUID(),level.getGameTime());
    }
    static void cancel(MilitarySoldier soldier){
        if(soldier.level() instanceof ServerLevel level){var s=LEVELS.get(level);if(s!=null)s.jobs.remove(soldier.getUUID());}
    }
    public static BlockPos standing(MilitarySoldier soldier,Vec3 destination){
        var exact=standingAt(soldier,destination);if(exact!=null)return exact;
        for(int radius=1;radius<=3;radius++)for(int[] offset:new int[][]{{radius,0},{-radius,0},{0,radius},{0,-radius},{radius,radius},{-radius,radius},{radius,-radius},{-radius,-radius}}){
            var alternative=standingAt(soldier,destination.add(offset[0],0,offset[1]));if(alternative!=null)return alternative;
        }return null;
    }
    private static BlockPos standingAt(MilitarySoldier soldier,Vec3 destination){
        var level=soldier.level();var base=BlockPos.containing(destination);
        if(!level.hasChunkAt(base)||!level.getWorldBorder().isWithinBounds(base))return null;
        int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,base.getX(),base.getZ());
        for(int y:new int[]{base.getY(),base.getY()+1,base.getY()-1,base.getY()+2,base.getY()-2,height}){
            if(Math.abs(y-soldier.getY())>16)continue;var p=new BlockPos(base.getX(),y,base.getZ());
            if(!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)||!level.getFluidState(p).isEmpty()||!level.getFluidState(p.above()).isEmpty())continue;
            if(level.noCollision(soldier,new AABB(p.getX()+.15,y,p.getZ()+.15,p.getX()+.85,y+1.95,p.getZ()+.85)))return p;
        }return null;
    }
    public static void observe(MilitarySoldier soldier,BlackwatchSquad.Contact contact){
        if(!(soldier.level() instanceof ServerLevel level)||soldier.unitId()==null)return;
        var s=state(level);s.alarms.put(soldier.unitId(),new Alarm(contact,soldier.position(),Set.of(soldier.unitId())));relay(level);
    }
    private static void relay(ServerLevel level){
        var s=state(level);long now=level.getGameTime();if(s.relayTick==now)return;s.relayTick=now;
        var org=MilitaryOrganizationSystem.organization(level);s.alarms.entrySet().removeIf(e->now-e.getValue().contact().observedAt()>=BlackwatchSquad.CONTACT_TICKS||org.unit(e.getKey()).isEmpty());
        if(now%100==0){s.routes.keySet().removeIf(id->org.unit(id).isEmpty());s.relayed.keySet().removeIf(id->org.unit(id).isEmpty());}
        int work=0;
        for(var entry:new ArrayList<>(s.alarms.entrySet())){
            if(now-s.relayed.getOrDefault(entry.getKey(),-100L)<10)continue;if(work++>=4)break;
            s.relayed.put(entry.getKey(),now);var unit=org.unit(entry.getKey()).orElseThrow();var sender=commander(level,unit.id());if(sender==null)continue;
            var neighbours=new ArrayList<>(unit.children());if(unit.parent()!=null)neighbours.add(unit.parent());
            for(var id:neighbours){var receiver=commander(level,id);var alarm=entry.getValue();
                if(receiver==null||alarm.visited().contains(id)||receiver.distanceToSqr(sender)>64*64)continue;
                var old=s.alarms.get(id);if(old!=null&&old.contact().observedAt()>=alarm.contact().observedAt())continue;
                var visited=new HashSet<>(alarm.visited());visited.add(id);s.alarms.put(id,new Alarm(alarm.contact(),sender.position(),Set.copyOf(visited)));
            }
        }
    }
    public static Optional<BlackwatchSquad.Contact> contact(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||soldier.unitId()==null||!soldier.tacticsAvailable())return Optional.empty();relay(level);
        var alarm=state(level).alarms.get(soldier.unitId());if(alarm==null)return Optional.empty();var c=alarm.contact();
        if(level.getGameTime()-c.observedAt()>=BlackwatchSquad.CONTACT_TICKS||soldier.position().distanceToSqr(alarm.relay())>64*64)return Optional.empty();
        var target=level.getEntity(c.target());if(!(target instanceof LivingEntity living)||!soldier.validTarget(living))return Optional.empty();
        var point=new Vec3(c.position().x(),c.position().y(),c.position().z());
        if(point.distanceToSqr(territory(soldier))>48*48){soldier.getLookControl().setLookAt(point.x,point.y+1,point.z);return Optional.empty();}
        return Optional.of(c);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){if(e.getLevel() instanceof ServerLevel l&&e.getEntity() instanceof MilitarySoldier soldier){var s=LEVELS.get(l);if(s!=null){s.jobs.remove(soldier.getUUID());s.planned.remove(soldier.getUUID());s.failed.remove(soldier.getUUID());}}}
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel l)LEVELS.remove(l);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){LEVELS.clear();}
}
