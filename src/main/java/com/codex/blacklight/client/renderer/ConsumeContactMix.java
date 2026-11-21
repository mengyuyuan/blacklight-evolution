package com.codex.blacklight.client.renderer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** One audible impact per execution and beat, shared by the pose and network paths. */
final class ConsumeContactMix<T> {
    record Strike(int victim,int owner,long started,int ordinal){}
    private record Fallback<T>(T cue,long due){}
    private final Map<Strike,Long> heard=new HashMap<>();
    private final Map<Strike,Fallback<T>> pending=new HashMap<>();
    void clear(){heard.clear();pending.clear();}
    void server(Strike key,T cue,long now){
        if(!heard.containsKey(key))pending.putIfAbsent(key,new Fallback<>(cue,now+2));
    }
    void local(Strike key,T cue,long now,Consumer<T> output){
        pending.remove(key);
        if(heard.putIfAbsent(key,now)==null)output.accept(cue);
    }
    void tick(long now,Consumer<T> output){
        for(var it=pending.entrySet().iterator();it.hasNext();){
            var en=it.next();var f=en.getValue();
            if(now<f.due())continue;
            if(heard.putIfAbsent(en.getKey(),now)==null)output.accept(f.cue());
            it.remove();
        }
        heard.values().removeIf(t->now<t||now-t>240);
    }
}
