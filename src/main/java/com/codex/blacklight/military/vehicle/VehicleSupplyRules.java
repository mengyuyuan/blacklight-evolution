package com.codex.blacklight.military.vehicle;

/** Fuel-free service thresholds. Resources remain owned by real vehicles and item stacks. */
public final class VehicleSupplyRules {
    public static java.util.Set<String> primaryAmmunition(VehicleDefinition definition) {
        return definition.weapons().values().stream().filter(w->!w.id().equals("coaxial_machine_gun")&&!w.id().equals("vehicle_rocket"))
                .map(VehicleDefinition.Weapon::ammunitionId).collect(java.util.stream.Collectors.toSet());
    }
    public static boolean lowPrimary(VehicleDefinition definition,VehicleInventory inventory) {
        long count=0,capacity=0;
        for(String kind:primaryAmmunition(definition)){count+=inventory.ammunition(kind);capacity+=definition.ammunitionCapacities().get(kind);}
        return capacity>0&&count*5<=capacity;
    }
    public static boolean emptyPrimary(VehicleDefinition definition,VehicleInventory inventory) {
        var kinds=primaryAmmunition(definition);return !kinds.isEmpty()&&kinds.stream().allMatch(kind->inventory.ammunition(kind)==0);
    }
    public static boolean needsService(VehicleDefinition definition,VehicleInventory inventory,double health) {
        return health<definition.maxHealth()*.35||lowPrimary(definition,inventory);
    }
    public static boolean readyToResume(VehicleDefinition definition,VehicleInventory inventory,double health) {
        return !resumeNeedsPrimary(definition,inventory)&&!resumeNeedsRepair(definition,health);
    }
    public static boolean resumeNeedsPrimary(VehicleDefinition definition,VehicleInventory inventory) {
        long count=0,capacity=0;
        for(String kind:primaryAmmunition(definition)){count+=inventory.ammunition(kind);capacity+=definition.ammunitionCapacities().get(kind);}
        return count*2<capacity;
    }
    public static boolean resumeNeedsRepair(VehicleDefinition definition,double health) { return health<definition.maxHealth()*.60; }
    public static int resupplyDeficit(VehicleDefinition definition,VehicleInventory inventory,double health,String item) {
        if(item.equals("vehicle_repair_parts"))return (int)Math.ceil(Math.max(0,definition.maxHealth()*.60-health)/20);
        var primary=primaryAmmunition(definition);if(!primary.contains(item))return 0;
        long count=0,capacity=0;for(String kind:primary){count+=inventory.ammunition(kind);capacity+=definition.ammunitionCapacities().get(kind);}
        return Math.min(deficit(definition,inventory,health,item),(int)Math.max(0,(capacity+1)/2-count));
    }
    public static int deficit(VehicleDefinition definition,VehicleInventory inventory,double health,String item) {
        if(item.equals("vehicle_repair_parts"))return (int)Math.ceil(Math.max(0,definition.maxHealth()-health)/20);
        return Math.max(0,definition.ammunitionCapacities().getOrDefault(item,0)-inventory.ammunition(item));
    }
    private VehicleSupplyRules() { }
}
