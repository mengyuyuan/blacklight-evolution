package com.codex.blacklight.firearms;
/** Platform independent weapon balance; no Minecraft classes. */
public record WeaponDefinition(String id,String ammoId,int capacity,int cooldownTicks,int reloadTicks,
                               float damage,double range,Kind kind,float radius) {
    public enum Kind { BULLET, GRENADE, ROCKET, MISSILE, THERMOBARIC, INFECTION }
    public WeaponDefinition {
        if(id==null||ammoId==null||capacity<1||cooldownTicks<1||reloadTicks<1||damage<0||range<=0||radius<0)
            throw new IllegalArgumentException("Invalid weapon definition");
    }
}
