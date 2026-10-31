package com.codex.blacklight.city;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** A spawn slot is issued once, including after its soldier dies or unloads. */
public final class CityMilitaryData extends SavedData {
    public static final class Base {
        public final String id;public final CityMilitaryPlan.Site site;public final UUID[] slots;public int losses;public CityMilitaryPlan.Checkpoint checkpoint;
        MilitaryBaseHealth health=new MilitaryBaseHealth(false);
        Base(String id,CityMilitaryPlan.Site site){this.id=id;this.site=site;slots=new UUID[LargeBaseBlueprint.get(site.blackwatch()).guards().size()];}
    }
    final Map<String,Base> bases=new LinkedHashMap<>();
    static CityMilitaryData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(CityMilitaryData::load,CityMilitaryData::new,"blacklight_city_military");}
    static CityMilitaryData load(CompoundTag tag){
        var data=new CityMilitaryData();var entries=tag.getList("Bases",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size()&&data.bases.size()<2048;i++){
            var b=entries.getCompound(i);String id=b.getString("Id");if(id.isBlank())continue;
            var site=new CityMilitaryPlan.Site(b.getInt("X"),b.getInt("Z"),b.getInt("Y"),Math.floorMod(b.getInt("Turn"),4),b.getBoolean("Blackwatch"));
            var base=new Base(id,site);base.losses=b.getInt("Losses");base.health=new MilitaryBaseHealth(b.getBoolean("CoreInactive"));var slots=b.getList("Slots",Tag.TAG_COMPOUND);
            if(b.contains("Checkpoint")){var c=b.getCompound("Checkpoint");base.checkpoint=new CityMilitaryPlan.Checkpoint(c.getInt("X"),c.getInt("Z"),c.getInt("Y"),Math.floorMod(c.getInt("Turn"),4));}
            for(int j=0;j<slots.size();j++){var slot=slots.getCompound(j);int n=slot.getInt("Index");if(n>=0&&n<base.slots.length&&slot.hasUUID("UUID"))base.slots[n]=slot.getUUID("UUID");}
            data.bases.put(id,base);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var entries=new ListTag();for(var base:bases.values()){
            var b=new CompoundTag();b.putString("Id",base.id);b.putInt("X",base.site.x());b.putInt("Z",base.site.z());b.putInt("Y",base.site.floor());
            b.putInt("Turn",base.site.rotation());b.putBoolean("Blackwatch",base.site.blackwatch());b.putInt("Losses",base.losses);b.putBoolean("CoreInactive",base.health.inactive());var slots=new ListTag();
            for(int i=0;i<base.slots.length;i++)if(base.slots[i]!=null){var slot=new CompoundTag();slot.putInt("Index",i);slot.putUUID("UUID",base.slots[i]);slots.add(slot);}
            b.put("Slots",slots);entries.add(b);
            if(base.checkpoint!=null){var c=new CompoundTag();c.putInt("X",base.checkpoint.x());c.putInt("Y",base.checkpoint.floor());c.putInt("Z",base.checkpoint.z());c.putInt("Turn",base.checkpoint.rotation());b.put("Checkpoint",c);}
        }tag.put("Bases",entries);return tag;
    }
}
