package com.codex.blacklight.military;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One authoritative tree per dimension. Only values and UUIDs are stored. */
public final class MilitarySavedData extends SavedData {
    public static final String KEY="blacklight_military";
    private MilitaryOrganization organization=new MilitaryOrganization();
    private final Set<UUID> automatic=new HashSet<>();
    private final java.util.Map<UUID,Boolean> manualCommanders=new LinkedHashMap<>();
    private long savedRevision;

    public static MilitarySavedData get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(MilitarySavedData::load,MilitarySavedData::new,KEY);
    }
    public MilitaryOrganization organization(){return organization;}
    public boolean automatic(UUID unit){return automatic.contains(unit);}
    public void automatic(UUID unit,boolean enabled){
        if(enabled?automatic.add(unit):automatic.remove(unit))setDirty();
        if(organization.unit(unit).isEmpty()&&manualCommanders.remove(unit)!=null)setDirty();
    }
    /** Legacy nonautomatic units retain historical appointments unless explicitly marked otherwise. */
    public boolean commanderManual(UUID unit){return manualCommanders.getOrDefault(unit,!automatic(unit));}
    public void setCommanderManual(UUID unit,boolean manual){
        if(organization.unit(unit).isEmpty())return;
        var previous=manualCommanders.put(unit,manual);if(previous==null||previous!=manual)setDirty();
    }
    public void replace(MilitaryOrganization next){
        organization=next;
        automatic.removeIf(id->organization.unit(id).isEmpty());
        manualCommanders.keySet().removeIf(id->organization.unit(id).isEmpty());
        setDirty();
    }
    @Override public boolean isDirty(){return super.isDirty()||organization.revision()!=savedRevision;}

    @Override public CompoundTag save(CompoundTag tag){
        tag.putInt("Schema",1);var entries=new ListTag();
        for(var unit:organization.units()){
            var entry=new CompoundTag();entry.putUUID("Id",unit.id());entry.putString("Faction",unit.faction().id());
            entry.putString("Type",unit.type().id());entry.putString("Name",unit.name());entry.putInt("Capacity",unit.capacity());
            entry.putDouble("X",unit.origin().x());entry.putDouble("Y",unit.origin().y());entry.putDouble("Z",unit.origin().z());
            if(unit.parent()!=null)entry.putUUID("Parent",unit.parent());
            if(unit.commander()!=null)entry.putUUID("Commander",unit.commander());
            entry.putBoolean("CommanderManual",commanderManual(unit.id()));
            var members=new ListTag();
            for(var id:unit.members().keySet().stream().sorted().toList()){
                var member=new CompoundTag();member.putUUID("Id",id);member.putString("Rank",unit.members().get(id).id());members.add(member);
            }
            entry.put("Members",members);
            if(unit.order()!=null){
                var order=new CompoundTag();order.putString("Mode",unit.order().mode().name());
                var target=unit.order().target();if(target!=null){order.putDouble("X",target.x());order.putDouble("Y",target.y());order.putDouble("Z",target.z());}
                entry.put("Order",order);
            }
            entries.add(entry);
        }
        tag.put("Units",entries);var auto=new ListTag();
        for(var id:automatic.stream().sorted().toList())if(organization.unit(id).isPresent()){
            var entry=new CompoundTag();entry.putUUID("Id",id);auto.add(entry);
        }
        tag.put("Automatic",auto);savedRevision=organization.revision();return tag;
    }

    public static MilitarySavedData load(CompoundTag tag){
        var data=new MilitarySavedData();var entries=new LinkedHashMap<UUID,CompoundTag>();var list=tag.getList("Units",10);
        boolean repaired=false;
        for(int i=0;i<list.size()&&i<MilitaryOrganization.MAX_UNITS;i++){
            var entry=list.getCompound(i);var faction=MilitaryFaction.parse(entry.getString("Faction"));var type=MilitaryUnitType.parse(entry.getString("Type"));
            if(!entry.hasUUID("Id")||faction.isEmpty()||type.isEmpty()||!entry.contains("X",99)||!entry.contains("Y",99)||!entry.contains("Z",99)){repaired=true;continue;}
            var id=entry.getUUID("Id");
            if(data.organization.create(id,faction.get(),type.get(),entry.getString("Name"),entry.getInt("Capacity"),
                    new MilitaryOrder.Point(entry.getDouble("X"),entry.getDouble("Y"),entry.getDouble("Z"))))entries.put(id,entry);
            else repaired=true;
        }
        for(var item:entries.entrySet()){
            var members=item.getValue().getList("Members",10);
            for(int i=0;i<members.size()&&i<MilitaryOrganization.MAX_LEAF;i++){
                var member=members.getCompound(i);
                if(!member.hasUUID("Id")||!data.organization.assign(item.getKey(),member.getUUID("Id"),MilitaryRank.fromId(member.getString("Rank"))))repaired=true;
            }
        }
        for(var item:entries.entrySet()){
            var entry=item.getValue();
            if(entry.hasUUID("Parent")&&!data.organization.attach(entry.getUUID("Parent"),item.getKey()))repaired=true;
        }
        for(var item:entries.entrySet()){
            var entry=item.getValue();
            if(entry.hasUUID("Commander")&&!data.organization.appoint(item.getKey(),entry.getUUID("Commander")))repaired=true;
            if(entry.contains("CommanderManual"))data.manualCommanders.put(item.getKey(),entry.getBoolean("CommanderManual"));
            if(entry.contains("Order",10)){
                var order=entry.getCompound("Order");
                try{
                    var mode=MilitaryOrder.Mode.valueOf(order.getString("Mode"));
                    var point=order.contains("X",99)&&order.contains("Y",99)&&order.contains("Z",99)
                            ?new MilitaryOrder.Point(order.getDouble("X"),order.getDouble("Y"),order.getDouble("Z")):null;
                    if(!data.organization.order(item.getKey(),new MilitaryOrder(mode,point)))repaired=true;
                }catch(IllegalArgumentException ignored){repaired=true;}
            }
        }
        var auto=tag.getList("Automatic",10);
        for(int i=0;i<auto.size()&&i<MilitaryOrganization.MAX_UNITS;i++){
            var entry=auto.getCompound(i);if(entry.hasUUID("Id")&&data.organization.unit(entry.getUUID("Id")).isPresent())data.automatic.add(entry.getUUID("Id"));
        }
        data.savedRevision=data.organization.revision();data.setDirty(repaired);return data;
    }
}
