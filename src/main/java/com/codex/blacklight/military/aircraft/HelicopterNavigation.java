package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-only cached route follower. Search jobs never survive a phase change, capture or reload. */
public final class HelicopterNavigation {
    public enum Status { DIRECT, SEARCHING, FOLLOWING, FAILED }
    public static final int NODES_PER_CRAFT=6,NODES_PER_LEVEL=24;
    private static final class Budget {long tick=Long.MIN_VALUE;int spent;}
    private static final Map<ServerLevel,Budget> BUDGETS=new WeakHashMap<>();
    private final BlackwatchHelicopter craft;
    private HelicopterRoute search;private final ArrayDeque<Vec3> path=new ArrayDeque<>();
    private Vec3 destination,selected;private long checkAt,retryAt;private Status status=Status.DIRECT;
    private int searches,expanded,invalidations;private boolean obstacle;
    public HelicopterNavigation(BlackwatchHelicopter craft){this.craft=craft;}
    public Status status(){return status;}public int searches(){return searches;}public int expanded(){return expanded;}public int invalidations(){return invalidations;}
    public boolean searching(){return status==Status.SEARCHING;}
    public List<Vec3> waypoints(){return List.copyOf(path);}
    public void reset(){search=null;path.clear();destination=null;selected=null;checkAt=retryAt=0;status=Status.DIRECT;obstacle=false;}
    public void obstructed(){if(status==Status.FOLLOWING){invalidate();}else checkAt=0;}
    private void invalidate(){invalidations++;search=null;path.clear();selected=null;status=Status.DIRECT;checkAt=0;obstacle=true;}
    private boolean clear(Vec3 from,Vec3 to){return craft.clearAirRoute(from,to,from,craft.getYRot());}
    private static int available(ServerLevel level){var b=BUDGETS.computeIfAbsent(level,key->new Budget());long tick=level.getGameTime();if(b.tick!=tick){b.tick=tick;b.spent=0;}return Math.max(0,NODES_PER_LEVEL-b.spent);}
    public static int nodesUsed(ServerLevel level){available(level);return BUDGETS.get(level).spent;}
    /** Null means hold/brake while the bounded job runs or while the route is unavailable. */
    public Vec3 guide(Vec3 goal){
        if(!(craft.level() instanceof ServerLevel level)||!HelicopterFlight.finite(goal))return null;
        long now=level.getGameTime();Vec3 at=craft.position();
        if(destination==null||destination.distanceToSqr(goal)>64){reset();destination=goal;}
        if(status==Status.FAILED){if(now<retryAt)return null;invalidate();destination=goal;}
        if(search!=null&&search.status()==HelicopterRoute.Status.SEARCHING){
            status=Status.SEARCHING;int allowance=Math.min(NODES_PER_CRAFT,available(level));int used=search.advance(allowance);BUDGETS.get(level).spent+=used;expanded+=used;
            if(search.status()==HelicopterRoute.Status.SEARCHING)return null;
            if(search.status()==HelicopterRoute.Status.FAILED){status=Status.FAILED;retryAt=now+100;return null;}
            path.addAll(search.path());search=null;status=Status.FOLLOWING;checkAt=0;
        }
        if(!path.isEmpty()){
            while(path.size()>1&&at.distanceToSqr(path.peekFirst())<1.21){path.removeFirst();checkAt=0;}
            if(now>=checkAt||selected==null){
                checkAt=now+5;
                if(!clear(at,path.peekFirst())){invalidate();destination=goal;}else{
                    selected=path.peekFirst();int checked=0;
                    // Small look-ahead removes four-block stop/start movement without cutting blocked corners.
                    for(var candidate:path){if(++checked>6||at.distanceToSqr(candidate)>24*24)break;if(clear(at,candidate))selected=candidate;else break;}
                    while(path.size()>1&&!path.peekFirst().equals(selected))path.removeFirst();
                    if(path.size()==1&&selected.distanceToSqr(goal)<64&&clear(at,goal))selected=goal;
                }
            }
            if(!path.isEmpty()){status=Status.FOLLOWING;return selected;}
        }
        if(!obstacle&&now<checkAt){status=Status.DIRECT;return goal;}
        Vec3 delta=goal.subtract(at);Vec3 probe=at.add(delta.length()>12?delta.normalize().scale(12):delta);
        if(!obstacle&&clear(at,probe)){checkAt=now+5;status=Status.DIRECT;return goal;}
        Vec3 origin=at;float yaw=craft.getYRot();destination=goal;obstacle=false;searches++;
        search=new HelicopterRoute(origin,goal,(from,to)->craft.clearAirRoute(from,to,origin,yaw));
        if(search.status()==HelicopterRoute.Status.FAILED){status=Status.FAILED;retryAt=now+100;return null;}
        if(search.status()==HelicopterRoute.Status.READY){path.addAll(search.path());search=null;selected=path.peekFirst();status=Status.FOLLOWING;checkAt=0;return selected;}
        status=Status.SEARCHING;return null;
    }
}
