package com.codex.blacklight.firearms;

import java.util.*;

/** A bounded, transient damage cache. Replaced blocks and expired hits start fresh. */
public final class BlockDamageLedger {
    public record Result(boolean broken,int stage,float damage){}
    private record Entry(int state,float damage,long last){}
    private final int maximum;
    private final long lifetime;
    private final LinkedHashMap<Long,Entry> entries=new LinkedHashMap<>(16,.75F,true);
    private final LinkedHashSet<Long> cleared=new LinkedHashSet<>();
    public BlockDamageLedger(int maximum,long lifetime){
        if(maximum<1||lifetime<1)throw new IllegalArgumentException("Positive damage-cache bounds required");
        this.maximum=maximum;this.lifetime=lifetime;
    }
    public Result hit(long key,int state,float amount,float capacity,long now){
        if(!Float.isFinite(amount)||amount<=0||!Float.isFinite(capacity)||capacity<=0)return new Result(false,-1,0);
        expire(now);var previous=entries.get(key);
        float damage=amount+(previous!=null&&previous.state==state?previous.damage:0);
        if(damage>=capacity){entries.remove(key);cleared.add(key);return new Result(true,-1,damage);}
        if(previous==null&&entries.size()>=maximum){var oldest=entries.keySet().iterator();cleared.add(oldest.next());oldest.remove();}
        entries.put(key,new Entry(state,damage,now));
        return new Result(false,Math.max(0,Math.min(9,(int)(damage/capacity*10))),damage);
    }
    public void expire(long now){
        var it=entries.entrySet().iterator();
        while(it.hasNext()){var entry=it.next();long age=now-entry.getValue().last;if(age<0||age>=lifetime){cleared.add(entry.getKey());it.remove();}}
    }
    public void forget(long key){if(entries.remove(key)!=null)cleared.add(key);}
    public int size(){return entries.size();}
    public List<Long> drainCleared(){var result=List.copyOf(cleared);cleared.clear();return result;}
    public List<Long> keys(){return List.copyOf(entries.keySet());}
}
