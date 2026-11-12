package com.codex.blacklight.military.vehicle;

/** Minecraft target/weapon selection, not a recovered P1 native AI implementation. */
public final class VehicleFireControl {
    public static int priority(VehicleWeaponDefinition weapon,boolean armored,boolean largeOrGroup){
        return switch(weapon.damageClass()){
            case AP -> armored?0:4;
            case EXPLOSIVE -> armored?1:largeOrGroup?0:3;
            case AUTOCANNON -> armored?2:largeOrGroup?1:1;
            case BULLET -> armored?3:largeOrGroup?2:0;
        };
    }
    private VehicleFireControl(){}
}
