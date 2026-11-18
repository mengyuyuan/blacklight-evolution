package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;

/** Additional ragdoll motions used only for a confirmed lethal weapon contact. */
public enum FatalWeaponImpact {
    FIST(9,.28,.07,.42,.19),
    WHIP(10,.62,.13,.67,.38),
    HAMMER(11,.50,.05,.62,.33),
    MUSCLE(12,1.55,.70,.93,.39),
    HAMMER_BREAK(13,.54,-.48,.76,.44),
    MUSCLE_BURST(14,1.8,.8,.96,.45);

    public final int motion;
    public final double bodyTransfer, turn;
    private final double forward, lift;
    FatalWeaponImpact(int motion,double forward,double lift,double bodyTransfer,double turn){
        this.motion=motion;
        this.forward=forward;
        this.lift=lift;
        this.bodyTransfer=bodyTransfer;
        this.turn=turn;
    }
    public static FatalWeaponImpact forForm(PowerForm form){
        return switch(form){
            case NONE -> FIST;
            case WHIPFIST -> WHIP;
            case HAMMERFISTS -> HAMMER;
            case MUSCLE_MASS -> MUSCLE;
            default -> null;
        };
    }
    public static FatalWeaponImpact forStrike(PowerForm equipped,WeaponMove move){
        if(move.id.startsWith("dev_")||move==WeaponMove.BULLETDIVE_DROP)return null;
        if(equipped==PowerForm.MUSCLE_MASS)return MUSCLE;
        if(move.shared())return FIST;
        if(equipped==PowerForm.HAMMERFISTS&&(move!=move.uncharged()||move.quake()||move==WeaponMove.HAMMER_COMBO_D))
            return HAMMER_BREAK;
        return forForm(equipped);
    }
    public static FatalWeaponImpact fromMotion(int motion){
        return switch(motion){case 9->FIST;case 10->WHIP;case 11->HAMMER;case 12->MUSCLE;case 13->HAMMER_BREAK;case 14->MUSCLE_BURST;default->null;};
    }
    public Vec3 launch(Vec3 inherited,Vec3 direction,double weight,boolean rising,boolean descending){
        double vertical=descending?-Math.max(.45,Math.abs(lift)):rising?Math.max(.6,lift):lift;
        return inherited.add(direction.normalize().scale(forward*weight)).add(0,vertical*weight,0);
    }
    /** Displacement of a captured skin vertex in its original part frame. */
    public Vec3 displace(Vec3 vertex,Vec3 contact,Vec3 axis,double radius){
        Vec3 offset=vertex.subtract(contact);
        double falloff=1-offset.lengthSqr()/(radius*radius);
        if(falloff<=0)return vertex;
        double depth=radius*falloff*falloff;
        return switch(this){
            case FIST -> vertex.add(axis.scale(depth*.18));
            case WHIP -> vertex.subtract(axis.scale(depth*.37));
            case HAMMER, HAMMER_BREAK -> vertex.add(axis.scale(depth*.62)).add(offset.subtract(axis.scale(offset.dot(axis))).scale(.28*falloff));
            case MUSCLE, MUSCLE_BURST -> vertex.add(axis.scale(depth*.32));
        };
    }
}
