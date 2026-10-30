package com.codex.blacklight.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded, dimension-local records. UUID membership is server-owned; tags are not authority. */
public final class MilitaryCampaignData extends SavedData {
    static final int MAX_BASES=2048, MAX_OPERATIONS=8, MAX_PLANS=128, MAX_CLEARS=32768;
    enum Kind { COLONY, HIVE, CITY_HIVE, PATROL }
    enum Phase { ADVANCE, RETURN }
    static final class Base {
        int cooldown,sabotage;
        boolean destroyed,destructionRewarded,infiltrationRewarded;
        UUID actor;
    }
    static final class Operation {
        final UUID id;final String base;final Kind kind;final UUID entity;final int hive;
        BlockPos target,origin;Phase phase=Phase.ADVANCE;int work,returnWork,absent;
        boolean expedition;
        boolean objectiveKilled;String result="";
        final List<UUID> members=new ArrayList<>();final Set<UUID> lost=new HashSet<>();
        Operation(UUID id,String base,Kind kind,BlockPos target,UUID entity,int hive){this.id=id;this.base=base;this.kind=kind;this.target=target.immutable();this.origin=this.target;this.entity=entity;this.hive=hive;}
    }
    record Infiltration(String base,int remaining) {}
    final Map<String,Base> bases=new LinkedHashMap<>();
    final Map<UUID,Operation> operations=new LinkedHashMap<>();
    final Map<UUID,Infiltration> infiltration=new LinkedHashMap<>();
    final Map<UUID,BlockPos> hives=new LinkedHashMap<>();
    final Set<Long> cleared=new LinkedHashSet<>();
    public static MilitaryCampaignData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(MilitaryCampaignData::load,MilitaryCampaignData::new,"blacklight_military_campaign");}
    Base base(String id){
        if(id==null||id.isBlank()||id.length()>160)return null;
        if(!bases.containsKey(id)&&bases.size()>=MAX_BASES)return null;
        return bases.computeIfAbsent(id,k->new Base());
    }
    boolean rememberClear(BlockPos at){
        if(cleared.contains(at.asLong()))return false;
        if(cleared.size()>=MAX_CLEARS)cleared.remove(cleared.iterator().next());
        cleared.add(at.asLong());setDirty();return true;
    }
    static MilitaryCampaignData load(CompoundTag tag){
        var d=new MilitaryCampaignData();
        for(var entry:tag.getList("bases",Tag.TAG_COMPOUND)){
            if(d.bases.size()>=MAX_BASES)break;
            var row=(CompoundTag)entry;var b=d.base(row.getString("id"));if(b==null)continue;
            b.cooldown=com.codex.blacklight.forge.WingMath.clamp(row.getInt("cooldown"),0,2400000);b.sabotage=com.codex.blacklight.forge.WingMath.clamp(row.getInt("sabotage"),0,2400000);
            b.destroyed=row.getBoolean("destroyed");b.destructionRewarded=row.getBoolean("destructionRewarded");b.infiltrationRewarded=row.getBoolean("infiltrationRewarded");
            if(row.hasUUID("actor"))b.actor=row.getUUID("actor");
        }
        var assigned=new HashSet<UUID>();
        for(var entry:tag.getList("operations",Tag.TAG_COMPOUND)){
            if(d.operations.size()>=MAX_OPERATIONS)break;var row=(CompoundTag)entry;
            if(!row.hasUUID("id")||d.operations.containsKey(row.getUUID("id"))||!row.contains("target",Tag.TAG_LONG))continue;
            String base=row.getString("base");if(!d.bases.containsKey(base))continue;
            int kind=row.getInt("kind");if(kind<0||kind>=Kind.values().length)continue;
            UUID entity=row.hasUUID("entity")?row.getUUID("entity"):null;int hive=row.getInt("hive");
            if(kind==Kind.HIVE.ordinal()&&entity==null||kind==Kind.CITY_HIVE.ordinal()&&(hive<0||hive>1))continue;
            var op=new Operation(row.getUUID("id"),base,Kind.values()[kind],BlockPos.of(row.getLong("target")),entity,hive);
            op.origin=row.contains("origin",Tag.TAG_LONG)?BlockPos.of(row.getLong("origin")):op.target;
            op.expedition=row.getBoolean("expedition");op.absent=com.codex.blacklight.forge.WingMath.clamp(row.getInt("absent"),0,1200);
            op.phase=row.getInt("phase")==Phase.RETURN.ordinal()?Phase.RETURN:Phase.ADVANCE;
            op.work=com.codex.blacklight.forge.WingMath.clamp(row.getInt("work"),0,240000);op.returnWork=com.codex.blacklight.forge.WingMath.clamp(row.getInt("returnWork"),0,240000);
            op.objectiveKilled=row.getBoolean("killed");op.result=row.getString("result");
            for(var value:row.getList("members",Tag.TAG_COMPOUND)){
                if(op.members.size()>=8)break;var member=(CompoundTag)value;
                if(!member.hasUUID("id"))continue;UUID id=member.getUUID("id");
                if(!op.members.contains(id)&&assigned.add(id)){op.members.add(id);if(member.getBoolean("lost"))op.lost.add(id);}
            }
            if(!op.members.isEmpty()&&!d.operations.containsKey(op.id))d.operations.put(op.id,op);
        }
        for(var entry:tag.getList("infiltration",Tag.TAG_COMPOUND)){
            if(d.infiltration.size()>=MAX_PLANS)break;var row=(CompoundTag)entry;
            String base=row.getString("base");int left=com.codex.blacklight.forge.WingMath.clamp(row.getInt("remaining"),0,12000);
            if(row.hasUUID("player")&&d.bases.containsKey(base)&&left>0)d.infiltration.put(row.getUUID("player"),new Infiltration(base,left));
        }
        for(long pos:tag.getLongArray("cleared")){if(d.cleared.size()>=MAX_CLEARS)break;d.cleared.add(pos);}
        for(var entry:tag.getList("hives",Tag.TAG_COMPOUND)){if(d.hives.size()>=MAX_BASES)break;var row=(CompoundTag)entry;if(row.hasUUID("id")&&row.contains("pos",Tag.TAG_LONG))d.hives.put(row.getUUID("id"),BlockPos.of(row.getLong("pos")));}
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var bs=new ListTag();for(var entry:bases.entrySet()){
            var b=entry.getValue();var row=new CompoundTag();row.putString("id",entry.getKey());row.putInt("cooldown",b.cooldown);row.putInt("sabotage",b.sabotage);
            row.putBoolean("destroyed",b.destroyed);row.putBoolean("destructionRewarded",b.destructionRewarded);row.putBoolean("infiltrationRewarded",b.infiltrationRewarded);
            if(b.actor!=null)row.putUUID("actor",b.actor);bs.add(row);
        }tag.put("bases",bs);
        var ops=new ListTag();for(var op:operations.values()){
            var row=new CompoundTag();row.putUUID("id",op.id);row.putString("base",op.base);row.putInt("kind",op.kind.ordinal());row.putLong("target",op.target.asLong());
            row.putLong("origin",op.origin.asLong());row.putBoolean("expedition",op.expedition);row.putInt("absent",op.absent);
            if(op.entity!=null)row.putUUID("entity",op.entity);row.putInt("hive",op.hive);row.putInt("phase",op.phase.ordinal());row.putInt("work",op.work);row.putInt("returnWork",op.returnWork);
            row.putBoolean("killed",op.objectiveKilled);row.putString("result",op.result);
            var members=new ListTag();for(var id:op.members){var m=new CompoundTag();m.putUUID("id",id);m.putBoolean("lost",op.lost.contains(id));members.add(m);}row.put("members",members);ops.add(row);
        }tag.put("operations",ops);
        var plans=new ListTag();for(var entry:infiltration.entrySet()){var row=new CompoundTag();row.putUUID("player",entry.getKey());row.putString("base",entry.getValue().base());row.putInt("remaining",entry.getValue().remaining());plans.add(row);}tag.put("infiltration",plans);
        var hs=new ListTag();for(var entry:hives.entrySet()){var row=new CompoundTag();row.putUUID("id",entry.getKey());row.putLong("pos",entry.getValue().asLong());hs.add(row);}tag.put("hives",hs);
        tag.putLongArray("cleared",cleared.stream().mapToLong(Long::longValue).toArray());tag.putInt("schema",1);return tag;
    }
}
