package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

/** Server-only admission check, evaluated before the ordinary grab clears AI targets. */
public final class StealthConsume {
    private StealthConsume(){}
    public static boolean behind(Vec3 targetToPlayer,float targetYaw){
        return StealthApproach.behind(targetToPlayer.x,targetToPlayer.y,targetToPlayer.z,targetYaw);
    }
    public static boolean canStart(ServerPlayer player,Mob target,PowerForm form){
        return form==PowerForm.NONE&&player.isShiftKeyDown()&&player.onGround()&&!player.isInWaterOrBubble()
                &&player.getMainHandItem().isEmpty()&&player.getOffhandItem().isEmpty()
                &&ConsumeSequence.humanoid(target)&&!target.isBaby()&&!ConsumeSequence.large(target)
                &&target.onGround()&&!target.isInWaterOrBubble()&&target.getTarget()==null
                &&target.getLastHurtByMob()==null&&target.getLastHurtMob()==null&&target.hurtTime==0
                &&!target.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                &&behind(player.position().subtract(target.position()),target.yBodyRot);
    }
}
