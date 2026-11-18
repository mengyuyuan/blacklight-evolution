package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import com.codex.blacklight.forge.PacketDistributor;

public final class WeaponImpact {
    private WeaponImpact() {}
    public static boolean finisher(WeaponMove m) {
        return m.uncharged()==WeaponMove.CLAW_AUTO3||m==WeaponMove.BLADE_COMBO_D
                ||m.uncharged()==WeaponMove.HAMMER_COMBO_D||m==WeaponMove.MUSCLE_COMBO_C
                ||m==WeaponMove.AIR_AUTO4||m.id.endsWith("_charged")||m.quake();
    }
    /** Weight for heavy contacts that do not run through a weapon session: landings, pounces,
     * devastators, groundspikes and thrown bodies. Call after the knockback is set so the hold
     * keeps the launch. Feedback goes to the attacker once per action ({@code first}). */
    public static void contact(ServerPlayer p,LivingEntity v,ImpactTier tier,boolean first,PhysicalFeedbackPayload.Effect effect){
        if(!v.level().isClientSide&&tier.atLeast(ImpactTier.HEAVY)){
            var state=com.codex.blacklight.registry.ModAttachments.getData(v, ModAttachments.HIT_REACTION);
            if(!v.isAlive()||state.weight(v.level().getGameTime(),0)>0)
                RagdollImpact.send(v,ImpactProfile.FALL,Math.max(18,state.duration()),false,HitStop.launchVelocity(v),
                        p==null?WeaponCutContact.Report.NONE:WeaponCutContact.impact(p,v,HitStop.launchVelocity(v)));
        }
        if(v.level().isClientSide)return;
        if(v.isAlive())HitStop.victim(v,tier.hitstop);
        float pitch=switch(tier){case LIGHT->1.1F;case MEDIUM->1F;case HEAVY->.88F;case FINISHER->.78F;};
        if(p!=null)com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),tier.atLeast(ImpactTier.HEAVY)?"heavy":"hit",v.position(),.8F+.15F*tier.ordinal(),pitch);
        if(tier.atLeast(ImpactTier.HEAVY))v.level().playSound(null,v.getX(),v.getY(),v.getZ(),SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.9F,.62F);
        if(tier==ImpactTier.FINISHER)v.level().playSound(null,v.getX(),v.getY(),v.getZ(),SoundEvents.WARDEN_ATTACK_IMPACT,SoundSource.PLAYERS,.55F,.85F);
        if(p!=null&&AnimatedHurtBody.of(v)==null)PlayerHitEffects.emit(p,v,
                tier.atLeast(ImpactTier.HEAVY)?com.codex.blacklight.infection.InfectedAttackStyle.CRUSH:com.codex.blacklight.infection.InfectedAttackStyle.TEAR,
                .5F*tier.effectScale,WeaponCutContact.impact(p,v,HitStop.launchVelocity(v)));
        if(first&&p!=null&&effect!=null)PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(effect,tier.feedback));
    }
    public static void apply(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction,boolean first){apply(p,v,move,direction,first,0F);}
    public static void apply(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction,boolean first,float charge) {
        apply(p,v,move,direction,first,charge,WeaponCutContact.Report.NONE);
    }
    public static void apply(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction,boolean first,float charge,WeaponCutContact.Report cut) {
        apply(p,v,move,direction,first,charge,cut,false);
    }
    static void apply(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction,boolean first,float charge,WeaponCutContact.Report cut,boolean fullyCharged) {
        // A confirmed ordinary hit still has impact feedback when no sharp face touched.
        // Keep that fallback separate from the report allowed to authorize dismemberment.
        var impact=cut.contacts().isEmpty()?WeaponCutContact.impact(p,v,direction):cut;
        var profile=ImpactProfile.of(move);var tier=ImpactTier.of(move);
        boolean slam=profile.kind()==HitReaction.SLAM||move.quake();
        boolean heavy=profile.kind()!=HitReaction.STAGGER;
        double resistance=com.codex.blacklight.util.MathCompat.clamp(1-v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE),0,1);
        double volume=v.getBbWidth()*v.getBbWidth()*v.getBbHeight();
        double mass=com.codex.blacklight.util.MathCompat.clamp(.8/Math.max(.6,volume),.08,1);
        // Large, resistant creatures flinch but are not tossed like a chicken.
        boolean canFall=profile.knockdown()&&resistance>.05&&volume<4;
        if(!canFall&&profile.knockdown())profile=new ImpactProfile(HitReaction.HEAVY,ImpactProfile.BODY,12,profile.push(),0,false);
        // Hunter flinches are bounded by ImpactControl's cooldown. Blocking the
        // entire committed attack here bypassed both light flinches and heavy control.
        boolean protectedAttack=v instanceof com.codex.blacklight.entity.BlacklightHive||v.isAlive()&&v instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.giantBoss();
        boolean controlled=false;
        if(!protectedAttack&&ImpactControl.begin(v,profile,p.getYRot())){controlled=true;
            double force=profile.push()*(volume<4?tier.force:1)*mass*resistance;
            // Tier lift pops ordinary bodies; massive ones stay grounded under the same hit.
            double lift=profile.lift()<0?profile.lift():profile.lift()+(volume<4?tier.lift:0);
            double vertical=lift==0?Math.max(0,Math.min(v.getDeltaMovement().y,.12)):lift*mass*resistance;
            Vec3 inherited=v.isDeadOrDying()?HitStop.launchVelocity(v):Vec3.ZERO;
            v.setDeltaMovement(new Vec3(direction.x*force,vertical,direction.z*force).add(inherited));
            if(vertical>0)v.setOnGround(false);
            v.hurtMarked=true;
            if(v instanceof Mob mob){mob.getNavigation().stop();mob.setXxa(0);mob.setZza(0);}
            // Every struck victim reacts; only attacker sound/camera is limited to the first.
            if(v.isAlive())HitStop.victim(v,tier.hitstop);
        }
        HeavyBlow.apply(p,v,move,tier,charge,direction,controlled);
        WeaponDismemberment.weapon(v,cut);
        boolean sharp=move.form==PowerForm.BLADE||move.form==PowerForm.CLAWS
                ||move.form==PowerForm.WHIPFIST&&WeaponCutContact.contactKind(move)!=WeaponCutContact.Kind.BLUNT;
        if(sharp&&v.isDeadOrDying()&&RagdollImpact.supportedBody(v)){
            // Capture connected skin once, including blade-back contacts before an edge crosses it.
            // This adapter flag does not sever anything; only confirmed planes may do that.
            com.codex.blacklight.registry.ModAttachments.setData(v, ModAttachments.DISMEMBER,com.codex.blacklight.registry.ModAttachments.getData(v, ModAttachments.DISMEMBER)|WeaponDismemberment.MESH_CUT);com.codex.blacklight.registry.ModAttachments.syncData(v, ModAttachments.DISMEMBER);
        }
        if(controlled||!v.isAlive()||!impact.contacts().isEmpty()){
            // Light cuts retain directional flinch; heavy cuts and lethal hits take over the body.
            int physicalTicks=!v.isAlive()?60:canFall?profile.stun():8;
            var launch=HitStop.launchVelocity(v);
            var style=v.isDeadOrDying() && RagdollImpact.supportedBody(v) && !CombatTargeting.anchoredBody(v)
                    ?FatalWeaponImpact.forStrike(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower(),move):null;
            if(style==FatalWeaponImpact.MUSCLE&&fullyCharged&&ConsumeSequence.humanoid(v))style=FatalWeaponImpact.MUSCLE_BURST;
            if(style!=null){
                var travel=style==FatalWeaponImpact.WHIP && !impact.contacts().isEmpty()?impact.direction():direction;
                launch=style.launch(launch,travel,mass*resistance*tier.force,profile.motion()==ImpactProfile.UP,profile.lift()<0);
            }
            RagdollImpact.send(v,style==null?profile.motion():style.motion,physicalTicks,sharp,launch,impact,tier.hitstop);
        }
        boolean blade=move.form==PowerForm.BLADE;
        if(AnimatedHurtBody.of(v)==null)CombatImpactEffects.target(p,v,move,impact,fullyCharged);
        var soundPoint=v instanceof AnimatedHurtBody hydra&&hydra.animatedHurtBody()?hydra.incomingPoint():v.position();
        if(first){
            float pitch=switch(tier){case LIGHT->1.1F;case MEDIUM->1F;case HEAVY->.88F;case FINISHER->.78F;};
            com.codex.blacklight.registry.ModWeaponSounds.play(p,com.codex.blacklight.registry.ModWeaponSounds.equipped(p),tier.atLeast(ImpactTier.HEAVY)||heavy||slam?"heavy":"hit",soundPoint,.8F+.15F*tier.ordinal(),pitch);
            if(tier.atLeast(ImpactTier.HEAVY))p.level().playSound(null,soundPoint.x,soundPoint.y,soundPoint.z,SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.9F,.62F);
            if(tier==ImpactTier.FINISHER){
                p.level().playSound(null,soundPoint.x,soundPoint.y,soundPoint.z,SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,1F,.7F);
                p.level().playSound(null,soundPoint.x,soundPoint.y,soundPoint.z,SoundEvents.WARDEN_ATTACK_IMPACT,SoundSource.PLAYERS,.55F,.85F);
            }
        }
        if(first&&blade){
            var effect=BladeEffects.heavy(move)?PhysicalFeedbackPayload.Effect.BLADE_HEAVY
                :move==WeaponMove.BLADE_RETURN||move==WeaponMove.BLADE_COMBO_C2?PhysicalFeedbackPayload.Effect.BLADE_RETURN:PhysicalFeedbackPayload.Effect.BLADE_CUT;
            PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(tier.atLeast(ImpactTier.HEAVY)&&effect!=PhysicalFeedbackPayload.Effect.BLADE_HEAVY?PhysicalFeedbackPayload.Effect.BLADE_HEAVY:effect,Math.max(tier.feedback,BladeEffects.heavy(move)?1.05F:.5F)));
            return;
        }
        if(first)PacketDistributor.sendToPlayer(p,new PhysicalFeedbackPayload(
                tier.atLeast(ImpactTier.HEAVY)||slam?PhysicalFeedbackPayload.Effect.CLAW_HEAVY:PhysicalFeedbackPayload.Effect.CLAW_HIT,tier.feedback));
    }
}
