package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.combat.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** On-foot Prototype extraction; the same vehicle and actual crew survive the transfer. */
@EventBusSubscriber(modid="blacklight")
public final class VehicleHijackSystem {
    private record Session(UUID player,UUID vehicle,List<UUID> crew,long started){}
    // No retained player/world/entity references and no durable claim to strand after restart.
    private static final Map<ServerLevel,Map<UUID,Session>> ACTIVE=new WeakHashMap<>();
    private VehicleHijackSystem(){}
    public static boolean claimed(MilitaryVehicle vehicle){
        return vehicle!=null&&vehicle.level() instanceof ServerLevel level&&ACTIVE.getOrDefault(level,Map.of()).containsKey(vehicle.getUUID());
    }
    public static boolean active(ServerPlayer player){
        return player!=null&&ACTIVE.getOrDefault(player.serverLevel(),Map.of()).values().stream().anyMatch(s->s.player.equals(player.getUUID()));
    }
    private static boolean ready(ServerPlayer p){
        var state=p.getData(ModAttachments.PLAYER_STATE);
        return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isPassenger()&&state.isInfected()&&state.isPrototypeMode()
                &&p.getMainHandItem().isEmpty()&&!p.getData(ModAttachments.CONSUMING)&&!WeaponCombatSystem.busy(p)
                &&p.getData(ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE
                &&!p.getData(ModAttachments.MOVEMENT_STATE).wallTraversal().attached()
                &&!ConsumeControl.ownsMotion(p)&&!ConsumeControl.protectedEntity(p)
                &&!com.codex.blacklight.disguise.DisguiseSystem.morphing(p)
                &&!com.codex.blacklight.power.BiomassSystem.active(p)&&!com.codex.blacklight.city.HydraTransport.active(p);
    }
    private static boolean contact(ServerPlayer p,MilitaryVehicle v){
        if(p.level()!=v.level()||v.isRemoved()||v.health()<=0)return false;
        var driver=v.definition().seats().stream().filter(s->s.job()==VehicleDefinition.Job.DRIVER).findFirst().orElse(null);
        if(driver==null)return false;
        var point=v.definition().mountWorld(driver.anchorId(),new VehicleDefinition.Vec3(v.getX(),v.getY(),v.getZ()),v.getYRot(),v.turretYaw(),v.gunPitch());
        var at=new Vec3(point.x(),point.y()+.8,point.z());
        return p.getEyePosition().distanceToSqr(at)<=3.5*3.5&&VehicleKinematics.loaded(v.level(),new AABB(p.getEyePosition(),at).inflate(1))
                &&p.level().clip(new ClipContext(p.getEyePosition(),at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static List<UUID> crew(MilitaryVehicle v){
        if(v.getPassengers().isEmpty()||v.getPassengers().stream().anyMatch(e->!(e instanceof MilitarySoldier s)||!s.isAlive()||s.isRemoved()
                ||com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(s)||ConsumeControl.protectedEntity(s)))return List.of();
        return v.getPassengers().stream().map(Entity::getUUID).sorted().toList();
    }
    public static boolean request(ServerPlayer player,MilitaryVehicle vehicle){
        if(player==null||vehicle==null||!ready(player)||active(player)||!contact(player,vehicle)||claimed(vehicle))return false;
        var level=player.serverLevel();var crew=crew(vehicle);var sessions=ACTIVE.computeIfAbsent(level,ignored->new HashMap<>());
        if(crew.isEmpty()||sessions.size()>=64||!level.mayInteract(player,vehicle.blockPosition()))return false;
        var victim=(MilitarySoldier)vehicle.getPassengers().get(0);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND,vehicle)).isCanceled())return false;
        sessions.put(vehicle.getUUID(),new Session(player.getUUID(),vehicle.getUUID(),crew,level.getGameTime()));
        vehicle.setDeltaMovement(Vec3.ZERO);
        com.codex.blacklight.disguise.DisguiseSystem.vehicleHostileAct(player,victim);
        return true;
    }
    private static Optional<List<Vec3>> exits(MilitaryVehicle vehicle,List<Entity> crew){
        var result=new ArrayList<Vec3>();var boxes=new ArrayList<AABB>();double yaw=Math.toRadians(vehicle.getYRot()),c=Math.cos(yaw),s=Math.sin(yaw);
        for(var member:crew){
            Vec3 chosen=null;
            for(int n=0;n<24&&chosen==null;n++){
                double angle=2*Math.PI*n/24,x=Math.cos(angle)*(vehicle.definition().width()/2+1.3),z=Math.sin(angle)*(vehicle.definition().length()/2+1.3);
                for(int dy:new int[]{0,1,-1}){
                    var at=new Vec3(vehicle.getX()+x*c+z*s,Math.floor(vehicle.getY())+dy,vehicle.getZ()+z*c-x*s);
                    var box=member.getDimensions(member.getPose()).makeBoundingBox(at);
                    if(!VehicleKinematics.loaded(vehicle.level(),box.inflate(1))||boxes.stream().anyMatch(other->other.inflate(.1).intersects(box))
                            ||!vehicle.level().noCollision(member,box))continue;
                    var below=BlockPos.containing(at.x,at.y-.05,at.z);
                    if(vehicle.level().getBlockState(below).getCollisionShape(vehicle.level(),below).isEmpty()||!vehicle.level().getFluidState(below.above()).isEmpty())continue;
                    chosen=at;boxes.add(box);break;
                }
            }
            if(chosen==null)return Optional.empty();result.add(chosen);
        }
        return Optional.of(List.copyOf(result));
    }
    public static void tick(ServerLevel level){
        var sessions=ACTIVE.get(level);if(sessions==null)return;
        for(var session:List.copyOf(sessions.values())){
            var player=level.getEntity(session.player) instanceof ServerPlayer p?p:null;
            var vehicle=level.getEntity(session.vehicle) instanceof MilitaryVehicle v?v:null;
            if(player==null||vehicle==null||!ready(player)||!contact(player,vehicle)||!session.crew.equals(crew(vehicle))){sessions.remove(session.vehicle);continue;}
            vehicle.setDeltaMovement(Vec3.ZERO);long elapsed=level.getGameTime()-session.started;
            if(elapsed<30){if(elapsed%5==0)player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.blacklight.vehicle_hijack",Math.min(100,(int)(elapsed*100/30))),true);continue;}
            var members=List.copyOf(vehicle.getPassengers());var safe=exits(vehicle,members);
            if(safe.isEmpty()){sessions.remove(session.vehicle);continue;}
            // Validate the entire egress before changing any actual passenger. Main-thread commit has no re-spawn path.
            for(int i=0;i<members.size();i++){var member=members.get(i);member.stopRiding();var at=safe.get().get(i);member.moveTo(at.x,at.y,at.z,member.getYRot(),member.getXRot());}
            sessions.remove(session.vehicle);
            if(player.startRiding(vehicle,true)){vehicle.setPlayerOwner(player.getUUID());vehicle.syncVehicleState();}
        }
        if(sessions.isEmpty())ACTIVE.remove(level);
    }
    @SubscribeEvent public static void afterTick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)tick(level);}
}
