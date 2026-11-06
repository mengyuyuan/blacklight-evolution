package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Per-host bounded action controller. Real collision, one contact, finite recovery and nutrition costs. */
public final class TissueAbilities {
    private static final List<TissueCore> ACTIONS=List.of(TissueCore.TENDON,TissueCore.HYDRAULIC,TissueCore.HYDROSTAT,TissueCore.BLADE,TissueCore.CHELA,TissueCore.HORN,TissueCore.SADDLE,TissueCore.CLUB,TissueCore.TONGUE,TissueCore.CNIDOCYTE,TissueCore.EXTRAORAL);
    private final net.minecraft.world.entity.Mob host;
    private int cursor,cooldown;
    private boolean contacted,launched,withdrew,ownsMotion=true;
    public TissueAbilities(net.minecraft.world.entity.Mob host){this.host=host;lastNativeTick=host.tickCount;}
    private int lastNativeTick;
    private void elapseNativeCooldown(){
        if(host instanceof BlacklightInfectedHost)return;
        int elapsed=Math.max(0,host.tickCount-lastNativeTick);lastNativeTick=host.tickCount;cooldown=Math.max(0,cooldown-elapsed);
    }
    public void interrupt(){if(!host.level().isClientSide){TissueActions.set(host,null,-1,Vec3.ZERO);TissueGrapples.release(host);cooldown=Math.max(cooldown,20);}}
    private static boolean gripping(TissueCore core){return TissueActions.gripping(core);}
    public boolean gripActive(){var core=TissueActions.core(host);return gripping(core)&&TissueActions.elapsed(host,0)>=TissueOrganCycle.cycle(core).contact()&&TissueActions.elapsed(host,0)<TissueOrganCycle.cycle(core).retract();}
    /** Called before ordinary melee starts, so close-range organs cannot be
     * starved forever by a fresh vanilla melee animation every recovery. */
    public boolean tryStart(LivingEntity target){return tryStart(target,true);}
    public boolean tryStart(LivingEntity target,boolean motion){
        elapseNativeCooldown();
        if(host.level().isClientSide||!ModAttachments.available(host)||cooldown>0||TissueActions.core(host)!=null||TissueActions.legacyStrike(host)||!host.isAlive()||host.isNoAi()
                ||!TissueActions.eligible(host)||host.isPassenger()||!valid(target)||!host.hasLineOfSight(target)
                ||ModAttachments.peekData(host,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()||TissueCompetition.controls(host)
                ||com.codex.blacklight.combat.ImpactControl.stunned(host)||host.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return false;
        double gap=Math.max(0,host.distanceTo(target)-(host.getBbWidth()+target.getBbWidth())*.5);
        for(int offset=0;offset<ACTIONS.size();offset++){
            int index=(cursor+offset)%ACTIONS.size();var candidate=ACTIONS.get(index);
            boolean locomotor=candidate==TissueCore.TENDON||candidate==TissueCore.HYDRAULIC||candidate==TissueCore.HORN;
            if(locomotor&&!motion)continue;
            if(locomotor&&(!host.onGround()||!TissueActions.family(host).equals("ground")))continue;
            if(!locomotor&&TissueActions.family(host).equals("water")&&!host.isInWater())continue;
            if(TissueActions.power(host,candidate)<=0||!TissueOrganCycle.range(candidate,gap)||!TissueActions.spend(host,TissueOrganCycle.cycle(candidate).cost()))continue;
            ownsMotion=motion;cursor=(index+1)%ACTIONS.size();contacted=false;launched=false;withdrew=false;
            Vec3 direction=target.position().subtract(host.position()).multiply(1,0,1).normalize();
            TissueActions.set(host,candidate,target.getId(),direction);if(ownsMotion)host.getNavigation().stop();com.codex.blacklight.registry.ModInfectedSounds.play(host,InfectedSoundCue.TENSION);return true;
        }return false;
    }
    public boolean tick(){
        if(host.level().isClientSide)return false;
        if(host instanceof BlacklightInfectedHost){if(cooldown>0)cooldown--;}else elapseNativeCooldown();
        if(!host.isAlive()||host.isNoAi()||!TissueActions.eligible(host)||host.isPassenger()
                ||ModAttachments.peekData(host,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active()||com.codex.blacklight.combat.ImpactControl.stunned(host)
                ||TissueCompetition.controls(host)||host.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){if(TissueActions.core(host)!=null)interrupt();return false;}
        var core=TissueActions.core(host);
        if(core==null){
            return tryStart(host.getTarget(),ownsMotion);
        }
        var cycle=TissueOrganCycle.cycle(core);int tick=(int)TissueActions.elapsed(host,0);
        var entity=host.level().getEntity(TissueActions.target(host));var target=entity instanceof LivingEntity living?living:null;
        if(core==TissueCore.EXTRAORAL&&contacted&&target!=null&&!target.isAlive()&&tick<cycle.retract()){
            TissueActions.retract(host);tick=cycle.retract();
        }
        boolean closing=core==TissueCore.EXTRAORAL&&tick>=cycle.retract();
        if(tick<0||tick>=cycle.end()||!closing&&!valid(target)){
            TissueActions.set(host,null,-1,Vec3.ZERO);TissueGrapples.release(host);cooldown=TissueActions.recover(host,cycle.cooldown());return false;
        }
        if(ownsMotion){host.getNavigation().stop();host.setXxa(0);host.setZza(0);}
        // Water attacks brace against drift; a land mob's panic-float goal must not eject an aquatic grasper.
        if(ownsMotion&&host.isInWater())host.setDeltaMovement(host.getDeltaMovement().scale(.35));
        if(ownsMotion&&target!=null&&(core!=TissueCore.HORN||tick<cycle.windup()))host.getLookControl().setLookAt(target,25,25);
        double power=TissueActions.power(host,core);Vec3 direction=TissueActions.direction(host);
        if(ownsMotion&&tick<cycle.windup())host.setDeltaMovement(new Vec3(0,host.getDeltaMovement().y,0));
        if(!launched&&tick>=cycle.windup()){
            com.codex.blacklight.registry.ModInfectedSounds.play(host,gripping(core)?InfectedSoundCue.LASH:InfectedSoundCue.SWING);
            launched=true;double speed=TissueOrganCycle.launchSpeed(core,power);
            if(speed>0){double rise=core==TissueCore.TENDON?.52:core==TissueCore.HYDRAULIC?.16:host.getDeltaMovement().y;
                host.setDeltaMovement(direction.scale(speed).add(0,rise,0));host.hurtMarked=true;}
        }
        if(core==TissueCore.HORN&&tick>=cycle.windup()&&tick<cycle.retract()&&!contacted){
            if(host.horizontalCollision&&tick>cycle.windup()+1)contacted=true;
            else host.setDeltaMovement(direction.scale(TissueOrganCycle.launchSpeed(core,power)).add(0,host.getDeltaMovement().y,0));
        }
        boolean travelling=core==TissueCore.TENDON||core==TissueCore.HORN;
        if(!contacted&&tick>=(travelling?cycle.windup()+1:cycle.contact())&&tick<cycle.retract()
                &&host.getBoundingBox().inflate(TissueOrganCycle.reach(core),.6,TissueOrganCycle.reach(core)).intersects(target.getBoundingBox())&&host.hasLineOfSight(target)){
            contacted=true;
            if(core==TissueCore.BLADE)cleave(target,power);
            else if(gripping(core))TissueGrapples.grab(host,target,cycle.retract()-tick);
            else hit(target,core,power);
        }
        // Clamp first, then crush/release; grasp does not apply damage every render frame.
        if(contacted&&gripping(core)&&core!=TissueCore.EXTRAORAL&&tick==cycle.retract()-1){
            if(TissueGrapples.owner(target)==host)hit(target,core,power);
        }
        // A paid enzyme dose works only while the real prey remains held at the mouth.
        if(core==TissueCore.EXTRAORAL&&contacted&&tick>=cycle.contact()&&tick<cycle.retract()
                &&(tick-cycle.contact())%10==0&&TissueGrapples.owner(target)==host){
            hit(target,core,power);
            if(host.level() instanceof net.minecraft.server.level.ServerLevel server)server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.28F,.025F,.035F),.7F),
                    target.getX(),target.getY()+target.getBbHeight()*.42,target.getZ(),5,target.getBbWidth()*.2,target.getBbHeight()*.1,target.getBbWidth()*.2,.01);
        }
        if(tick>=cycle.retract()){
            if(!withdrew){withdrew=true;com.codex.blacklight.registry.ModInfectedSounds.play(host,InfectedSoundCue.RETRACT);}
            TissueGrapples.release(host);
            if(core==TissueCore.HORN)host.setDeltaMovement(host.getDeltaMovement().multiply(.55,1,.55));
        }
        return true;
    }
    private boolean valid(LivingEntity target){return ModAttachments.available(target)&&target!=null&&target!=host&&target.isAlive()&&!target.isRemoved()&&target.level()==host.level()&&!host.isAlliedTo(target)
            &&!(target instanceof Player p&&(p.isCreative()||p.isSpectator()))&&!TissueCompetition.controls(target)
            &&com.codex.blacklight.power.BiomassSystem.claimant(target)==null&&(TissueGrapples.owner(target)==null||TissueGrapples.owner(target)==host)
            &&!ModAttachments.peekData(target,ModAttachments.CONSUME_VISUAL,com.codex.blacklight.combat.ConsumeVisual.EMPTY).active();}
    private void hit(LivingEntity target,TissueCore core,double power){
        float factor=switch(core){case CHELA->1.3F;case HORN->1.35F;case SADDLE->1.8F;case CLUB->2.1F;case TONGUE->.65F;case CNIDOCYTE->.35F;case EXTRAORAL->.55F;default->1;};
        double base=host.getAttribute(Attributes.ATTACK_DAMAGE)==null
                ?com.codex.blacklight.config.InfectionEcologyConfig.value(com.codex.blacklight.config.InfectionEcologyConfig.FALLBACK_DAMAGE)*(1+NativeTissue.combat(host).damage())
                :host.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float amount=(float)Math.min(Float.MAX_VALUE/4,base*factor*(1+.045*TissueOrganCycle.boundedPower(power)));
        if(!Float.isFinite(amount)||amount<=0)return;
        var before=com.codex.blacklight.compat.NativeCombatAccess.read(target,false);
        boolean returned=target.hurt(TissueAttackSource.mobAttack(host),amount);
        var after=com.codex.blacklight.compat.NativeCombatAccess.read(target,false);
        // Observe the completed foreign override. This controls only our own
        // secondary effects; it never changes that override's return contract.
        if(returned||after.health()<before.health()||after.absorption()<before.absorption()){
            if(!(host instanceof BlacklightInfectedHost)){
                var cue=switch(core){case CHELA,HORN,SADDLE,CLUB,HYDRAULIC->InfectedSoundCue.CRUSH;case BLADE,EXTRAORAL->InfectedSoundCue.TEAR;default->InfectedSoundCue.PIERCE;};
                com.codex.blacklight.registry.ModInfectedSounds.play(host,cue,target.getBoundingBox().getCenter());
            }
            if(core==TissueCore.CNIDOCYTE)target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,40,1),host);
            if(core!=TissueCore.EXTRAORAL){Vec3 d=target.position().subtract(host.position());target.knockback(TissueOrganCycle.knockback(core,power),-d.x,-d.z);}
        }
    }
    private void cleave(LivingEntity main,double power){
        Vec3 front=main.position().subtract(host.position()).multiply(1,0,1).normalize();
        var targets=host.level().getEntitiesOfClass(LivingEntity.class,host.getBoundingBox().inflate(2.2,1,2.2),this::valid)
                .stream().filter(t->host.hasLineOfSight(t)&&t.position().subtract(host.position()).normalize().dot(front)>.15)
                .sorted(Comparator.comparingDouble(host::distanceToSqr)).limit(3).toList();
        for(var target:targets)hit(target,TissueCore.BLADE,power);
    }
}
