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
        private long threatWork=-1,lastObservation=Long.MIN_VALUE;
        private DistrictThreat.Zone zone=DistrictThreat.Zone.GREEN;
        public long exposure(){return exposure;}
        public int cleared(){return cleared;}
        public long suppression(){return suppression;}
        public boolean observed(){return threatWork>=0;}
        public int pressure(){return (int)(Math.max(0,threatWork)/20);}
        public DistrictThreat.Zone zone(){return zone;}
        public int stage(int global,int authored,int dayTicks){
            int local=Math.max(authored,OutbreakRules.stage(exposure,dayTicks));
            if(observed())return Math.min(global,Math.max(0,DistrictThreat.stage(zone(),4,local)-(suppression>0?2:0)));
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
    /** Observations are scheduled work, never elapsed unloaded or offline time. */
    public District observe(Key key,long now,int ticks,int authored,int dayTicks,DistrictThreat.Signal signal){
        var previous=districts.get(key);long previousExposure=previous==null?0:previous.exposure;
        var d=advance(key,now,ticks);if(d==null||d.lastObservation==now)return d;
        d.lastObservation=now;
        if(!d.observed()){
            int initial=DistrictThreat.seed(Math.max(authored,OutbreakRules.stage(previousExposure,dayTicks)));
            d.threatWork=initial*20L;d.zone=DistrictThreat.zone(DistrictThreat.Zone.GREEN,initial);
        }
        d.threatWork=DistrictThreat.advance(d.threatWork,ticks,signal);
        d.zone=DistrictThreat.zone(d.zone,d.pressure());setDirty();return d;
    }
    /** Another vial can establish a new district, but cannot reset its clock. */
    public void release(Key key){
        if(!districts.containsKey(key)&&districts.size()>=MAX_DISTRICTS)return;
        var d=districts.computeIfAbsent(key,k->new District());
        d.threatWork=Math.max(Math.max(0,d.threatWork),2400*20L);
        d.zone=DistrictThreat.zone(d.zone,d.pressure());setDirty();
    }
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
    /** A confirmed physical source removal, not a timer or nearby kill counter. */
    public void purge(Key center,long duration,int reduction,boolean secured,int securedPressure){
        clear(center,duration);
        var district=districts.get(center);if(district==null)return;
        district.threatWork=Math.max(0,Math.max(0,district.threatWork)-Math.clamp(reduction,0,DistrictThreat.MAXIMUM)*20L);
        if(secured)district.threatWork=Math.min(district.threatWork,Math.clamp(securedPressure,0,DistrictThreat.YELLOW-401)*20L);
        district.zone=DistrictThreat.zone(district.zone,district.pressure());setDirty();
    }
    public static CityEcologyData load(CompoundTag tag,HolderLookup.Provider lookup){
        var data=new CityEcologyData();
        for(var value:tag.getList("districts",Tag.TAG_COMPOUND)){
            if(data.districts.size()>=MAX_DISTRICTS)break;
            var row=(CompoundTag)value;String city=row.getString("city");
            if(city.isEmpty()||city.length()>160)continue;
            var d=new District();d.exposure=Math.max(0,row.getLong("exposure"));
            d.suppression=Math.max(0,row.getLong("suppression"));d.cleared=Math.max(0,row.getInt("cleared"));
            if(row.contains("threatWork",Tag.TAG_LONG)){
                d.threatWork=Math.clamp(row.getLong("threatWork"),0,DistrictThreat.MAXIMUM*20L);
                int saved=Math.clamp(row.getInt("zone"),0,2);
                d.zone=DistrictThreat.zone(DistrictThreat.Zone.values()[saved],d.pressure());
            }
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
            if(d.observed()){row.putLong("threatWork",d.threatWork);row.putInt("zone",d.zone.ordinal());}
        }
        tag.putInt("schema",2);tag.put("districts",rows);return tag;
    }
}
