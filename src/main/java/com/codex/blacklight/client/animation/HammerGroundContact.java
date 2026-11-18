package com.codex.blacklight.client.animation;
import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.combat.*;
/** Legacy ram/recovery entry points delegate to the shared scaled-mesh solver. */
public final class HammerGroundContact {
    private HammerGroundContact(){}
    public static float minimum(BlacklightPose p,boolean left){return HammerGroundPose.minimum(p,left);}
    public static float minimum(BlacklightPose p,boolean left,WeaponAttackScales.Shape shape){return HammerGroundPose.minimum(p,left,shape);}
    public static void apply(BlacklightPose p){HammerGroundPose.apply(p);}
    public static void quakeRecovery(BlacklightPose p,String key,float seconds,WeaponAttackScales.Shape right,WeaponAttackScales.Shape left){HammerGroundPose.quakeRecovery(p,key,seconds,right,left);}
}
