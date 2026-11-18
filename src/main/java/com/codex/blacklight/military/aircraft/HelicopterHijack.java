package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Short, interruptible cockpit claim. The attacker rides an exterior latch until actual crew egress commits. */
public final class HelicopterHijack {
    public static final int EXTRACT=54,COMPLETE=76;
    private final BlackwatchHelicopter craft;private final HelicopterPilot pilot;
    private UUID actor;private List<UUID> crew=List.of();private int ticks,side=-1;private boolean extracted;
    private BlackwatchHelicopter.Phase resume;
    public HelicopterHijack(BlackwatchHelicopter craft,HelicopterPilot pilot){this.craft=craft;this.pilot=pilot;}
    public boolean active(){return craft.level().isClientSide?craft.controlData().hasUUID("Hijacker"):actor!=null;}
    public boolean actor(UUID id){return id!=null&&(craft.level().isClientSide?craft.controlData().hasUUID("Hijacker")&&id.equals(craft.controlData().getUUID("Hijacker")):id.equals(actor));}
    public int ticks(){return craft.level().isClientSide?craft.controlData().getInt("HijackTicks"):ticks;}
    public int side(){return craft.level().isClientSide?craft.controlData().getInt("HijackSide"):side;}
    public float pull(){return Math.max(0,Math.min(1,(ticks()-24)/30F));}
    public Vec3 actorPoint(){
        double enter=Math.max(0,Math.min(1,(ticks()-EXTRACT)/(double)(COMPLETE-EXTRACT)));
        double rise=Math.min(1,ticks()/18.0);return craft.local(side()*2.0*(1-enter)-.65*enter,.15+.5*rise,2.1);
    }
    private List<UUID> manifest(){return craft.getPassengers().stream().filter(e->!actor(e.getUUID())).map(Entity::getUUID).sorted().toList();}
    private boolean clearContact(ServerPlayer player){
        Vec3 port=craft.local(side*1.8,1.6,2.1);
        return player.level()==craft.level()&&player.getEyePosition().distanceToSqr(port)<=3.5*3.5&&VehicleDamageBridge.loaded(player.serverLevel(),player.getEyePosition(),port)
                &&craft.level().clip(new ClipContext(player.getEyePosition(),port,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }
    public boolean request(ServerPlayer player){
        if(!(craft.level() instanceof ServerLevel level)||player==null||active()||craft.health()<=0||craft.phase()==BlackwatchHelicopter.Phase.CRASH
                ||!VehicleHijackSystem.hijackEligible(player,false)||VehicleHijackSystem.active(player)||!level.mayInteract(player,craft.blockPosition()))return false;
        var members=craft.getPassengers();
        if(members.isEmpty()||members.stream().anyMatch(e->!(e instanceof MilitarySoldier s)||!s.isAlive()||com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(s)||com.codex.blacklight.combat.ConsumeControl.protectedEntity(s)))return false;
        side=player.distanceToSqr(craft.local(-1.8,1.6,2.1))<=player.distanceToSqr(craft.local(1.8,1.6,2.1))?-1:1;
        if(!clearContact(player))return false;
        if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND,craft)))return false;
        actor=player.getUUID();crew=manifest();ticks=0;extracted=false;resume=craft.phase();craft.setFlightPhase(BlackwatchHelicopter.Phase.HIJACK);craft.setDeltaMovement(Vec3.ZERO);
        if(!player.startRiding(craft,true)){reset();craft.setFlightPhase(resume);return false;}
        com.codex.blacklight.disguise.DisguiseSystem.vehicleHostileAct(player,(MilitarySoldier)members.get(0));
        craft.playSound(VehicleSounds.event("helicopter_door"),1.4F,1);pilot.reconcile();pilot.sync();craft.positionRider(player);return true;
    }
    public void tick(){
        if(!(craft.level() instanceof ServerLevel level)||actor==null)return;
        var player=level.getEntity(actor) instanceof ServerPlayer p?p:null;
        String invalid=player==null?"missing actor":player.getVehicle()!=craft?"detached":!VehicleHijackSystem.hijackEligible(player,true)?"actor unavailable":craft.health()<=0?"destroyed":!clearContact(player)?"contact lost":!extracted&&!crew.equals(manifest())?"crew changed":null;
        if(invalid!=null){com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Helicopter cockpit claim interrupted at {}: {}",ticks,invalid);cancel(player);return;}
        craft.setDeltaMovement(Vec3.ZERO);ticks++;
        if(ticks==EXTRACT){
            var members=craft.getPassengers().stream().filter(e->!actor(e.getUUID())).toList();var exits=exits(members);
            if(exits==null){com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Helicopter cockpit claim interrupted: no clear crew exits");cancel(player);return;}
            // Every body/exit is validated before any original crew or inventory is changed.
            for(int i=0;i<members.size();i++){
                var member=members.get(i);member.stopRiding();member.setPos(exits.get(i));member.fallDistance=0;
                member.setDeltaMovement(craft.safeLanding(craft.position())?Vec3.ZERO:new Vec3((i%2==0?-.18:.18),.08,0).yRot((float)Math.toRadians(craft.getYRot())));
            }
            extracted=true;
        }
        if(ticks>=COMPLETE){
            var winner=player;reset();pilot.captured(winner);craft.positionRider(winner);
        }
    }
    private List<Vec3> exits(List<Entity> members){
        var level=craft.level();var points=new ArrayList<Vec3>();var occupied=new ArrayList<AABB>();boolean grounded=craft.safeLanding(craft.position());
        for(int i=0;i<members.size();i++){
            var member=members.get(i);Vec3 selected=null;
            for(int n=0;n<12;n++){
                int direction=n%2==0?-1:1;Vec3 at=craft.local(direction*4.1,.05,2.4-(n/2)*1.35);var box=member.getDimensions(member.getPose()).makeBoundingBox(at);
                if(!VehicleKinematics.loaded(level,box.inflate(.2))||!level.getWorldBorder().isWithinBounds(box)||!level.noCollision(member,box)
                        ||occupied.stream().anyMatch(b->b.inflate(.1).intersects(box))||!level.getEntitiesOfClass(LivingEntity.class,box,e->e!=member&&e.isAlive()&&!craft.hasPassenger(e)).isEmpty())continue;
                var below=net.minecraft.core.BlockPos.containing(at.add(0,-.1,0));
                if(grounded&&(!level.getBlockState(below).isFaceSturdy(level,below,net.minecraft.core.Direction.UP)||!level.getFluidState(below).isEmpty()))continue;
                selected=at;occupied.add(box);break;
            }
            if(selected==null)return null;points.add(selected);
        }
        return points;
    }
    private void cancel(ServerPlayer player){
        if(player!=null&&player.getVehicle()==craft)player.stopRiding();
        var prior=resume;boolean already=extracted;reset();
        if(craft.health()>0)craft.setFlightPhase(already?BlackwatchHelicopter.Phase.CRASH:prior==null?BlackwatchHelicopter.Phase.IDLE:prior);
        pilot.reconcile();
    }
    public void reset(){actor=null;crew=List.of();ticks=0;extracted=false;}
    public void sync(CompoundTag data){if(actor!=null){data.putUUID("Hijacker",actor);data.putInt("HijackTicks",ticks);data.putInt("HijackSide",side);}}
}
