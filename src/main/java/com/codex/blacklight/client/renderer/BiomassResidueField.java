package com.codex.blacklight.client.renderer;

import java.util.Collection;
import java.util.LinkedHashMap;

/** Bounded, transient visual marks. No world blocks, saved data, or player state are changed. */
final class BiomassResidueField {
    static final int LIFETIME=90, LIMIT=2048;
    record Key(int x,int z,long floor) {}
    record Mark(Key key,double y,long born,int light,int rgb) {
        double remaining(double now){return Math.max(0,Math.min(1,(LIFETIME-(now-born))/35D));}
    }
    private final LinkedHashMap<Key,Mark> marks=new LinkedHashMap<>();
    void put(int x,int z,double y,long now,int light,int rgb){
        Key key=new Key(x,z,Math.round(y*1024));
        marks.remove(key);marks.put(key,new Mark(key,y,now,light,rgb));
        while(marks.size()>LIMIT)marks.remove(marks.keySet().iterator().next());
    }
    void expire(long now){marks.values().removeIf(m->now<m.born||now-m.born>=LIFETIME);}
    Collection<Mark> marks(){return marks.values();}
    void clear(){marks.clear();}
}
