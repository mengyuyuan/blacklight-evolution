package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Minecraft presentation of a confirmed original hit event; never a damage source. */
public final class EncounterImpact {
    public static void confirmed(EncounterMob boss,LivingEntity victim,boolean wave,float strength){
        if(!(boss.level() instanceof ServerLevel level))return;
        var center=boss.damageContact()==null?victim.getBoundingBox().getCenter():boss.damageContact();
        if(boss.damageContact()==null)level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,center.x,center.y,center.z,wave?6:3,.2,.3,.2,.08);
        // PlayerPoise bounds the flinch and respects a committed player attack.
        if(victim instanceof ServerPlayer player){
            ImpactControl.begin(player,new ImpactProfile(wave?HitReaction.HEAVY:HitReaction.STAGGER,ImpactProfile.BODY,wave?10:6,0,0,false),boss.getYRot());
            PacketDistributor.sendToPlayer(player,new PhysicalFeedbackPayload(wave?PhysicalFeedbackPayload.Effect.GROUND_SPIKE:PhysicalFeedbackPayload.Effect.CLAW_HIT,(wave?.8F:.38F)*strength));
        }
    }
    private EncounterImpact(){}
}
