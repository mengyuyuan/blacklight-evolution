package com.codex.blacklight.infection;

import com.codex.blacklight.network.InfectedImpactPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import com.codex.blacklight.forge.PacketDistributor;
import java.util.*;

/** Every infected creature uses the same accepted-hit contract, including in-flight infection.
 * This observer never changes damage, heals, repeats hits or touches invulnerability frames. */
@EventBusSubscriber(modid="blacklight")
public final class InfectedAttackEffects {
    private static final Map<ServerLevel,Budget> LEVELS=new WeakHashMap<>();
    private static final class Budget {long tick;int count;}
    private InfectedAttackEffects(){}
    @SubscribeEvent public static void accepted(com.codex.blacklight.combat.ForgeDamageEvent.Post event){
        com.codex.blacklight.combat.BossImpactFeedback.accepted(event.getEntity(),event.getSource(),event.getNewDamage());
        if(!event.getEntity().level().isClientSide&&event.getNewDamage()>0&&com.codex.blacklight.combat.AnimatedHurtBody.of(event.getEntity())!=null&&com.codex.blacklight.combat.AnimatedHurtBody.of(event.getEntity()).incomingContact()!=null){com.codex.blacklight.combat.AnimatedHurtBody.of(event.getEntity()).confirmedIncoming(event.getSource(),event.getNewDamage());return;}
        if(!(event.getEntity().level() instanceof ServerLevel level)||event.getNewDamage()<=0
                ||!(event.getSource().getEntity() instanceof Mob attacker)||!BlacklightInfectionSystem.isInfected(attacker))return;
        // Sound has its own spatial budget; visual packet saturation cannot silence a hit.
        var style=InfectedAttackStyle.of(attacker,event.getSource());
        var projectile=event.getSource().getDirectEntity() instanceof com.codex.blacklight.entity.BlacklightSpit spit?spit:null;
        var precise=projectile!=null?projectile.damageContact():attacker instanceof com.codex.blacklight.entity.BlacklightMutant mutant?mutant.damageContact():attacker instanceof com.codex.blacklight.entity.BlacklightHunter hunter?hunter.damageContact():attacker instanceof com.codex.blacklight.entity.BlacklightHydra hydra?hydra.damageContact():attacker instanceof com.codex.blacklight.entity.EncounterMob mob?mob.damageContact():null;
        var soundAt=precise==null?event.getEntity().getBoundingBox().getCenter():precise;
        // The physical bolus owns its one splash sound, including blocked/immune hits.
        var sound=projectile!=null?null:com.codex.blacklight.registry.ModInfectedSounds.prepare(attacker,InfectedSoundCue.contact(style),soundAt);
        if(precise==null&&sound!=null)com.codex.blacklight.registry.ModInfectedSounds.playPrepared(attacker,sound,soundAt);
        var budget=LEVELS.computeIfAbsent(level,k->new Budget());long now=level.getGameTime();if(budget.tick!=now){budget.tick=now;budget.count=0;}
        if(budget.count++>=64){if(precise!=null)com.codex.blacklight.combat.ContactEffects.audio(attacker,soundAt,sound);return;}
        var target=event.getEntity();var direct=event.getSource().getDirectEntity();var from=direct==null||direct==attacker?attacker.getBoundingBox().getCenter():direct.position();var box=target.getBoundingBox();
        var contact=precise==null?new Vec3(Mth.clamp(from.x,box.minX,box.maxX),Mth.clamp(from.y,box.minY+.05,box.maxY),Mth.clamp(from.z,box.minZ,box.maxZ)):precise;
        var normal=box.getCenter().subtract(from).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
        float size=(float)net.minecraft.util.Mth.clamp(.25+attacker.getBbWidth()*.14+Math.sqrt(Math.min(1,event.getNewDamage()/Math.max(1,target.getMaxHealth())))*.55,.25,2.2);
        var tissue=new InfectedImpactPayload(attacker.getId(),target.getId(),style.ordinal(),contact.x,contact.y,contact.z,(float)normal.x,(float)normal.y,(float)normal.z,size,now*31+attacker.getId()*73L+target.getId());
        if(precise!=null){com.codex.blacklight.combat.ContactEffects.body(attacker,tissue,style==InfectedAttackStyle.CRUSH,sound);return;}
        if(target instanceof net.minecraft.world.entity.animal.IronGolem)com.codex.blacklight.combat.ContactEffects.metal(attacker,tissue,null);
        else PacketDistributor.sendToPlayersNear(level,null,contact.x,contact.y,contact.z,80,tissue);
    }
}
