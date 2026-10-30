package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VehicleWorkBudgetTest {
    private static Object budget(){try{return Class.forName("com.codex.blacklight.military.vehicle.VehicleWorkBudget").getConstructor().newInstance();}catch(ReflectiveOperationException missing){throw new AssertionError("production vehicle planning budget missing",missing);}}
    private static Object call(Object budget,String name,Class<?>[] types,Object... args){try{return budget.getClass().getMethod(name,types).invoke(budget,args);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    private static void enqueue(Object budget,UUID id,long tick){call(budget,"enqueue",new Class<?>[]{UUID.class,long.class},id,tick);}
    @SuppressWarnings("unchecked") private static List<UUID> drain(Object budget,long tick){return (List<UUID>)call(budget,"drain",new Class<?>[]{long.class},tick);}
    @Test void onlyTwoPlansPerTickEvenWhenDrainedTwice(){
        var b=budget();var ids=new ArrayList<UUID>();for(int i=0;i<24;i++){var id=new UUID(0,i);ids.add(id);enqueue(b,id,1);}
        assertEquals(ids.subList(0,2),drain(b,1));assertTrue(drain(b,1).isEmpty());
        assertEquals(ids.subList(2,4),drain(b,2));
    }
    @Test void eachVehicleWaitsTwentyTicksAndQueuedVehiclesRemainFair(){
        var b=budget();var ids=new ArrayList<UUID>();for(int i=0;i<24;i++)ids.add(new UUID(0,i));var seen=new HashSet<UUID>();var previous=new HashMap<UUID,Long>();
        for(long tick=0;tick<120;tick++){
            for(var id:ids)enqueue(b,id,tick);
            var granted=drain(b,tick);assertTrue(granted.size()<=2);
            for(var id:granted){var before=previous.put(id,tick);if(before!=null)assertTrue(tick-before>=20);seen.add(id);}
        }
        assertEquals(Set.copyOf(ids),seen);
    }
    @Test void dimensionsShareTwoTicketsWithoutConsumingAnotherDimensionsPendingVehicle(){
        var b=budget();var a=new UUID(0,1);var b1=new UUID(0,2);var b2=new UUID(0,3);
        enqueue(b,a,1);enqueue(b,b1,1);enqueue(b,b2,1);
        assertEquals(List.of(a),call(b,"drainEligible",new Class<?>[]{long.class,Collection.class},1L,List.of(a)));
        assertEquals(List.of(b1),call(b,"drainEligible",new Class<?>[]{long.class,Collection.class},1L,List.of(b1,b2)));
        assertEquals(List.of(b2),call(b,"drainEligible",new Class<?>[]{long.class,Collection.class},2L,List.of(b1,b2)));
    }
    @Test void busyFirstDimensionCannotStarveAnotherDimensionsOldestRequest(){
        var b=budget();var first=new ArrayList<UUID>();for(int i=0;i<64;i++)first.add(new UUID(0,i));var other=new UUID(1,1);boolean received=false;
        for(long tick=0;tick<80;tick++){
            for(var id:first)enqueue(b,id,tick);enqueue(b,other,tick);
            call(b,"drainEligible",new Class<?>[]{long.class,Collection.class},tick,first);
            var granted=(List<?>)call(b,"drainEligible",new Class<?>[]{long.class,Collection.class},tick,List.of(other));received|=granted.contains(other);
        }
        assertTrue(received,"a busy dimension must yield to an older request in another dimension");
    }
}
