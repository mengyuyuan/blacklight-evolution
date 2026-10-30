package com.codex.blacklight.client;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.codex.blacklight.network.VehicleControlPayload;
import com.codex.blacklight.network.VehicleSeatPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 10 Hz intent stream; vanilla vehicle position packets are disabled by the mechanical entity. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleControls {
    private static final String CATEGORY="key.categories.blacklight_vehicles";
    private static final KeyMapping NEXT_SEAT=new KeyMapping("key.blacklight.vehicle_next_seat",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,CATEGORY);
    private static final KeyMapping NEXT_AMMO=new KeyMapping("key.blacklight.vehicle_next_ammo",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,CATEGORY);
    private static UUID vehicleId,leaseId,playerId;
    private static long sequence;
    private static boolean nextAmmo;
    private VehicleControls(){}
    static boolean riding(){var p=Minecraft.getInstance().player;return p!=null&&p.getVehicle() instanceof MilitaryVehicle;}
    static boolean interacting(){
        var mc=Minecraft.getInstance();
        return mc.player!=null&&mc.player.getMainHandItem().isEmpty()
                &&mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                &&com.codex.blacklight.military.vehicle.VehicleDamageBridge.vehicle(hit.getEntity())!=null;
    }
    public static boolean ownsInput(){return riding()||interacting()&&SkillBindings.down(Minecraft.getInstance().options.keyUse);}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(NEXT_SEAT);event.register(NEXT_AMMO);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){update();}
    public static void update(){
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || !(mc.player.getVehicle() instanceof MilitaryVehicle vehicle)) {
            vehicleId=null;leaseId=null;playerId=null;sequence=0;nextAmmo=false;
            while(NEXT_SEAT.consumeClick()){}while(NEXT_AMMO.consumeClick()){}return;
        }
        if(mc.screen!=null)return;
        while(NEXT_SEAT.consumeClick())PacketDistributor.sendToServer(new VehicleSeatPayload(vehicle.getUUID()));
        while(NEXT_AMMO.consumeClick())nextAmmo=true;
        UUID lease=vehicle.controlLease(mc.player.getUUID());
        if(lease==null)return;
        if(!vehicle.getUUID().equals(vehicleId) || !lease.equals(leaseId) || !mc.player.getUUID().equals(playerId)) {
            vehicleId=vehicle.getUUID();leaseId=lease;playerId=mc.player.getUUID();sequence=0;
        }
        if((mc.player.tickCount&1)!=0)return;
        String seatId=vehicle.seatOf(mc.player.getUUID());
        var occupied=vehicle.getPassengers().stream().map(p->vehicle.seatOf(p.getUUID())).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var seat=new SeatInput(vehicle.definition(),seatId,occupied);
        boolean driver=seat.driver();
        double forward=driver?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
        double steering=driver?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
        int actions=seat.fireActions(mc.options.keyAttack.isDown(),mc.options.keyUse.isDown())
                |(driver&&mc.options.keyJump.isDown()?VehicleControlAuthority.BRAKE:0)
                |(nextAmmo&&seat.weaponOperator()?VehicleControlAuthority.NEXT_AMMUNITION:0);
        PacketDistributor.sendToServer(new VehicleControlPayload(vehicleId,lease,sequence++,forward,steering,
                Mth.wrapDegrees(mc.player.getYRot()),Mth.clamp(mc.player.getXRot(),-90,90),actions));
        nextAmmo=false;
    }
    record SeatInput(VehicleDefinition definition,String id,java.util.Set<String> occupied){
        boolean driver(){return definition.seats().stream().anyMatch(s->s.id().equals(id)&&s.job()==VehicleDefinition.Job.DRIVER);}
        boolean weaponOperator(){
            return !definition.weapons().isEmpty()&&(definition.seats().stream().anyMatch(s->s.id().equals(id)&&s.job()==VehicleDefinition.Job.GUNNER)
                    ||driver()&&definition.seats().stream().noneMatch(s->s.job()==VehicleDefinition.Job.GUNNER&&occupied.contains(s.id())));
        }
        int fireActions(boolean primary,boolean secondary){
            if(!weaponOperator())return 0;
            return (primary?VehicleControlAuthority.PRIMARY_FIRE:0)|(secondary?VehicleControlAuthority.SECONDARY_FIRE:0);
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void interaction(InputEvent.InteractionKeyMappingTriggered event){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.getVehicle() instanceof MilitaryVehicle){event.setCanceled(true);event.setSwingHand(false);}
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void hands(RenderHandEvent event){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.getVehicle() instanceof MilitaryVehicle)event.setCanceled(true);
    }
}
