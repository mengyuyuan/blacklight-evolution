package com.codex.blacklight.firearms;

/** Physical-arm support for a main-hand firearm with a genuinely empty offhand.
 * A firearm carried only in the offhand deliberately remains one handed; its vanilla
 * empty main arm is retained. Occupied offhands and two guns also keep one hand per item. */
public final class FirearmSupportPose {
    public record Frame(boolean visible,boolean left,double gripWeight,double x,double y,double z){}
    private FirearmSupportPose(){}
    public static Frame evaluate(boolean enabled,boolean mainGun,boolean offhandEmpty,boolean leftMain,double reload){
        return evaluate(enabled,mainGun,offhandEmpty,leftMain,reload,"military_rifle");
    }
    public static Frame evaluate(boolean enabled,boolean mainGun,boolean offhandEmpty,boolean leftMain,double reload,String weapon){
        double progress=Double.isFinite(reload)?Math.max(0,Math.min(1,reload)):0;
        double release=Math.pow(Math.sin(progress*Math.PI),2);
        var hand=FirearmHandPose.support(weapon,leftMain,reload);
        return new Frame(enabled&&mainGun&&offhandEmpty,!leftMain,1-release,
                hand.x(),hand.y(),hand.z());
    }
}
