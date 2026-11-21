package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;

/** Stable wire slots. The P2 behemothSubTarget specializations attach to
 * Head (5698978), Knee_R (5698121) and Elbow_R (5699851) in behemoth.p3d. */
public enum GoliathLockPart {
    HEAD("source_Head", "head"),
    RIGHT_LEG("source_Knee_R", "right_leg"),
    RIGHT_ARM("source_Elbow_R", "right_arm");

    final String joint;
    public final String labelKey;

    GoliathLockPart(String joint, String key) {
        this.joint = joint;
        this.labelKey = "hud.blacklight.goliath_part." + key;
    }

    public static GoliathLockPart byId(int id) {
        return switch (id) {
            case 0 -> HEAD;
            case 1 -> RIGHT_LEG;
            case 2 -> RIGHT_ARM;
            default -> null;
        };
    }

    public static GoliathLockPart forSkin(String bone) {
        // The authored left_* arm is the slender P2 R arm. The plated arm is L.
        return switch (bone) {
            case "head", "jaw" -> HEAD;
            case "weak_0", "right_leg", "right_shin", "right_calf", "right_foot", "right_toes" -> RIGHT_LEG;
            case "left_arm", "left_forearm", "left_hand",
                    "left_finger_0", "left_finger_1", "left_finger_2", "left_finger_3" -> RIGHT_ARM;
            default -> null;
        };
    }

    /** P2 damage routing includes nearby joints that remain visible after this limb is severed. */
    public boolean acceptsDamage(String bone) {
        if(bone==null)return false;
        return switch(bone) {
            case "torso","neck_base","neck_top" -> this==HEAD; // Spine_3 and Neck_2
            case "left_shin","left_calf","left_foot","left_toes" -> this==RIGHT_LEG; // Knee_L and Ankle_L
            default -> forSkin(bone)==this;
        };
    }

    static GoliathLockPart openOn(AnimatedHurtBody body) {
        return body instanceof EncounterMob mob && mob.kind()==com.codex.blacklight.entity.EncounterKind.GOLIATH
                && mob.exposed() ? mob.goliathWindow().part : null;
    }

    public static boolean severed(String bone, int broken) {
        var part=forSkin(bone);
        return part==RIGHT_LEG && (broken & 1)!=0 || part==RIGHT_ARM && (broken & 2)!=0;
    }

    public boolean available(EncounterMob mob) {
        return switch (this) {
            case RIGHT_LEG -> !mob.broken(0);
            case RIGHT_ARM -> !mob.broken(1);
            case HEAD -> true;
        };
    }
}
