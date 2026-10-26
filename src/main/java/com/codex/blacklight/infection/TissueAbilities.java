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
    private static final List<TissueCore> ACTIONS=List.of(TissueCore.TENDON,TissueCore.HYDRAULIC,TissueCore.HYDROSTAT,TissueCore.BLADE,TissueCore.CHELA,TissueCore.HORN);
    private final BlacklightInfectedHost host;
    private int cursor,cooldown;
    private boolean contacted,launched;
    public TissueAbilities(BlacklightInfectedHost host){this.host=host;}
    public void interrupt(){if(!host.level().isClientSide){host.setOrganAction(null,-1,Vec3.ZERO);TissueGrapples.release(host);cooldown=Math.max(cooldown,20);}}
    public boolean gripActive(){var core=host.organActionCore();return (core==TissueCore.CHELA||core==TissueCore.HYDROSTAT)&&host.organActionElapsed(0)>=TissueOrganCycle.cycle(core).contact()&&host.organActionElapsed(0)<TissueOrganCycle.cycle(core).retract();}
    public boolean tick(){
        if(host.level().isClientSide)return false;
        if(cooldown>0)cooldown--;
        if(!host.isAlive()||host.isNoAi()||host.hasMultipartBody()||host.devourerIdentity()||host.isPassenger()
                ||host.getData(ModAttachments.CONSUME_VISUAL).active()||com.codex.blacklight.combat.ImpactControl.stunned(host)
                ||host.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){if(host.organActionCore()!=null)interrupt();return false;}
        var core=host.organActionCore();
        if(core==null){
            var target=host.getTarget();if(cooldown>0||host.tissueStrikeElapsed(0)>=0||!valid(target)||!host.hasLineOfSight(target))return false;
            double gap=Math.max(0,host.distanceTo(target)-(host.getBbWidth()+target.getBbWidth())*.5);
            for(int offset=0;offset<ACTIONS.size();offset++){
                int index=(cursor+offset)%ACTIONS.size();var candidate=ACTIONS.get(index);
                boolean locomotor=candidate==TissueCore.TENDON||candidate==TissueCore.HYDRAULIC||candidate==TissueCore.HORN;
                if(locomotor&&(!host.onGround()||!host.family().equals("ground")))continue;
                if(!locomotor&&host.family().equals("water")&&!host.isInWater())continue;
                if(host.organPower(candidate)<=0||!TissueOrganCycle.range(candidate,gap)||!host.spendTissueNutrition(TissueOrganCycle.cycle(candidate).cost()))continue;
                cursor=(index+1)%ACTIONS.size();contacted=false;launched=false;
                Vec3 direction=target.position().subtract(host.position()).multiply(1,0,1).normalize();
                host.setOrganAction(candidate,target.getId(),direction);host.getNavigation().stop();return true;
            }return false;
        }
        var cycle=TissueOrganCycle.cycle(core);int tick=(int)host.organActionElapsed(0);
        var entity=host.level().getEntity(host.organActionTarget());var target=entity instanceof LivingEntity living?living:null;
        if(tick<0||tick>=cycle.end()||!valid(target)){
            host.setOrganAction(null,-1,Vec3.ZERO);TissueGrapples.release(host);cooldown=cycle.cooldown();return false;
        }
        host.getNavigation().stop();host.setXxa(0);host.setZza(0);
        // Water attacks brace against drift; a land mob's panic-float goal must not eject an aquatic grasper.
        if(host.isInWater())host.setDeltaMovement(host.getDeltaMovement().scale(.35));
        if(core!=TissueCore.HORN||tick<cycle.windup())host.getLookControl().setLookAt(target,25,25);
        double power=host.organPower(core);Vec3 direction=host.organActionDirection();
        if(tick<cycle.windup())host.setDeltaMovement(new Vec3(0,host.getDeltaMovement().y,0));
        if(!launched&&tick>=cycle.windup()){
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
            else if(core==TissueCore.CHELA||core==TissueCore.HYDROSTAT)TissueGrapples.grab(host,target,cycle.retract()-tick);
            else hit(target,core,power);
        }
        // Clamp first, then crush/release; grasp does not apply damage every render frame.
        if(contacted&&(core==TissueCore.CHELA||core==TissueCore.HYDROSTAT)&&tick==cycle.retract()-1){
            if(TissueGrapples.owner(target)==host)hit(target,core,power);
        }
        if(tick>=cycle.retract()){
            TissueGrapples.release(host);
            if(core==TissueCore.HORN)host.setDeltaMovement(host.getDeltaMovement().multiply(.55,1,.55));
        }
        return true;
    }
    private boolean valid(LivingEntity target){return target!=null&&target!=host&&target.isAlive()&&!target.isRemoved()&&target.level()==host.level()
            &&!(target instanceof Player p&&(p.isCreative()||p.isSpectator()))&&!TissueCompetition.controls(target)
            &&!target.getData(ModAttachments.CONSUME_VISUAL).active();}
    private void hit(LivingEntity target,TissueCore core,double power){
        float factor=core==TissueCore.CHELA?1.3F:core==TissueCore.HORN?1.35F:1;
        float amount=(float)(host.getAttributeValue(Attributes.ATTACK_DAMAGE)*factor*(1+.045*TissueOrganCycle.boundedPower(power)));
        if(target.hurt(host.damageSources().mobAttack(host),amount)){
            Vec3 d=target.position().subtract(host.position());target.knockback(TissueOrganCycle.knockback(core,power),-d.x,-d.z);
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
