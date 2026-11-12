package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Accepted lethal damage plus intersecting sharp surfaces; no weapon-name limb lottery. */
public final class WeaponDismemberment {
    public static final int HEAD=1, RIGHT_ARM=2, LEFT_ARM=4, RIGHT_LEG=8, LEFT_LEG=16, BODY=32, ALL=63;
    /** These region bits locate contact; only transmitted mesh planes authorize separation. */
    public static final int MESH_CUT=64;
    private WeaponDismemberment() {}
    public static void weapon(LivingEntity victim,WeaponCutContact.Report contact){
        if(victim.level().isClientSide||!victim.isDeadOrDying()||victim.isInvisible()||!RagdollImpact.supportedBody(victim))return;
        int mask=contact.severMask();
        if(mask==0)return;
        victim.setData(ModAttachments.DISMEMBER,victim.getData(ModAttachments.DISMEMBER)|mask|MESH_CUT);
        victim.syncData(ModAttachments.DISMEMBER);
    }
}
