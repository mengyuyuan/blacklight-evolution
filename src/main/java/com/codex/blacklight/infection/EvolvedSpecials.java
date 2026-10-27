package com.codex.blacklight.infection;

import com.codex.blacklight.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server-owned specialized attacks. No teleport, forced health subtraction or client damage. */
public final class EvolvedSpecials {
    private final BlacklightInfectedHost host;
    private int cooldown,shield=20,broken,recharge;
    private Vec3 direction=Vec3.ZERO;
    private final Set<Integer> emitted=new HashSet<>();
    private final List<Integer> patients=new ArrayList<>();
    public EvolvedSpecials(BlacklightInfectedHost host){this.host=host;}
    public void interrupt(){emitted.clear();patients.clear();direction=Vec3.ZERO;cooldown=Math.max(cooldown,35);}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Shield",shield);t.putInt("Broken",broken);var active=EvolvedSpecialMove.resolve(host.evolvedAction());t.putInt("Cooldown",Math.max(cooldown,active==null?0:active.cooldown));return t;}
    public void load(CompoundTag tag){shield=tag.contains("Shield")?Math.clamp(tag.getInt("Shield"),0,20):20;broken=Math.clamp(tag.getInt("Broken"),0,100);cooldown=Math.clamp(tag.getInt("Cooldown"),0,240);}
    public int shield(){return shield;}
    public float protect(DamageSource source,float amount){
        if(host.evolvedArchetype()!=EvolvedArchetype.ARMORED||!host.isEvolved()||!host.evolvedEngaged()||broken>0||shield<=0
                ||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)||source.getSourcePosition()==null)return amount;
        var move=host.evolvedAction();if(!move.isEmpty()&&!(move.equals(EvolvedSpecialMove.BASH.key)&&host.evolvedActionElapsed(0)<12))return amount;
        var incoming=source.getSourcePosition().subtract(host.position()).multiply(1,0,1);
        if(incoming.lengthSqr()<.001)return amount;
        double yaw=Math.toRadians(host.yBodyRot);var front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        if(incoming.normalize().dot(front)<.45)return amount;
        return amount*.4F;
    }
    public void acceptedShield(float prevented){
        if(prevented<=0)return;
        shield=Math.max(0,shield-(int)Math.clamp(Math.ceil(prevented/Math.max(1,host.getMaxHealth())*80),3,10));
        if(shield==0)broken=100;recharge=0;host.publishEvolvedShield(shield);
    }
    public boolean tick(LivingEntity target,boolean canStart){
        if(cooldown>0)cooldown--;if(broken>0)broken--;
        if(host.evolvedArchetype()==EvolvedArchetype.ARMORED&&broken==0&&shield<20&&++recharge>=30){shield++;recharge=0;host.publishEvolvedShield(shield);}
        var active=EvolvedSpecialMove.resolve(host.evolvedAction());
        if(active!=null){
            float t=host.evolvedActionElapsed(0);
            if(active.type!=host.evolvedArchetype()||!valid(target)||target.getId()!=host.evolvedSpecialTarget()||t>=active.duration){finish(active);return true;}
            if(active==EvolvedSpecialMove.LASH&&!host.hasLineOfSight(target)){finish(active);return true;}
            host.getNavigation().stop();host.setSprinting(false);host.setXxa(0);host.setZza(0);
            if(direction.lengthSqr()<.001)direction=toward(target);
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));host.setYRot(yaw);host.yBodyRot=yaw;host.yHeadRot=yaw;
            if(active==EvolvedSpecialMove.POUNCE&&t>=10&&t<23){if(t==10&&host.onGround())host.setDeltaMovement(host.getDeltaMovement().add(0,.32,0));travel(.39);}
            else if(active==EvolvedSpecialMove.DASH&&t>=10&&t<19)travel(.68);
            else host.setDeltaMovement(0,host.getDeltaMovement().y,0);
            if(active==EvolvedSpecialMove.LASH&&t>=active.contact&&t<=active.retract&&visible(target,10)){
                var pull=host.position().subtract(target.position()).multiply(1,0,1);
                if(pull.lengthSqr()>4&&!target.isPassenger()){
                    var v=target.getDeltaMovement().multiply(.55,1,.55).add(pull.normalize().scale(.14));
                    double speed=Math.hypot(v.x,v.z);if(speed>.26)v=new Vec3(v.x*.26/speed,v.y,v.z*.26/speed);
                    target.setDeltaMovement(v);target.hurtMarked=true;
                }
            }
            if(active==EvolvedSpecialMove.VOLLEY){
                for(int i=0;i<3;i++)if(t>=active.contact+i*6&&emitted.add(i))fire(target,i);
            }else if(t>=active.contact&&emitted.add(0))impact(active,target);
            return true;
        }
        var move=EvolvedSpecialMove.of(host.evolvedArchetype());
        if(!canStart||move==null||cooldown>0||!valid(target)||!host.hasLineOfSight(target))return false;
        double distance=host.distanceTo(target);
        if(distance>move.range+host.getBbWidth()||move==EvolvedSpecialMove.VOLLEY&&distance<5)return false;
        if((move==EvolvedSpecialMove.POUNCE||move==EvolvedSpecialMove.DASH)&&(!host.onGround()||distance<3.2||!support(direction=toward(target),1.2)))return false;
        if(move==EvolvedSpecialMove.MEND){
            patients.clear();
            for(var ally:host.level().getEntitiesOfClass(Mob.class,host.getBoundingBox().inflate(10),m->m.isAlive()&&m!=target&&m.getHealth()<m.getMaxHealth()&&BlacklightInfectionSystem.isInfected(m)))
                if(host.hasLineOfSight(ally)&&patients.size()<4)patients.add(ally.getId());
            if(patients.isEmpty()||!host.spendTissueNutrition(6))return false;
        }
        emitted.clear();direction=toward(target);host.getNavigation().stop();host.setEvolvedAction(move.key,target);
        EvolvedCombatEffects.prepare(host,move);
        host.setEvolvedPatients(patients.stream().mapToInt(Integer::intValue).toArray());
        return true;
    }
    private void finish(EvolvedSpecialMove move){host.setEvolvedAction("");host.setDeltaMovement(0,host.getDeltaMovement().y,0);cooldown=move.cooldown;emitted.clear();patients.clear();}
    private boolean valid(LivingEntity e){return e!=null&&e.isAlive()&&e!=host&&e.level()==host.level()&&!host.isAlliedTo(e)&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private Vec3 toward(LivingEntity target){var v=target.position().subtract(host.position()).multiply(1,0,1);return v.lengthSqr()>.001?v.normalize():new Vec3(0,0,1);}
    private boolean visible(LivingEntity e,double distance){return valid(e)&&host.distanceToSqr(e)<=distance*distance&&host.hasLineOfSight(e);}
    private boolean support(Vec3 dir,double distance){
        var box=host.getBoundingBox().move(dir.scale(distance));
        for(double x:new double[]{box.minX+.05,box.maxX-.05})for(double z:new double[]{box.minZ+.05,box.maxZ-.05}){
            var p=BlockPos.containing(x,box.minY-.2,z);
            if(((ServerLevel)host.level()).getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null)return false;
            boolean floor=!host.level().getBlockState(p).getCollisionShape(host.level(),p).isEmpty();
            if(!floor&&!host.onGround()&&host.evolvedAction().equals(EvolvedSpecialMove.POUNCE.key))
                for(int y=1;y<=2;y++)floor|=!host.level().getBlockState(p.below(y)).getCollisionShape(host.level(),p.below(y)).isEmpty();
            if(!floor)return false;
        }return true;
    }
    private void travel(double speed){
        var d=direction.scale(speed);var box=host.getBoundingBox().expandTowards(d);
        for(int x=net.minecraft.util.Mth.floor(box.minX)>>4;x<=net.minecraft.util.Mth.floor(box.maxX)>>4;x++)
            for(int z=net.minecraft.util.Mth.floor(box.minZ)>>4;z<=net.minecraft.util.Mth.floor(box.maxZ)>>4;z++)if(((ServerLevel)host.level()).getChunkSource().getChunkNow(x,z)==null)return;
        if(!support(direction,speed)||!host.level().noCollision(host,host.getBoundingBox().move(d)))return;
        host.setDeltaMovement(d.x,host.getDeltaMovement().y,d.z);host.hurtMarked=true;
    }
    private float hit(LivingEntity target,float factor,double knockback){
        float before=target.getHealth();
        if(target.hurt(host.damageSources().mobAttack(host),(float)host.getAttributeValue(Attributes.ATTACK_DAMAGE)*factor)){
            EvolvedCombatEffects.impact(host,target);var d=target.position().subtract(host.position());target.knockback(knockback,-d.x,-d.z);
        }return Math.max(0,before-target.getHealth());
    }
    private void impact(EvolvedSpecialMove move,LivingEntity target){
        if(move==EvolvedSpecialMove.MEND){
            float budget=120;
            for(int id:patients)if(host.level().getEntity(id) instanceof Mob ally&&ally.isAlive()&&host.distanceToSqr(ally)<=100&&host.hasLineOfSight(ally)&&BlacklightInfectionSystem.isInfected(ally)){
                float heal=Math.min(budget,Math.min(60,ally.getMaxHealth()*.05F));float before=ally.getHealth();ally.heal(heal);float actual=Math.max(0,ally.getHealth()-before);budget-=actual;
                if(actual>0)EvolvedCombatEffects.feed(host,ally);if(budget<=0)break;
            }return;
        }
        if(move==EvolvedSpecialMove.SLAM){
            EvolvedCombatEffects.ground(host,true);
            for(var enemy:host.level().getEntitiesOfClass(LivingEntity.class,host.getBoundingBox().inflate(4.5,1.5,4.5),e->valid(e)&&(e==target||!(e instanceof Mob m)||!BlacklightInfectionSystem.isInfected(m))))
                if(visible(enemy,5)&&Math.abs(enemy.getY()-host.getY())<2)hit(enemy,1.45F,.9);
            return;
        }
        double range=move==EvolvedSpecialMove.LASH?10:move==EvolvedSpecialMove.BASH?3.5:3.2;
        if(!visible(target,range)||toward(target).dot(direction)<.35)return;
        float dealt=hit(target,move==EvolvedSpecialMove.BASH?1.1F:move==EvolvedSpecialMove.LASH?.75F:1.15F,move==EvolvedSpecialMove.BASH?1.1:.28);
        if(move==EvolvedSpecialMove.POUNCE&&dealt>0)host.heal(Math.min(20,dealt*.15F));
    }
    private void fire(LivingEntity target,int index){
        if(!visible(target,24))return;
        var dart=new EvolvedQuill(host.level(),host);dart.setPos(host.getX(),host.getEyeY()-.4,host.getZ());
        var d=target.getBoundingBox().getCenter().subtract(dart.position());dart.damage((float)host.getAttributeValue(Attributes.ATTACK_DAMAGE)*.42F);
        dart.shoot(d.x,d.y+Math.sqrt(d.x*d.x+d.z*d.z)*.018,d.z,1.3F,index==1?1.5F:0);
        host.level().addFreshEntity(dart);EvolvedCombatEffects.sweep(host);
    }
}
