package com.codex.blacklight.combat;

/** Injury committed by one beat of a paired execution. Fractions are Minecraft damage tuning. */
public enum ConsumeWound {
    JAB(.07F), HOOK(.10F), CROSS(.13F), DRIVE(.17F), FINISHING_CRUSH(.35F),
    BRUISE(.24F), QUILL_PUNCTURE(.25F), COMPRESSION(.32F), PUNCTURE(.48F), CUT(.54F),
    LACERATION(.61F), FRACTURE(.65F), HEAD_CRUSH(.80F);

    private final float fraction;
    ConsumeWound(float fraction) { this.fraction = fraction; }

    /** Reserve a final portion for the original consume settlement, even on very low-health receivers. */
    public float damage(float startingHealth, float currentHealth) {
        if (!(startingHealth > 0 && currentHealth > 0)) return 0;
        float reserve = Math.min(.25F, startingHealth * .25F);
        return Math.max(0, Math.min(currentHealth - reserve, (startingHealth - reserve) * fraction));
    }

    public static ConsumeWound forCue(String cue) {
        return switch (cue) {
            case "beat_jab" -> JAB;
            case "beat_hook" -> HOOK;
            case "beat_cross" -> CROSS;
            case "beat_drive" -> DRIVE;
            case "beat_crush" -> FINISHING_CRUSH;
            case "knee_bump", "mount_fist", "heart_slap" -> BRUISE;
            case "rib_squeeze", "joint_lock", "choke_press", "hunter_jaw" -> COMPRESSION;
            case "chest_drive", "heart_grab", "lash_bite" -> PUNCTURE;
            case "quill_extract" -> QUILL_PUNCTURE;
            case "blade_split", "claw_shear", "mount_blade", "mount_claw" -> CUT;
            case "hook_rend", "lash_recoil", "throat_rip", "mount_cable", "arm_wrench", "hunter_rend" -> LACERATION;
            case "neck_snap", "mount_heave", "mount_maul" -> FRACTURE;
            case "skull_click", "head_drop", "hammer_press", "heel_stamp" -> HEAD_CRUSH;
            default -> throw new IllegalArgumentException("Unknown consume contact " + cue);
        };
    }
}
