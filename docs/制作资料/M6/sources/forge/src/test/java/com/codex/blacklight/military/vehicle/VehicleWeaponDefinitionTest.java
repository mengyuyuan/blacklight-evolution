package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.firearms.WeaponDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleWeaponDefinitionTest {
    @Test void fiveExplicitWeaponsMatchActualAmmoAndCooldownStorage(){
        assertEquals(5,VehicleWeaponDefinition.ALL.size());
        for(String chassis:new String[]{"apc","tank"})for(var stored:VehicleCatalog.loadDefault().require(chassis).weapons().values()){
            var runtime=VehicleWeaponDefinition.require(stored.id()).weapon();
            assertEquals(stored.ammunitionId(),runtime.ammoId());assertEquals(stored.cooldownTicks(),runtime.cooldownTicks());
        }
    }
    @Test void sameKindHeAndApRetainDistinctIdentityAndImpact(){
        var he=VehicleWeaponDefinition.require("tank_he").weapon();var ap=VehicleWeaponDefinition.require("tank_ap").weapon();
        assertEquals(he.kind(),ap.kind());assertNotEquals(he.id(),ap.id());
        assertEquals(24,he.damage());assertEquals(36,ap.damage());assertEquals(4,he.radius());assertTrue(ap.radius()<he.radius());
    }
    @Test void unknownIdCannotBecomeAnotherLoadedGun(){
        assertThrows(IllegalArgumentException.class,()->VehicleWeaponDefinition.require("missing"));
    }
    @Test void heavyArmorResistsRifleButDoesNotEraseAp(){
        assertEquals(.4F,VehicleWeaponDefinition.require("coaxial_machine_gun").mechanicalDamage(4,VehicleDefinition.Armor.HEAVY),1e-6);
        assertEquals(2.4F,VehicleWeaponDefinition.require("autocannon").mechanicalDamage(8,VehicleDefinition.Armor.HEAVY),1e-6);
        assertEquals(12,VehicleWeaponDefinition.require("tank_he").mechanicalDamage(24,VehicleDefinition.Armor.HEAVY),1e-6);
        assertEquals(36,VehicleWeaponDefinition.require("tank_ap").mechanicalDamage(36,VehicleDefinition.Armor.HEAVY),1e-6);
    }
    @Test void lightVehicleReceivesTheActualUnscaledImpact(){
        for(var weapon:VehicleWeaponDefinition.ALL)assertEquals(weapon.weapon().damage(),weapon.mechanicalDamage(weapon.weapon().damage(),VehicleDefinition.Armor.LIGHT),1e-6);
    }
    @Test void nonfiniteImpactAndInvalidGeometryFailClosed(){
        var weapon=VehicleWeaponDefinition.require("tank_ap");
        assertThrows(IllegalArgumentException.class,()->weapon.mechanicalDamage(Float.NaN,VehicleDefinition.Armor.HEAVY));
        assertThrows(IllegalArgumentException.class,()->new VehicleWeaponDefinition(new WeaponDefinition("x","x",1,1,1,1,1,WeaponDefinition.Kind.BULLET,0),VehicleWeaponDefinition.DamageClass.BULLET,"x","x",Double.POSITIVE_INFINITY));
    }
}
