package com.codex.blacklight.firearms;
/** Standalone deterministic checks also invoked by JUnit. */
public final class FirearmsRulesTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @org.junit.jupiter.api.Test public void finiteMagazineReloadCooldownAndSupply() {
        var rifle=new WeaponDefinition("rifle","rifle_round",30,4,40,4,64,WeaponDefinition.Kind.BULLET,0);
        var empty=new AmmoState(0,7,0);
        check(!empty.canFire(100),"empty magazine cannot fire");
        var loaded=empty.reload(rifle,100);
        check(loaded.magazine()==7&&loaded.reserve()==0,"reload must transfer exactly seven finite rounds");
        check(!loaded.canFire(139)&&loaded.canFire(140),"reload duration gates shots");
        check(loaded.fire(rifle,139).equals(loaded),"early fire cannot consume ammunition");
        var shot=loaded.fire(rifle,140);
        check(shot.magazine()==6&&shot.reserve()==0&&!shot.canFire(143)&&shot.canFire(144),"shot consumes one round and enforces cooldown");
        check(shot.reload(rifle,141).equals(shot),"cooldown prevents reload bypass");
        var partial=new AmmoState(28,10,0).reload(rifle,0);
        check(partial.magazine()==30&&partial.reserve()==8,"tactical reload retains loaded rounds and consumes only deficit");
        check(new AmmoState(30,4,0).reload(rifle,10).readyAt()==0,"full magazine does not consume or delay");
        var supplied=new AmmoState(3,88,200).receive(20,90);
        check(supplied.magazine()==3&&supplied.reserve()==90&&supplied.readyAt()==200,"finite supply changes only reserve up to cap");
        check(supplied.receive(-5,90).equals(supplied),"negative supply cannot create ammunition");
        var roundTrip=new AmmoState(shot.magazine(),shot.reserve(),shot.readyAt());
        check(roundTrip.equals(shot),"serialized fields retain ammunition and cooldown");
        System.out.println("FirearmsRulesChecks: 11 checks passed");
    }
}
