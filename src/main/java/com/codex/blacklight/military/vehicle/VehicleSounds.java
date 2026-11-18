package com.codex.blacklight.military.vehicle;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** Both factions use the sound of the actual chassis and installed weapon. */
public final class VehicleSounds {
    private static final class Registered {
    private static final DeferredRegister<SoundEvent> REGISTRY=DeferredRegister.create(Registries.SOUND_EVENT,"blacklight");
    private static final Map<String,Supplier<SoundEvent>> EVENTS=new LinkedHashMap<>();
    static {for(String cue:List.of("uav_rotor","uav_scan","uav_seeing","uav_alert","uav_transmit","uav_confirm","uav_break","helicopter_rotor","helicopter_turbine","helicopter_jet","helicopter_startup","helicopter_winddown","helicopter_door","helicopter_alarm","truck_engine","apc_engine","tank_engine","wheels","tracks","autocannon","coaxial","cannon","rocket")){
        String id="vehicle."+cue;EVENTS.put(cue,REGISTRY.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight",id))));
    }}
    }
    public static void register(IEventBus bus){Registered.REGISTRY.register(bus);}
    public static SoundEvent event(String cue){return Registered.EVENTS.get(cue).get();}
    public static String shotCue(int weapon){return switch(weapon){case 8,15->"autocannon";case 9,13,14->"rocket";case 10,11->"cannon";case 12->"coaxial";default->"";};}
    public static float shotVolume(int weapon){return weapon==10||weapon==11?3.6F:weapon==8||weapon==15?2.2F:1.5F;}
    private VehicleSounds(){}
}
