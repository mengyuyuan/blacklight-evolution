package com.codex.blacklight.city;

import com.codex.blacklight.infection.OutbreakRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Sparse city/district history. Only scheduled, occupied districts accrue exposure. */
public final class CityEcologyData extends SavedData {
    public static final int CELL=256, MAX_DISTRICTS=32768;
    public record Key(String city,int x,int z) {}
    public static final class District {
        private long exposure, suppression;
        private int cleared;
        private long lastTick=Long.MIN_VALUE;
        public long exposure(){return exposure;}
        public int cleared(){return cleared;}
        public long suppression(){return suppression;}
        public int stage(int global,int authored,int dayTicks){
            int local=Math.max(authored,OutbreakRules.stage(exposure,dayTicks));
            return Math.min(global,Math.max(0,local-(suppression>0?2:0)));
        }
    }
    private final Map<Key,District> districts=new HashMap<>();
    public static CityEcologyData get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(CityEcologyData::new,CityEcologyData::load),"blacklight_city_ecology");
    }
    public static Key key(String city,int x,int z){return new Key(city,Math.floorDiv(x,CELL),Math.floorDiv(z,CELL));}
    public District get(Key key){return districts.get(key);}
    public int size(){return districts.size();}
    /** Duplicate players in the same district do not accelerate it; absence creates no catch-up. */
    public District advance(Key key,long now,int scheduledTicks){
        var d=districts.get(key);
        if(d==null){if(districts.size()>=MAX_DISTRICTS)return null;d=new District();districts.put(key,d);}
        if(d.lastTick==now)return d;
        d.lastTick=now;
        int ticks=Math.clamp(scheduledTicks,0,24000);
        d.exposure=d.exposure>Long.MAX_VALUE-ticks?Long.MAX_VALUE:d.exposure+ticks;
        d.suppression=Math.max(0,d.suppression-ticks);setDirty();return d;
    }
    public void clear(Key center,long duration){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var key=new Key(center.city,center.x+x,center.z+z);
            if(!districts.containsKey(key)&&districts.size()>=MAX_DISTRICTS)continue;
            var d=districts.computeIfAbsent(key,k->new District());
            d.suppression=Math.max(d.suppression,Math.max(0,duration));
            d.cleared=Math.min(Integer.MAX_VALUE-1,d.cleared)+1;
        }
        setDirty();
    }
    public static CityEcologyData load(CompoundTag tag,HolderLookup.Provider lookup){
        var data=new CityEcologyData();
        for(var value:tag.getList("districts",Tag.TAG_COMPOUND)){
            if(data.districts.size()>=MAX_DISTRICTS)break;
            var row=(CompoundTag)value;String city=row.getString("city");
            if(city.isEmpty()||city.length()>160)continue;
            var d=new District();d.exposure=Math.max(0,row.getLong("exposure"));
            d.suppression=Math.max(0,row.getLong("suppression"));d.cleared=Math.max(0,row.getInt("cleared"));
            data.districts.put(new Key(city,row.getInt("x"),row.getInt("z")),d);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        var rows=new ListTag();
        for(var entry:districts.entrySet()){
            var key=entry.getKey();var d=entry.getValue();var row=new CompoundTag();
            row.putString("city",key.city);row.putInt("x",key.x);row.putInt("z",key.z);
            row.putLong("exposure",d.exposure);row.putLong("suppression",d.suppression);row.putInt("cleared",d.cleared);rows.add(row);
        }
        tag.putInt("schema",1);tag.put("districts",rows);return tag;
    }
}
