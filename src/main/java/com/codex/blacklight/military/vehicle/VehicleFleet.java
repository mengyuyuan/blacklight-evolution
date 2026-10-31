package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryOrder;
import java.util.*;

/** Durable relationships and intent only. Actual vehicle entities remain the only resource ledger. */
public final class VehicleFleet {
    public enum Mission { GUARD, PATROL, CLEAR_AREA, RETURN, MOVE }
    public enum Status { PARKED, ACTIVE, BLOCKED, NO_COMMANDER, NO_DRIVER, NO_FUEL, PLAYER_CONTROL, UNLOADED, COMPLETE }
    private final UUID id,formalCommander;
    private final MilitaryFaction faction;
    private final List<UUID> vehicles,units;
    private final MilitaryOrder.Point returnPoint;
    private Mission mission=Mission.GUARD;
    private Status status=Status.PARKED;
    private MilitaryOrder.Point target;
    private String personnelOrder="";
    private boolean supplyReturn;
    private boolean automaticPatrol;
    private boolean ambientPatrol;
    private int contacts,resolved;
    public VehicleFleet(UUID id,MilitaryFaction faction,List<UUID> vehicles,List<UUID> units,UUID formalCommander,MilitaryOrder.Point returnPoint){
        this.id=Objects.requireNonNull(id);this.faction=Objects.requireNonNull(faction);
        if(vehicles==null||vehicles.isEmpty()||vehicles.size()>4||new HashSet<>(vehicles).size()!=vehicles.size()||vehicles.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Fleet requires one to four distinct actual vehicles");
        this.vehicles=List.copyOf(vehicles);this.units=List.copyOf(new LinkedHashSet<>(units));this.formalCommander=Objects.requireNonNull(formalCommander);
        if(returnPoint==null||!returnPoint.finite())throw new IllegalArgumentException("Fleet needs finite return point");
        this.returnPoint=returnPoint;target=returnPoint;
    }
    public UUID id(){return id;}
    public MilitaryFaction faction(){return faction;}
    public List<UUID> vehicles(){return vehicles;}
    public List<UUID> units(){return units;}
    public UUID formalCommander(){return formalCommander;}
    public MilitaryOrder.Point returnPoint(){return returnPoint;}
    public Mission mission(){return mission;}
    public Status status(){return status;}
    public MilitaryOrder.Point target(){return target;}
    public String personnelOrder(){return personnelOrder;}
    public int contacts(){return contacts;}
    public int resolved(){return resolved;}
    public boolean supplyReturn(){return supplyReturn;}
    public boolean automaticPatrol(){return automaticPatrol;}
    public void automaticPatrol(boolean value){automaticPatrol=value;}
    public boolean ambientPatrol(){return ambientPatrol;}
    public void ambientPatrol(boolean value){ambientPatrol=value;}
    public void supplyReturn(boolean value){supplyReturn=value;}
    public boolean order(Mission mission,MilitaryOrder.Point target){
        if(mission==null||target!=null&&!target.finite()||mission==Mission.MOVE&&target==null)return false;
        this.mission=mission;this.target=mission==Mission.RETURN?returnPoint:target==null?(mission==Mission.PATROL?new MilitaryOrder.Point(returnPoint.x(),returnPoint.y(),returnPoint.z()+20):returnPoint):target;status=Status.PARKED;supplyReturn=false;return true;
    }
    public void status(Status status){this.status=Objects.requireNonNull(status);}
    public void personnelOrder(String signature){personnelOrder=signature==null?"":signature;}
    public void contact(){if(contacts<Integer.MAX_VALUE)contacts++;}
    public void resolvedContact(){if(resolved<Integer.MAX_VALUE)resolved++;}
    public void restoreResults(int contacts,int resolved){this.contacts=Math.max(0,contacts);this.resolved=Math.max(0,resolved);}
    /** The caller supplies only actual loaded eligible crew. Never edits the formal appointment. */
    public static UUID chooseCommander(UUID formal,Map<UUID,Integer> loaded){
        if(formal!=null&&loaded.containsKey(formal))return formal;
        return loaded.keySet().stream().sorted(Comparator.<UUID>comparingInt(loaded::get).reversed().thenComparing(Comparator.naturalOrder())).findFirst().orElse(null);
    }
}
