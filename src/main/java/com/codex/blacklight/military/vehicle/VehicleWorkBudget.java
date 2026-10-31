package com.codex.blacklight.military.vehicle;

import java.util.*;

/** Separate from infantry work. Repeated requests keep FIFO position and share one tick allowance. */
public final class VehicleWorkBudget {
    public static final int PLANS_PER_TICK=2, PLAN_INTERVAL=20, MAX_NODES=2048, SEARCH_RADIUS=96;
    private final LinkedHashMap<UUID,Long> pending=new LinkedHashMap<>();
    private final Map<UUID,Long> granted=new HashMap<>();
    private long clock=Long.MIN_VALUE,totalPlans,totalNodes;
    private int used,lastSearchNodes,maxSearchNodes;
    private void advance(long now){
        if(clock==now)return;
        if(now<clock){granted.clear();pending.clear();}
        clock=now;used=0;
        granted.entrySet().removeIf(entry->now-entry.getValue()>200);
    }
    public void enqueue(UUID id,long now){if(id==null)return;advance(now);pending.put(id,now);}
    public List<UUID> drain(long now){return drainEligible(now,null);}
    public List<UUID> drainEligible(long now,Collection<UUID> eligible){
        advance(now);var result=new ArrayList<UUID>();var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();if(now-entry.getValue()>100){iterator.remove();continue;}
            if(used>=PLANS_PER_TICK)break;
            var previous=granted.get(entry.getKey());if(previous!=null&&now-previous<PLAN_INTERVAL)continue;
            // A later dimension will receive its own oldest ticket in this same server tick.
            if(eligible!=null&&!eligible.contains(entry.getKey()))break;
            result.add(entry.getKey());granted.put(entry.getKey(),now);used++;totalPlans++;iterator.remove();
        }
        return List.copyOf(result);
    }
    public void recordSearch(int nodes){
        if(nodes<0||nodes>MAX_NODES)throw new IllegalArgumentException("Search exceeded vehicle node budget");
        lastSearchNodes=nodes;maxSearchNodes=Math.max(maxSearchNodes,nodes);totalNodes+=nodes;
    }
    public void cancel(UUID id){pending.remove(id);}
    public int plansThisTick(){return used;}
    public int lastSearchNodes(){return lastSearchNodes;}
    public int maxSearchNodes(){return maxSearchNodes;}
    public long totalPlans(){return totalPlans;}
    public long totalNodes(){return totalNodes;}
}
