package com.codex.blacklight.military.aircraft;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Incremental three-dimensional A*. The caller owns loaded-terrain and complete-airframe clearance. */
public final class HelicopterRoute {
    public enum Status { SEARCHING, READY, FAILED }
    @FunctionalInterface public interface Clearance { boolean clear(Vec3 from,Vec3 to); }
    public static final int MAX_NODES=1536;
    public static final double STEP=4,MARGIN=32,VERTICAL_MARGIN=24,MAX_DISTANCE=160;
    private record Cell(int x,int y,int z){}
    private record Node(Cell cell,double cost,double priority,long order){}
    private record Edge(Cell from,Cell to){}
    private static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
    private final Vec3 start,goal;private final Clearance clearance;
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::priority).thenComparingLong(Node::order));
    private final Map<Cell,Double> costs=new HashMap<>();private final Map<Cell,Cell> parents=new HashMap<>();private final Set<Cell> closed=new HashSet<>();
    private final Map<Edge,Boolean> checked=new HashMap<>();
    private Status status=Status.SEARCHING;private List<Vec3> path=List.of();private long order;private int expanded,queries;
    public HelicopterRoute(Vec3 start,Vec3 goal,Clearance clearance){
        this.start=start;this.goal=goal;this.clearance=Objects.requireNonNull(clearance);
        if(!HelicopterFlight.finite(start)||!HelicopterFlight.finite(goal)||start.distanceTo(goal)>MAX_DISTANCE){status=Status.FAILED;return;}
        // An unavailable destination cannot make this search fetch or generate it.
        queries++;if(!clearance.clear(goal,goal)){status=Status.FAILED;return;}
        if(start.distanceToSqr(goal)<=64){queries++;if(clearance.clear(start,goal)){path=List.of(goal);status=Status.READY;return;}}
        var first=new Cell(0,0,0);costs.put(first,0.0);open.add(new Node(first,0,start.distanceTo(goal),order++));
    }
    public Status status(){return status;}public List<Vec3> path(){return path;}public int expanded(){return expanded;}public int queries(){return queries;}
    public Vec3 goal(){return goal;}public Vec3 start(){return start;}
    private Vec3 point(Cell cell){return start.add(cell.x*STEP,cell.y*STEP,cell.z*STEP);}
    private boolean within(Vec3 point){return point.x>=Math.min(start.x,goal.x)-MARGIN&&point.x<=Math.max(start.x,goal.x)+MARGIN
            &&point.z>=Math.min(start.z,goal.z)-MARGIN&&point.z<=Math.max(start.z,goal.z)+MARGIN
            &&point.y>=Math.min(start.y,goal.y)-VERTICAL_MARGIN&&point.y<=Math.max(start.y,goal.y)+VERTICAL_MARGIN;}
    /** At most seven new clearance calls per expanded node; no unbounded work in one game tick. */
    public int advance(int budget){
        if(budget<0)throw new IllegalArgumentException("Negative route budget");int used=0;
        while(status==Status.SEARCHING&&used<budget){
            if(expanded>=MAX_NODES||open.isEmpty()){status=Status.FAILED;break;}
            var node=open.remove();used++;if(closed.contains(node.cell)||node.cost>costs.getOrDefault(node.cell,Double.POSITIVE_INFINITY))continue;
            closed.add(node.cell);expanded++;Vec3 at=point(node.cell);
            if(at.distanceToSqr(goal)<=64){queries++;if(clearance.clear(at,goal)){finish(node.cell);break;}}
            for(var direction:DIRECTIONS){
                var next=new Cell(node.cell.x+direction[0],node.cell.y+direction[1],node.cell.z+direction[2]);if(closed.contains(next))continue;
                Vec3 destination=point(next);if(!within(destination))continue;
                double cost=node.cost+STEP*(direction[1]==0?1:1.1);if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                var edge=new Edge(node.cell,next);var pass=checked.get(edge);
                if(pass==null){queries++;pass=clearance.clear(at,destination);checked.put(edge,pass);checked.put(new Edge(next,node.cell),pass);}
                if(!pass)continue;costs.put(next,cost);parents.put(next,node.cell);open.add(new Node(next,cost,cost+destination.distanceTo(goal),order++));
            }
        }
        return used;
    }
    private void finish(Cell last){
        var reversed=new ArrayList<Vec3>();reversed.add(goal);
        for(Cell cell=last;cell!=null;cell=parents.get(cell))if(cell.x!=0||cell.y!=0||cell.z!=0)reversed.add(point(cell));
        Collections.reverse(reversed);path=List.copyOf(reversed);status=Status.READY;
    }
}
