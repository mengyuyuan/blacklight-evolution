package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.combat.ConsumeControl;
import com.codex.blacklight.combat.ImpactControl;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.DevourerFeedingSystem;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Redirects an existing lawful passenger pursuit to its real hull, never acquires a vehicle. */
public final class VehicleHullAttackGoal extends Goal {
    private final Mob actor;
    private int cooldown;

    public VehicleHullAttackGoal(Mob actor){
        this.actor=actor;
        setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
    }
    /** This same narrow gate is used by EntityJoin, rather than scanning all world mobs. */
    public static boolean supports(Class<?> type){
        return type!=null&&(MilitarySoldier.class.isAssignableFrom(type)||BlacklightHunter.class.isAssignableFrom(type)
                ||BlacklightMutant.class.isAssignableFrom(type)||BlacklightInfectedHost.class.isAssignableFrom(type)
                ||BlacklightCommonInfected.class.isAssignableFrom(type));
    }
    private static boolean operational(Mob actor){
        return actor!=null&&supports(actor.getClass())&&actor.level() instanceof ServerLevel level
                &&actor.isAlive()&&!actor.isRemoved()&&!actor.isNoAi()&&!actor.isPassenger()
                &&level.getEntity(actor.getUUID())==actor&&!ImpactControl.stunned(actor)&&!ConsumeControl.ownsMotion(actor)
                &&(!(actor instanceof MilitarySoldier soldier)||soldier.tacticsAvailable())
                &&(!(actor instanceof BlacklightCommonInfected common)||common.combatAvailable())
                &&(!(actor instanceof BlacklightInfectedHost host)||!DevourerFeedingSystem.controls(host));
    }
    private static boolean lawful(Mob actor,LivingEntity target){
        if(target==null||target==actor||!target.isAlive()||target.isRemoved()||target.level()!=actor.level()
                ||FirearmsCombat.protectedTarget(actor,target)||!actor.canAttack(target))return false;
        if(actor instanceof MilitarySoldier soldier)return soldier.validTarget(target);
        if(actor instanceof BlacklightCommonInfected common)return common.validTarget(target);
        if(actor instanceof BlacklightMutant&&(!actor.isWithinRestriction()||!actor.isWithinRestriction(target.blockPosition())))return false;
        if(actor instanceof BlacklightInfectedHost host){
            if(target instanceof HiveTendril)return false;
            if(DevourerFeedingSystem.isDevourer(host)&&(!host.giantBoss()||!(target instanceof Player)
                    ||!DevourerFeedingSystem.withinTerritory(host,target)))return false;
            // As in the original host selector, player infection grants powers, not allegiance.
            return target instanceof Player||!BlacklightInfectionSystem.isInfected(target);
        }
        if(target instanceof Player player)return !player.isCreative()&&!player.isSpectator();
        if(actor.getLastHurtByMob()==target)return true;
        return actor instanceof BlacklightHunter&&(target instanceof Animal||target instanceof AbstractVillager)
                &&!BlacklightInfectionSystem.isInfected(target);
    }
    private static MilitaryVehicle hull(Mob actor){
        if(!operational(actor))return null;
        var target=actor.getTarget();
        if(!lawful(actor,target)||!(target.getVehicle() instanceof MilitaryVehicle vehicle)
                ||vehicle.level()!=actor.level()||vehicle.isRemoved()||vehicle.health()<=0
                ||!vehicle.getPassengers().contains(target)||((ServerLevel)actor.level()).getEntity(vehicle.getUUID())!=vehicle
                ||VehicleDamageBridge.protectedHull(actor,vehicle)
                ||actor.distanceToSqr(vehicle)>48*48
                ||!VehicleKinematics.loaded(actor.level(),actor.getBoundingBox())
                ||!VehicleKinematics.loaded(actor.level(),vehicle.getBoundingBox()))return null;
        return vehicle;
    }
    public static boolean handles(Mob actor){return hull(actor)!=null;}
    // The existing FireTeamGoal remains the only owner of infantry rifle ammo and firing cadence.
    @Override public boolean canUse(){return !(actor instanceof MilitarySoldier)&&handles(actor);}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){actor.getNavigation().stop();cooldown=10;}
    @Override public void stop(){actor.getNavigation().stop();actor.getMoveControl().setWantedPosition(actor.getX(),actor.getY(),actor.getZ(),0);}
    private static VehicleDefinition.Vec3 point(Vec3 p){return new VehicleDefinition.Vec3(p.x,p.y,p.z);}
    private Vec3 surface(MilitaryVehicle vehicle){
        Vec3 eye=actor.getEyePosition(),center=vehicle.position().add(0,Math.min(1.2,vehicle.definition().height()/2),0);
        Vec3 direction=center.subtract(eye);double length=direction.length();if(length<1e-6)return center;
        var hit=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch())
                .raycast(point(vehicle.position()),vehicle.getYRot(),point(eye),point(direction),length);
        return hit.isPresent()?eye.add(direction.scale(hit.getAsDouble()/length)):null;
    }
    private boolean clear(ServerLevel level,Vec3 point){
        var eye=actor.getEyePosition();
        return VehicleKinematics.loaded(level,new AABB(eye,point).inflate(.05))
                &&level.clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()==HitResult.Type.MISS;
    }
    @Override public void tick(){
        var vehicle=hull(actor);if(vehicle==null){stop();return;}
        if(cooldown>0)cooldown--;
        var level=(ServerLevel)actor.level();var surface=surface(vehicle);if(surface==null){actor.getNavigation().stop();return;}
        actor.getLookControl().setLookAt(surface.x,surface.y,surface.z,30,30);
        var box=actor.getBoundingBox();var hand=new Vec3(Mth.clamp(surface.x,box.minX,box.maxX),Mth.clamp(surface.y,box.minY,box.maxY),Mth.clamp(surface.z,box.minZ,box.maxZ));
        if(hand.distanceToSqr(surface)<=1.25*1.25&&clear(level,surface)){
            actor.getNavigation().stop();actor.getMoveControl().setWantedPosition(actor.getX(),actor.getY(),actor.getZ(),0);
            if(cooldown==0&&hull(actor)==vehicle){
                actor.swing(InteractionHand.MAIN_HAND);actor.doHurtTarget(vehicle);
                cooldown=actor instanceof BlacklightMutant mutant?(mutant.kind()==BlacklightMutant.Kind.BRUTE?40:mutant.kind()==BlacklightMutant.Kind.SPITTER?58:30)
                        :actor instanceof BlacklightInfectedHost host&&host.giantBoss()?40:20;
            }
            return;
        }
        // Conservative local surface approach. No vanilla path search or access to unknown chunks.
        Vec3 outward=actor.position().subtract(vehicle.position()).multiply(1,0,1);
        if(outward.lengthSqr()<1e-6){actor.getNavigation().stop();return;}
        Vec3 approach=surface.add(outward.normalize().scale(actor.getBbWidth()/2+.35));
        approach=new Vec3(approach.x,actor.getY(),approach.z);
        var travel=actor.getBoundingBox().expandTowards(approach.subtract(actor.position())).inflate(1.1);
        if(!VehicleKinematics.loaded(level,travel)||!clear(level,new Vec3(approach.x,actor.getEyeY(),approach.z))){actor.getNavigation().stop();return;}
        actor.getNavigation().stop();actor.getMoveControl().setWantedPosition(approach.x,approach.y,approach.z,1.1);
    }
}
