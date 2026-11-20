package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The point and velocity here belong to the segment that actually dealt damage. */
public final class QuillImpact {
    public static final int LIGHT=15, HEAVY=16;
    private QuillImpact() {}
    public static boolean isQuill(int motion){return motion==LIGHT||motion==HEAVY;}
    public static void hit(ServerPlayer player,LivingEntity target,Vec3 entry,Vec3 direction,boolean heavy,int slot){
        if(direction.lengthSqr()<.5)return;
        double height=(entry.y-target.getY())/target.getBbHeight();
        double side=entry.subtract(target.position()).yRot((float)Math.toRadians(target.yBodyRot-180)).x;
        int region=height>.82?1:height<.40?(side<0?8:16):Math.abs(side)>target.getBbWidth()*.31?(side<0?2:4):32;
        var contact=new WeaponCutContact.Contact(region,"quill",entry,direction,direction.scale(-1));
        var report=new WeaponCutContact.Report(WeaponCutContact.Kind.PUNCTURE,List.of(contact));
        boolean central = slot == 0;
        float size = central ? (heavy ? 1.16F : .57F) : .32F;
        PlayerHitEffects.emit(player,target,InfectedAttackStyle.PIERCE,size,report,FleshStrike.QUILL,heavy && central);
        var hardSound=PlayerHitEffects.hardContactSound(target,entry);
        var cue=hardSound!=null ? hardSound : com.codex.blacklight.registry.ModInfectedSounds.event(com.codex.blacklight.infection.InfectedSoundCue.PIERCE);
        float gain = central ? (heavy ? .72F : .46F) : .14F;
        player.level().playSound(null, entry.x, entry.y, entry.z, cue, net.minecraft.sounds.SoundSource.PLAYERS,
                gain, central && heavy ? .84F : 1.08F + .04F * Math.abs(slot));
        if (central && heavy && target.isAlive() && !CombatTargeting.anchoredBody(target))
            target.knockback(.32, -direction.x, -direction.z);
        double force = heavy ? (central ? .96 : .29) : .27;
        Vec3 momentum = direction.scale(force).add(0, force * .13, 0);
        RagdollImpact.send(target,heavy && central?HEAVY:LIGHT,target.isDeadOrDying()?65:6,true,momentum,report);
    }
}
