package com.codex.blacklight.firearms;
/** Immutable finite magazine/reserve state. */
public record AmmoState(int magazine,int reserve,long readyAt) {
    public AmmoState { magazine=Math.max(0,magazine);reserve=Math.max(0,reserve);readyAt=Math.max(0,readyAt); }
    public AmmoState reload(WeaponDefinition d,long now) {
        if(now<readyAt||magazine>=d.capacity()||reserve==0)return this;
        int transferred=Math.min(d.capacity()-magazine,reserve);
        return new AmmoState(magazine+transferred,reserve-transferred,now+d.reloadTicks());
    }
    public AmmoState fire(WeaponDefinition d,long now) {
        return canFire(now)?new AmmoState(magazine-1,reserve,now+d.cooldownTicks()):this;
    }
    public boolean canFire(long now) { return magazine>0 && now>=readyAt; }
    public AmmoState receive(int offered,int limit) {
        int accepted=Math.min(Math.max(0,offered),Math.max(0,limit-reserve));
        return new AmmoState(magazine,reserve+accepted,readyAt);
    }
}
