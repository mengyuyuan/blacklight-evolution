package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A boss hit staggers the player: the existing synced hit pose plays and movement is
 * pinned by heavy slowness for the move's stagger window. Nothing is saved. */
public final class SupremeHunterStagger {
    private SupremeHunterStagger(){}
    public static void apply(ServerPlayer player,int ticks,float yaw){
        if(ticks<=0||com.codex.blacklight.power.ParasiteSystem.superArmor(player)||!player.isAlive()||player.isCreative()||player.isSpectator())return;
        long now=player.level().getGameTime();
        var kind=ticks>=36?HitReaction.HEAVY:HitReaction.STAGGER;
        player.setData(ModAttachments.HIT_REACTION,new HitReaction(now,Math.min(ticks,40),kind,yaw,ImpactProfile.BODY));
        player.syncData(ModAttachments.HIT_REACTION);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ticks,5,false,false,true));
    }
}
