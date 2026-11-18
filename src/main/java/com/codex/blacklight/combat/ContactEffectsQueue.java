package com.codex.blacklight.combat;

import java.util.*;
import java.util.function.Consumer;

/** A presentation clock, never a delayed damage queue. Clear it when the client world changes. */
public final class ContactEffectsQueue<T> {
    // Adding a float directly to a long loses partial ticks in long-running worlds.
    public static double renderTime(long tick,float partial){return (double)tick+partial;}
    private record Entry<T>(long tick,long id,T value) {}
    private final List<Entry<T>> pending=new ArrayList<>();
    private final LinkedHashSet<Long> seen=new LinkedHashSet<>();
    public boolean add(double now,long tick,long id,T value){
        if(!Double.isFinite(now)||tick<0||tick>now+200||now-tick>20||seen.contains(id)||pending.size()>=128)return false;
        seen.add(id);if(seen.size()>512)seen.remove(seen.iterator().next());
        pending.add(new Entry<>(tick,id,value));return true;
    }
    public void drain(double now,Consumer<T> show){
        drain(now,value->true,show);
    }
    public void drain(double now,java.util.function.Predicate<T> visible,Consumer<T> show){
        if(!Double.isFinite(now))return;
        pending.sort(Comparator.comparingLong(Entry::tick));
        var due=new ArrayList<T>();
        pending.removeIf(e->{if(e.tick>now)return false;if(now-e.tick>20)return true;if(!visible.test(e.value))return false;due.add(e.value);return true;});
        due.forEach(show);
    }
    public void clear(){pending.clear();seen.clear();}
    public int size(){return pending.size();}
}
