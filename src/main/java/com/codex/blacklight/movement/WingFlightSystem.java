package com.codex.blacklight.movement;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Map;
import java.util.WeakHashMap;

/** Flight permissions and transitions stay on the server; vanilla collision remains enabled. */
public final class WingFlightSystem {
    private static final Map<ServerPlayer,Control> INPUTS=new WeakHashMap<>();
    private static final Map<Player,WingFlightCorridor.LoadBudget> LOAD_BUDGETS=new WeakHashMap<>();
    private static final class Control {
        long received,lastMotion;boolean jump,launch;Vec3 lastPosition;
        final WingFlightTuning.SonicGate sonic=new WingFlightTuning.SonicGate();
    }
    public static boolean equipped(Player p){return UtilityPowerSystem.active(p,PowerForm.SOVEREIGN_WINGS)&&!BiomassSystem.active(p);}
    public static boolean eligible(Player p){return equipped(p)&&!p.isPassenger()&&!p.isSpectator()&&!p.isInWater()&&!p.isInLava()&&!p.isFallFlying()&&!p.getAbilities().flying;}
    public static boolean flying(Player p){return eligible(p)&&p.getData(ModAttachments.WING_FLIGHT).flying();}
    public static boolean ownsTravel(Player p){return flying(p)&&p.getData(ModAttachments.WEAPON_VISUAL).spec()==null&&!p.getData(ModAttachments.CONSUMING);}
    public static int tier(Player p){return MovementUpgrade.wingTier(p.getData(ModAttachments.PLAYER_STATE).getMovementUpgradeMask());}
    public static void acceptedMotion(ServerPlayer p){var c=INPUTS.get(p);if(c!=null)c.lastMotion=p.level().getGameTime();}
    public static void input(ServerPlayer p,boolean jump,int forward,int sideways,boolean sprint,boolean down){
        var c=INPUTS.computeIfAbsent(p,k->new Control());
        c.launch|=jump&&!c.jump;c.jump=jump;c.received=p.level().getGameTime();
        p.getData(ModAttachments.MOVEMENT_STATE).acceptInput(jump,false,forward,sideways,sprint,down);
    }
    public static Vec3 nextVelocity(Player p){
        var move=p.getData(ModAttachments.MOVEMENT_STATE);var keys=move.wallInput();var v=p.getDeltaMovement();
        if(p instanceof ServerPlayer server){var control=INPUTS.get(server);if(control!=null&&p.level().getGameTime()-control.lastMotion>3)v=Vec3.ZERO;}
        var state=p.getData(ModAttachments.WING_FLIGHT);
        // Keep the feet planted while the capture coils, then release into flight.
        if(state.phase()==WingFlightState.TAKEOFF&&p.level().getGameTime()-state.started()<WingFlightState.WINDUP_TICKS&&p.onGround())return new Vec3(0,-.01,0);
        int tier=tier(p);
        var next=WingFlightPhysics.step(v.x,v.y,v.z,keys.forward(),keys.sideways(),move.jumpHeld(),keys.detach(),keys.sprint(),move.cameraYaw(p.getYRot()),p.getXRot(),state.phase()==WingFlightState.TAKEOFF,tier);
        var result=new Vec3(next.x(),next.y(),next.z());double speed=result.length();
        if(speed<1e-6)return result;
        var direction=result.scale(1/speed);var box=p.getBoundingBox().inflate(.25);
        double permitted=WingFlightCorridor.adaptiveLimit(speed,tier,p.level().getGameTime(),
                LOAD_BUDGETS.computeIfAbsent(p,k->new WingFlightCorridor.LoadBudget()),distance->{
            var probe=box.move(direction.scale(distance));
            if(!p.level().getWorldBorder().isWithinBounds(probe))return false;
            int minX=net.minecraft.util.Mth.floor(probe.minX)>>4,maxX=net.minecraft.util.Mth.floor(probe.maxX)>>4;
            int minZ=net.minecraft.util.Mth.floor(probe.minZ)>>4,maxZ=net.minecraft.util.Mth.floor(probe.maxZ)>>4;
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                // getChunk(..., false) can still wait for an in-flight server future.
                // Probe the ready cache only; flight must never block chunk generation.
                var chunk=p.level() instanceof net.minecraft.server.level.ServerLevel server
                        ?server.getChunkSource().getChunkNow(x,z)
                        :p.level().getChunkSource().getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
                if(chunk==null)return false;
            }
            return true;
        });
        return result.scale(permitted/speed);
    }
    private static void phase(ServerPlayer p,int value){
        var old=p.getData(ModAttachments.WING_FLIGHT);
        if(old.phase()==value)return;
        boolean flying=value>=WingFlightState.TAKEOFF&&value<=WingFlightState.BOOST;
        int order=com.codex.blacklight.animation.WingFoldOrder.onTransition(old.foldVariant(),old.flying(),flying,()->p.getRandom().nextInt(2));
        p.setData(ModAttachments.WING_FLIGHT,new WingFlightState(value,p.level().getGameTime(),order,old.transformedAt(),old.deployed()));p.syncData(ModAttachments.WING_FLIGHT);
    }
    public static void transform(ServerPlayer p,boolean deployed){
        var old=p.getData(ModAttachments.WING_FLIGHT);long now=p.level().getGameTime();
        p.setData(ModAttachments.WING_FLIGHT,new WingFlightState(old.phase(),old.started(),old.foldVariant(),now,deployed));
        p.syncData(ModAttachments.WING_FLIGHT);
    }
    public static void stop(ServerPlayer p){
        phase(p,WingFlightState.GROUNDED);INPUTS.remove(p);LOAD_BUDGETS.remove(p);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        if(!eligible(p)){stop(p);return;}
        long now=p.level().getGameTime();var s=p.getData(ModAttachments.WING_FLIGHT);
        var c=INPUTS.get(p);var move=p.getData(ModAttachments.MOVEMENT_STATE);
        if(c!=null){
            double distance=c.lastPosition==null?0:p.position().distanceTo(c.lastPosition);c.lastPosition=p.position();
            // Actual displacement, never a sprint key or a teleport, causes the shared sound.
            boolean moving=s.flying()&&tier(p)==3&&distance<=WingFlightTuning.tier(3).boostSpeed()*1.5;
            if(c.sonic.update(now,distance,moving))p.serverLevel().playSound(null,p.getX(),p.getY(),p.getZ(),
                    net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,net.minecraft.sounds.SoundSource.PLAYERS,2F,.85F);
        }
        if(c==null||now-c.received>12)move.acceptInput(false,false,0,0,false,false);
        if(!s.flying()){
            if(s.phase()==WingFlightState.LAND&&now-s.started()<WingFlightState.LAND_TICKS)return;
            if(c!=null&&c.launch){
                if(c!=null)c.launch=false;
                move.cancelCharge();move.wallTraversal().reset();move.endDash();
                p.setData(ModAttachments.JUMP_CHARGE,0);p.syncData(ModAttachments.JUMP_CHARGE);
                p.setData(ModAttachments.GLIDE_UNTIL,-1L);p.syncData(ModAttachments.GLIDE_UNTIL);
                phase(p,WingFlightState.TAKEOFF);p.setDeltaMovement(p.onGround()?new Vec3(0,-.01,0):p.getDeltaMovement());p.hurtMarked=true;
            }else phase(p,WingFlightState.GROUNDED);
            return;
        }
        if(c!=null)c.launch=false;
        p.resetFallDistance();
        if(s.phase()==WingFlightState.TAKEOFF&&now-s.started()<WingFlightState.TAKEOFF_TICKS){if(ownsTravel(p))p.setDeltaMovement(nextVelocity(p));return;}
        if(p.onGround()&&now-s.started()>10){phase(p,WingFlightState.LAND);return;}
        var keys=move.wallInput();
        phase(p,keys.sprint()&&(keys.forward()!=0||keys.sideways()!=0)?WingFlightState.BOOST:keys.forward()!=0||keys.sideways()!=0||move.jumpHeld()||keys.detach()?WingFlightState.CRUISE:WingFlightState.HOVER);
        // Used by vanilla speed validation; normal movement packets retain collision checks.
        if(ownsTravel(p))p.setDeltaMovement(nextVelocity(p));
    }
    private WingFlightSystem(){}
}
