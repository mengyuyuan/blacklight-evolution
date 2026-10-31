package com.codex.blacklight.military.vehicle;

import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.military.*;
import java.util.*;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class VehicleFleetSavedDataTest {
    @Test void optionalSupplyReturnSurvivesRealNbtRoundTripAndOldSavesRemainUnlatched(){
        var data=new VehicleFleetSavedData();var id=UUID.randomUUID();
        var fleet=new VehicleFleet(id,MilitaryFaction.MILITARY,List.of(UUID.randomUUID()),List.of(),UUID.randomUUID(),new MilitaryOrder.Point(1,81,2));
        assertTrue(fleet.order(VehicleFleet.Mission.MOVE,new MilitaryOrder.Point(30,81,40)));fleet.supplyReturn(true);assertTrue(data.add(fleet));
        var tag=data.save(new CompoundTag());var restored=VehicleFleetSavedData.load(tag).fleet(id).orElseThrow();
        assertTrue(restored.supplyReturn(),"a partially supplied return must stay latched after the trigger clears and the process restarts");
        assertEquals(VehicleFleet.Mission.MOVE,restored.mission());assertEquals(fleet.target(),restored.target());
        assertFalse(tag.toString().contains("Fuel"));assertFalse(tag.toString().contains("Ammo"));assertFalse(tag.toString().contains("Health"));
        tag.getList("Fleets",10).getCompound(0).remove("SupplyReturn");
        assertFalse(VehicleFleetSavedData.load(tag).fleet(id).orElseThrow().supplyReturn());
        assertTrue(restored.order(VehicleFleet.Mission.GUARD,null));assertFalse(restored.supplyReturn(),"an explicit new order supersedes the temporary return");
    }
    private static Object data(){try{return Class.forName("com.codex.blacklight.military.vehicle.VehicleFleetSavedData").getConstructor().newInstance();}catch(ReflectiveOperationException failure){throw new AssertionError("production fleet saved index missing",failure);}}
    private static Object call(Object data,String method,Class<?>[] types,Object... values){try{return data.getClass().getMethod(method,types).invoke(data,values);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    @Test void actualSavedDataRoundTripKeepsIdentityMissionAndOnlyOneVehicleIndex(){
        var data=data();var vehicle=UUID.randomUUID();var id=UUID.randomUUID();var officer=UUID.randomUUID();var unit=UUID.randomUUID();
        var fleet=new VehicleFleet(id,MilitaryFaction.MILITARY,List.of(vehicle),List.of(unit),officer,new MilitaryOrder.Point(1,81,2));
        assertTrue(fleet.order(VehicleFleet.Mission.CLEAR_AREA,new MilitaryOrder.Point(20,81,30)));
        assertEquals(true,call(data,"add",new Class<?>[]{VehicleFleet.class},fleet));
        var conflicting=new VehicleFleet(UUID.randomUUID(),MilitaryFaction.MILITARY,List.of(vehicle),List.of(),officer,new MilitaryOrder.Point(1,81,2));
        assertEquals(false,call(data,"add",new Class<?>[]{VehicleFleet.class},conflicting));
        var tag=(CompoundTag)call(data,"save",new Class<?>[]{CompoundTag.class},new CompoundTag());
        assertFalse(tag.toString().contains("Fuel"));assertFalse(tag.toString().contains("Ammo"));assertFalse(tag.toString().contains("Health"));
        Object restored;
        try{restored=data.getClass().getMethod("load",CompoundTag.class).invoke(null,tag);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        var result=(Optional<?>)call(restored,"fleet",new Class<?>[]{UUID.class},id);var read=(VehicleFleet)result.orElseThrow();
        assertEquals(id,read.id());assertEquals(officer,read.formalCommander());assertEquals(List.of(vehicle),read.vehicles());assertEquals(List.of(unit),read.units());assertEquals(VehicleFleet.Mission.CLEAR_AREA,read.mission());assertEquals(fleet.target(),read.target());
    }
    @Test void oldWorldWithoutFleetTagRemainsEmptyAndIssuesNoVehicles(){
        var data=data();Object restored;
        try{restored=data.getClass().getMethod("load",CompoundTag.class).invoke(null,new CompoundTag());}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        assertEquals(List.of(),call(restored,"fleets",new Class<?>[]{}));
    }
}
