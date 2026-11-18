package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Stable real seats and revocable server control. No client position or persistent input is accepted. */
public final class HelicopterPilot {
    public static final List<VehicleDefinition.Seat> SEATS=List.of(
            new VehicleDefinition.Seat("pilot",VehicleDefinition.Job.DRIVER,"pilot"),
            new VehicleDefinition.Seat("copilot",VehicleDefinition.Job.GUNNER,"copilot"),
            new VehicleDefinition.Seat("troop_1",VehicleDefinition.Job.PASSENGER,"troop_1"),new VehicleDefinition.Seat("troop_2",VehicleDefinition.Job.PASSENGER,"troop_2"),
            new VehicleDefinition.Seat("troop_3",VehicleDefinition.Job.PASSENGER,"troop_3"),new VehicleDefinition.Seat("troop_4",VehicleDefinition.Job.PASSENGER,"troop_4"));
    private final BlackwatchHelicopter craft;
    private final VehicleSeats seats=new VehicleSeats(SEATS);
    private final Map<UUID,UUID> leases=new HashMap<>();
    private final Map<UUID,String> restored=new HashMap<>();
    private VehicleControlAuthority authority;private long revision=-1;private boolean reconciling;
    private UUID owner;
    private boolean initialStoresIssued;
    private final HelicopterHijack hijack;
    public HelicopterPilot(BlackwatchHelicopter craft){this.craft=craft;hijack=new HelicopterHijack(craft,this);}
    public HelicopterHijack hijack(){return hijack;}
    public boolean owned(){return craft.level().isClientSide?craft.controlData().hasUUID("Owner"):owner!=null;}
    public UUID owner(){return craft.level().isClientSide?(craft.controlData().hasUUID("Owner")?craft.controlData().getUUID("Owner"):null):owner;}
    private VehicleControlAuthority authority(){if(authority==null)authority=new VehicleControlAuthority(craft.getUUID(),craft.level().dimension().location().toString(),seats,true);return authority;}
    private Set<UUID> actual(){var set=new HashSet<UUID>();for(var p:craft.getPassengers())if(p.getVehicle()==craft&&!p.isRemoved()&&!hijack.actor(p.getUUID()))set.add(p.getUUID());return set;}
    private VehicleControlAuthority.Session session(ServerPlayer player){return new VehicleControlAuthority.Session(player.getUUID(),player.level().dimension().location().toString(),player.getVehicle()==craft?craft.getUUID():null,MilitaryVehicle.crewOperational(player));}
    public String seat(UUID id){
        if(!craft.level().isClientSide)return seats.seatOf(id);
        var data=craft.controlData().getCompound("Seats");for(String name:data.getAllKeys())if(data.hasUUID(name)&&data.getUUID(name).equals(id))return name;return null;
    }
    public int slot(UUID id){String name=seat(id);for(int i=0;i<SEATS.size();i++)if(SEATS.get(i).id().equals(name))return i;return -1;}
    public Entity occupant(int slot){for(var p:craft.getPassengers())if(slot(p.getUUID())==slot)return p;return null;}
    public Entity weaponOperator(){return occupant(1)!=null?occupant(1):occupant(0);}
    public UUID lease(UUID id){
        if(craft.level().isClientSide){var data=craft.controlData().getCompound("Leases");return data.hasUUID(id.toString())?data.getUUID(id.toString()):null;}
        reconcile();return leases.get(id);
    }
    public void reconcile(){
        if(craft.level().isClientSide||reconciling)return;reconciling=true;
        try{
            var actual=actual();seats.reconcile(actual);
            for(var p:List.copyOf(craft.getPassengers()))if(actual.contains(p.getUUID())&&seats.seatOf(p.getUUID())==null){
                String hint=restored.remove(p.getUUID());boolean assigned=hint!=null&&seats.board(p.getUUID(),hint,actual);
                for(int i=0;i<SEATS.size()&&!assigned;i++){
                    if(p instanceof MilitarySoldier&&p.getPersistentData().getBoolean("HelicopterTroop")!=(i>=2))continue;
                    assigned=seats.board(p.getUUID(),SEATS.get(i).id(),actual);
                }
                if(!assigned)p.stopRiding();
            }
            if(revision!=seats.revision()){authority().revokeAll();leases.clear();revision=seats.revision();}
            Set<UUID> operational=new HashSet<>();
            if(craft.health()>0&&!hijack.active())for(var p:craft.getPassengers())if(p instanceof ServerPlayer player&&MilitaryVehicle.crewOperational(player)&&slot(player.getUUID())<2&&slot(player.getUUID())>=0){
                operational.add(player.getUUID());if(!leases.containsKey(player.getUUID())){var token=authority().grant(session(player));if(token!=null)leases.put(player.getUUID(),token);}
            }
            for(var id:List.copyOf(leases.keySet()))if(!operational.contains(id)){authority().revoke(id);leases.remove(id);}
            if(occupant(0) instanceof ServerPlayer player&&!hijack.active())claim(player);
            sync();
        }finally{reconciling=false;}
    }
    public boolean accept(ServerPlayer player,UUID token,long sequence,double forward,double steering,double yaw,double pitch,int actions){
        if(craft.level().isClientSide||player==null||player.level()!=craft.level()||player.getVehicle()!=craft||craft.health()<=0||hijack.active()||!MilitaryVehicle.crewOperational(player))return false;
        reconcile();return authority().accept(session(player),new VehicleControlAuthority.Input(craft.getUUID(),token,sequence,forward,steering,yaw,pitch,actions),craft.level().getGameTime());
    }
    public boolean switchSeat(ServerPlayer player){
        if(player==null||player.getVehicle()!=craft||craft.level().isClientSide||hijack.active()||!MilitaryVehicle.crewOperational(player))return false;
        reconcile();int before=slot(player.getUUID());
        for(int n=1;n<6;n++)if(seats.switchSeat(player.getUUID(),SEATS.get(Math.floorMod(before+n,6)).id(),actual())){reconcile();craft.positionRider(player);return true;}
        return false;
    }
    public Vec3 seatPoint(Entity passenger){
        if(hijack.actor(passenger.getUUID()))return hijack.actorPoint();
        int slot=slot(passenger.getUUID());if(slot<0)slot=0;
        Vec3 point=new Vec3(slot%2==0?-.65:.65,.65,slot>=2?-.6-(slot/2)*.85:2.1);
        if(slot==(hijack.side()<0?0:1)&&hijack.active())point=point.lerp(new Vec3(hijack.side()*1.7,.75,2.1),hijack.pull());
        return craft.local(point.x,point.y,point.z);
    }
    public boolean tick(){
        reconcile();
        if(hijack.active()){hijack.tick();sync();return true;}
        if(!owned()||craft.phase()==BlackwatchHelicopter.Phase.CRASH)return false;
        var driver=occupant(0);boolean ready=driver instanceof ServerPlayer p&&MilitaryVehicle.crewOperational(p);
        if(ready){
            craft.setFlightPhase(BlackwatchHelicopter.Phase.MANUAL);
            var input=authority().state(session((ServerPlayer)driver),craft.level().getGameTime());
            int actions=input.actions();double lift=((actions&VehicleControlAuthority.ASCEND)!=0?1:0)-((actions&VehicleControlAuthority.DESCEND)!=0?1:0);
            craft.manualFlight(input.throttle(),input.steering(),lift,input.brake());
        }else craft.landWithoutPilot();
        if(weaponOperator() instanceof ServerPlayer gunner&&MilitaryVehicle.crewOperational(gunner))craft.combat().manual(gunner,authority().state(session(gunner),craft.level().getGameTime()));
        else craft.combat().cooldown();
        sync();return true;
    }
    private void claim(ServerPlayer player){
        // One loadout per airframe. Dismounts, seat changes, another player and
        // reloads must not turn finite player weapons into infinite magazines.
        if(!initialStoresIssued){initialStoresIssued=true;craft.combat().fillPlayerStores();}
        owner=player.getUUID();
    }
    public void captured(ServerPlayer player){claim(player);craft.combat().finish();reconcile();craft.setFlightPhase(BlackwatchHelicopter.Phase.MANUAL);sync();}
    public void sync(){
        if(craft.level().isClientSide)return;
        var data=new CompoundTag();var positions=new CompoundTag();seats.occupants().forEach(positions::putUUID);data.put("Seats",positions);
        var tokens=new CompoundTag();leases.forEach((id,token)->tokens.putUUID(id.toString(),token));data.put("Leases",tokens);
        if(owner!=null)data.putUUID("Owner",owner);data.putInt("Rounds",craft.combat().rounds());data.putInt("Rockets",craft.combat().rockets());
        data.putInt("RapidLeft",craft.combat().rapid().left());data.putInt("RapidRight",craft.combat().rapid().right());data.putBoolean("RapidSelected",craft.combat().rapidSelected());hijack.sync(data);craft.setControlData(data);
    }
    public void save(CompoundTag tag){
        var data=new CompoundTag();if(owner!=null)data.putUUID("Owner",owner);data.putBoolean("InitialStoresIssued",initialStoresIssued);var positions=new CompoundTag();seats.occupants().forEach(positions::putUUID);data.put("Seats",positions);tag.put("AirPilot",data);
    }
    public void load(CompoundTag tag){
        var data=tag.getCompound("AirPilot");owner=data.hasUUID("Owner")?data.getUUID("Owner"):null;
        // Legacy owned aircraft also receive the new loadout once, when an
        // actual player next takes control. Merely loading never fills stores.
        initialStoresIssued=data.getBoolean("InitialStoresIssued");
        for(var id:List.copyOf(seats.occupants().values()))seats.remove(id);restored.clear();leases.clear();authority=null;revision=-1;
        var positions=data.getCompound("Seats");for(var seat:SEATS)if(positions.hasUUID(seat.id()))restored.put(positions.getUUID(seat.id()),seat.id());
        hijack.reset();
    }
}
