package com.codex.blacklight.client;

import com.codex.blacklight.combat.CombatTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** The local lock participates in Minecraft's outline pass without changing entity data. */
public final class ClientTargetOutline {
    public static final int COLOR=0xFF3030;
    private ClientTargetOutline() { }

    public static boolean matches(Entity entity) {
        var mc=Minecraft.getInstance();
        if(entity==null||mc.level==null||mc.player==null||entity.level()!=mc.level)return false;
        var target=ClientTargetLock.entityTarget();
        // Parent identity also covers multipart renderers, and cannot match an old
        // world's entity merely because its numeric ID has been reused.
        return target!=null&&target.level()==mc.level&&com.codex.blacklight.combat.ExplicitTarget.resolve(entity)==target;
    }
}
