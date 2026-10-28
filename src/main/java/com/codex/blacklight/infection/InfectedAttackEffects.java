package com.codex.blacklight.infection;

import com.codex.blacklight.network.InfectedImpactPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Every infected creature uses the same accepted-hit contract, including in-flight infection.
 * This observer never changes damage, heals, repeats hits or touches invulnerability frames. */
@EventBusSubscriber(modid="blacklight")
public final class InfectedAttackEffects {
    private static final Map<ServerLevel,Budget> LEVELS=new WeakHashMap<>();
    private static final class Budget {long tick;int count;}
    private InfectedAttackEffects(){}
    @SubscribeEvent public static void accepted(LivingDamageEvent.Post event){
        if(!(event.getEntity().level() instanceof ServerLevel level)||event.getNewDamage()<=0
                ||!(event.getSource().getEntity() instanceof Mob attacker)||!BlacklightInfectionSystem.isInfected(attacker))return;
        // Sound has its own spatial budget; visual packet saturation cannot silence a hit.
        var style=InfectedAttackStyle.of(attacker,event.getSource());
        com.codex.blacklight.registry.ModInfectedSounds.play(attacker,InfectedSoundCue.contact(style),event.getEntity().getBoundingBox().getCenter());
        var budget=LEVELS.computeIfAbsent(level,k->new Budget());long now=level.getGameTime();if(budget.tick!=now){budget.tick=now;budget.count=0;}if(budget.count++>=64)return;
        var target=event.getEntity();var direct=event.getSource().getDirectEntity();var from=direct==null||direct==attacker?attacker.getBoundingBox().getCenter():direct.position();var box=target.getBoundingBox();
        var contact=new Vec3(Mth.clamp(from.x,box.minX,box.maxX),Mth.clamp(from.y,box.minY+.05,box.maxY),Mth.clamp(from.z,box.minZ,box.maxZ));
        var normal=box.getCenter().subtract(from).normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
        float size=(float)Math.clamp(.25+attacker.getBbWidth()*.14+Math.sqrt(Math.min(1,event.getNewDamage()/Math.max(1,target.getMaxHealth())))*.55,.25,2.2);
        PacketDistributor.sendToPlayersNear(level,null,contact.x,contact.y,contact.z,80,new InfectedImpactPayload(attacker.getId(),target.getId(),style.ordinal(),contact.x,contact.y,contact.z,(float)normal.x,(float)normal.y,(float)normal.z,size,now*31+attacker.getId()*73L+target.getId()));
    }
}
