package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WeaponImpact {
    private WeaponImpact() {}
    public static boolean finisher(WeaponMove m) {
        return m.uncharged()==WeaponMove.CLAW_AUTO3||m==WeaponMove.BLADE_COMBO_D
                ||m.uncharged()==WeaponMove.HAMMER_COMBO_D||m==WeaponMove.MUSCLE_COMBO_C
                ||m==WeaponMove.AIR_AUTO4||m.id.endsWith("_charged")||m.quake();
    }
    public static void apply(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction,boolean first) {
        var profile=ImpactProfile.of(move);
        boolean slam=profile.kind()==HitReaction.SLAM||move.quake();
        boolean heavy=profile.kind()!=HitReaction.STAGGER;
        double resistance=Math.clamp(1-v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),0,1);
        double volume=v.getBbWidth()*v.getBbWidth()*v.getBbHeight();
        double mass=Math.clamp(.8/Math.max(.6,volume),.08,1);
        // Large, resistant creatures flinch but are not tossed like a chicken.
        boolean canFall=profile.knockdown()&&resistance>.05&&volume<4;
        if(!canFall&&profile.knockdown())profile=new ImpactProfile(HitReaction.HEAVY,ImpactProfile.BODY,12,profile.push(),0,false);
        boolean protectedAttack=v.isAlive()&&(v instanceof com.codex.blacklight.entity.BlacklightHunter hunter&&hunter.committedAttack()
                ||v instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.giantBoss());
        if(!protectedAttack){
            ImpactControl.begin(v,profile,p.getYRot());
            double force=profile.push()*mass*resistance;
            double vertical=profile.lift()==0?Math.max(0,Math.min(v.getDeltaMovement().y,.12)):profile.lift()*mass*resistance;
            v.setDeltaMovement(direction.x*force,vertical,direction.z*force);
            if(vertical>0)v.setOnGround(false);
            v.hurtMarked=true;
            if(v instanceof Mob mob){mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);}
        }
        boolean blade=move.form==PowerForm.BLADE;
        if(!blade)ClawPhysicalEffects.onComboTargetHit(p,v,heavy||slam);
        CombatImpactEffects.target(p,v,move);
        if(first)com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),heavy||slam?"heavy":"hit",v.position(),.9F,1F);
        if(first&&blade){
            var effect=BladeEffects.heavy(move)?PhysicalFeedbackPayload.Effect.BLADE_HEAVY
                :move==WeaponMove.BLADE_RETURN||move==WeaponMove.BLADE_COMBO_C2?PhysicalFeedbackPayload.Effect.BLADE_RETURN:PhysicalFeedbackPayload.Effect.BLADE_CUT;
            PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(effect,BladeEffects.heavy(move)?1.05F:.72F));
            return;
        }
        if(first)PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(
                heavy||slam?PhysicalFeedbackPayload.Effect.CLAW_HEAVY:PhysicalFeedbackPayload.Effect.CLAW_HIT,heavy?.85F:.42F));
    }
}
