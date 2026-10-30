package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.military.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension indices. Missing/unloaded vehicle UUIDs are retained and never reissued here. */
public final class VehicleFleetSavedData extends SavedData {
    public static final String KEY="blacklight_vehicle_fleets";
    public static final int MAX_FLEETS=128;
    private final Map<UUID,VehicleFleet> fleets=new LinkedHashMap<>();
    private final Map<UUID,UUID> vehicleIndex=new HashMap<>();
    public VehicleFleetSavedData(){}
    public static VehicleFleetSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(VehicleFleetSavedData::new,VehicleFleetSavedData::load),KEY);}
    public List<VehicleFleet> fleets(){return List.copyOf(fleets.values());}
    public Optional<VehicleFleet> fleet(UUID id){return Optional.ofNullable(fleets.get(id));}
    public Optional<VehicleFleet> vehicleFleet(UUID vehicle){return fleet(vehicleIndex.get(vehicle));}
    public boolean add(VehicleFleet fleet){
        if(fleet==null||fleets.size()>=MAX_FLEETS||fleets.containsKey(fleet.id())||fleet.vehicles().stream().anyMatch(vehicleIndex::containsKey))return false;
        fleets.put(fleet.id(),fleet);fleet.vehicles().forEach(vehicle->vehicleIndex.put(vehicle,fleet.id()));setDirty();return true;
    }
    public boolean remove(UUID id){
        var old=fleets.remove(id);if(old==null)return false;old.vehicles().forEach(vehicleIndex::remove);setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("Schema",1);var list=new ListTag();
        for(var fleet:fleets.values()){
            var entry=new CompoundTag();entry.putUUID("Id",fleet.id());entry.putString("Faction",fleet.faction().id());entry.putUUID("Commander",fleet.formalCommander());
            entry.put("Vehicles",ids(fleet.vehicles()));entry.put("Units",ids(fleet.units()));entry.put("Return",point(fleet.returnPoint()));entry.put("Target",point(fleet.target()));
            entry.putString("Mission",fleet.mission().name());entry.putString("Status",fleet.status().name());entry.putString("PersonnelOrder",fleet.personnelOrder());
            if(fleet.supplyReturn())entry.putBoolean("SupplyReturn",true);
            if(fleet.automaticPatrol())entry.putBoolean("AutomaticPatrol",true);
            entry.putInt("Contacts",fleet.contacts());entry.putInt("Resolved",fleet.resolved());list.add(entry);
        }
        tag.put("Fleets",list);return tag;
    }
    private static ListTag ids(List<UUID> ids){var list=new ListTag();for(var id:ids){var entry=new CompoundTag();entry.putUUID("Id",id);list.add(entry);}return list;}
    private static CompoundTag point(MilitaryOrder.Point point){var tag=new CompoundTag();if(point!=null){tag.putDouble("X",point.x());tag.putDouble("Y",point.y());tag.putDouble("Z",point.z());}return tag;}
    private static MilitaryOrder.Point readPoint(CompoundTag tag){
        if(!tag.contains("X",99)||!tag.contains("Y",99)||!tag.contains("Z",99))throw new IllegalArgumentException("Missing finite fleet point");
        var result=new MilitaryOrder.Point(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));if(!result.finite())throw new IllegalArgumentException("Invalid fleet point");return result;
    }
    private static List<UUID> readIds(ListTag list,int maximum){
        if(list.size()>maximum)throw new IllegalArgumentException("Unbounded fleet index");var result=new ArrayList<UUID>();
        for(int i=0;i<list.size();i++){var entry=list.getCompound(i);if(!entry.hasUUID("Id"))throw new IllegalArgumentException("Missing fleet reference");result.add(entry.getUUID("Id"));}return result;
    }
    public static VehicleFleetSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new VehicleFleetSavedData();if(tag.getInt("Schema")!=1)return data;
        var list=tag.getList("Fleets",10);
        for(int i=0;i<list.size()&&i<MAX_FLEETS;i++)try{
            var entry=list.getCompound(i);if(!entry.hasUUID("Id")||!entry.hasUUID("Commander"))continue;
            var faction=MilitaryFaction.parse(entry.getString("Faction")).orElseThrow();
            var fleet=new VehicleFleet(entry.getUUID("Id"),faction,readIds(entry.getList("Vehicles",10),4),readIds(entry.getList("Units",10),64),entry.getUUID("Commander"),readPoint(entry.getCompound("Return")));
            if(!fleet.order(VehicleFleet.Mission.valueOf(entry.getString("Mission")),readPoint(entry.getCompound("Target"))))continue;
            fleet.supplyReturn(entry.getBoolean("SupplyReturn"));
            fleet.automaticPatrol(entry.getBoolean("AutomaticPatrol"));
            fleet.status(VehicleFleet.Status.valueOf(entry.getString("Status")));fleet.personnelOrder(entry.getString("PersonnelOrder"));fleet.restoreResults(entry.getInt("Contacts"),entry.getInt("Resolved"));data.add(fleet);
        }catch(IllegalArgumentException|NoSuchElementException invalid){data.setDirty();}
        return data;
    }
}
